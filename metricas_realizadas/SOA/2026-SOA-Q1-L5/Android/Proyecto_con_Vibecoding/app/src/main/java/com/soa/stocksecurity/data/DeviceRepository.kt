package com.soa.stocksecurity.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Repositorio que centraliza el acceso a la API de Node-RED.
 *
 * La base URL es dinámica (la IP/puerto los configura el usuario), por eso
 * cacheamos la instancia de Retrofit y solo la reconstruimos si cambia.
 */
class DeviceRepository {

    private var cachedBaseUrl: String? = null
    private var api: NodeRedApi? = null

    private fun api(baseUrl: String): NodeRedApi {
        if (baseUrl != cachedBaseUrl || api == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .writeTimeout(6, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NodeRedApi::class.java)

            cachedBaseUrl = baseUrl
        }
        return api!!
    }

    suspend fun fetchDevice(baseUrl: String, device: String): DeviceState =
        api(baseUrl).getDevice(device)

    suspend fun setStockMode(baseUrl: String, device: String, on: Boolean) =
        api(baseUrl).setStock(device, StatusCommand(if (on) "ON" else "OFF"))

    suspend fun setSecurityMode(baseUrl: String, device: String, on: Boolean) =
        api(baseUrl).setSecurity(device, StatusCommand(if (on) "ON" else "OFF"))

    /** [muted] = true -> "MUTE" (alarma apagada); false -> "UNMUTE" (alarma habilitada). */
    suspend fun setAlarm(baseUrl: String, device: String, muted: Boolean) =
        api(baseUrl).setAlarm(device, StatusCommand(if (muted) "MUTE" else "UNMUTE"))

    companion object {
        /** Construye la base URL con barra final (requerida por Retrofit). */
        fun buildBaseUrl(host: String, port: String): String {
            val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
            val cleanPort = port.trim().ifBlank { "1880" }
            return "http://$cleanHost:$cleanPort/"
        }
    }
}
