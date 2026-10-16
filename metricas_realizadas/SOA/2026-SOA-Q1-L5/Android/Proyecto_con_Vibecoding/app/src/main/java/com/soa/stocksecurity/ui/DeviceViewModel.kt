package com.soa.stocksecurity.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soa.stocksecurity.data.DeviceRepository
import com.soa.stocksecurity.data.DeviceState
import com.soa.stocksecurity.data.SettingsStore
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Estado de UI compartido por las tres pantallas.
 */
data class UiState(
    val host: String = SettingsStore.DEFAULT_HOST,
    val port: String = SettingsStore.DEFAULT_PORT,
    val device: String = SettingsStore.DEFAULT_DEVICE,
    val loading: Boolean = false,          // GET en curso
    val actionInProgress: Boolean = false, // POST en curso
    val connected: Boolean = false,        // el servicio Node-RED respondió
    val data: DeviceState? = null,         // último estado recibido
    val error: String? = null,             // mensaje de error legible
    val message: String? = null,           // feedback de una acción (ej. "Modo Stock activado")
    val lastRefresh: Long? = null,         // epoch millis del último refresh OK
)

/**
 * ViewModel único (scope de Activity) que orquesta la conexión con el
 * dispositivo a través de los endpoints de Node-RED.
 */
class DeviceViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DeviceRepository()
    private val settings = SettingsStore(app)

    var uiState by mutableStateOf(
        UiState(
            host = settings.host,
            port = settings.port,
            device = settings.device,
        )
    )
        private set

    // ---- Configuración -------------------------------------------------

    fun onHostChange(value: String) {
        uiState = uiState.copy(host = value)
        settings.host = value
    }

    fun onPortChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(5)
        uiState = uiState.copy(port = digits)
        settings.port = digits
    }

    fun onDeviceChange(value: String) {
        uiState = uiState.copy(device = value)
        settings.device = value
    }

    fun clearMessage() {
        uiState = uiState.copy(message = null, error = null)
    }

    // ---- Lectura (GET) -------------------------------------------------

    fun refresh() {
        if (uiState.host.isBlank()) {
            uiState = uiState.copy(error = "Configurá la IP del broker antes de conectar.")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                val baseUrl = DeviceRepository.buildBaseUrl(uiState.host, uiState.port)
                val state = repo.fetchDevice(baseUrl, uiState.device.trim())
                uiState = uiState.copy(
                    loading = false,
                    connected = true,
                    data = state,
                    error = null,
                    lastRefresh = state.lastUpdate ?: uiState.lastRefresh,
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    loading = false,
                    connected = false,
                    error = friendlyError(e),
                )
            }
        }
    }

    // ---- Comandos (POST) ----------------------------------------------

    fun setStockMode(on: Boolean) = runAction(
        successMsg = if (on) "Modo Stock activado" else "Modo Stock desactivado",
    ) { baseUrl, device ->
        repo.setStockMode(baseUrl, device, on)
    }

    fun setSecurityMode(on: Boolean) = runAction(
        successMsg = if (on) "Modo Seguridad activado" else "Modo Seguridad desactivado",
    ) { baseUrl, device ->
        repo.setSecurityMode(baseUrl, device, on)
    }

    /** [muted] = true apaga la alarma (MUTE); false la habilita (UNMUTE). */
    fun setAlarmMuted(muted: Boolean) = runAction(
        successMsg = if (muted) "Alarma silenciada" else "Alarma habilitada",
    ) { baseUrl, device ->
        repo.setAlarm(baseUrl, device, muted)
    }

    private fun runAction(
        successMsg: String,
        block: suspend (baseUrl: String, device: String) -> retrofit2.Response<Unit>,
    ) {
        if (uiState.host.isBlank()) {
            uiState = uiState.copy(error = "Configurá la IP del broker antes de enviar comandos.")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(actionInProgress = true, error = null, message = null)
            try {
                val baseUrl = DeviceRepository.buildBaseUrl(uiState.host, uiState.port)
                val response = block(baseUrl, uiState.device.trim())
                if (response.isSuccessful) {
                    uiState = uiState.copy(actionInProgress = false, message = successMsg)
                    refresh() // sincronizamos el estado real tras el comando
                } else {
                    uiState = uiState.copy(
                        actionInProgress = false,
                        error = "El servidor respondió ${response.code()}.",
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(actionInProgress = false, error = friendlyError(e))
            }
        }
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is IOException -> "No se pudo conectar. Verificá la IP, el puerto y que estés en la misma red."
        else -> e.message ?: "Ocurrió un error inesperado."
    }
}
