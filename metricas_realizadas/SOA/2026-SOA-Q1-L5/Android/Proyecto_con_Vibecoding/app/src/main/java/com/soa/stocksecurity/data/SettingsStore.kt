package com.soa.stocksecurity.data

import android.content.Context

/**
 * Persistencia simple (SharedPreferences) de la configuración de conexión:
 * IP del host, puerto y identificador del dispositivo.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var port: String
        get() = prefs.getString(KEY_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
        set(value) = prefs.edit().putString(KEY_PORT, value).apply()

    var device: String
        get() = prefs.getString(KEY_DEVICE, DEFAULT_DEVICE) ?: DEFAULT_DEVICE
        set(value) = prefs.edit().putString(KEY_DEVICE, value).apply()

    companion object {
        private const val PREFS_NAME = "stock_security_prefs"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_DEVICE = "device"

        const val DEFAULT_HOST = "192.168.1.100"
        const val DEFAULT_PORT = "1880"
        const val DEFAULT_DEVICE = "corridor-01"
    }
}
