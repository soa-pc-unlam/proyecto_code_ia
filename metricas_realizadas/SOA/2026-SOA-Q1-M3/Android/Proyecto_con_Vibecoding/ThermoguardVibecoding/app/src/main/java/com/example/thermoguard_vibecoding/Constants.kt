package com.example.thermoguard_vibecoding

object Constants {
    const val PREFS_NAME = "ThermoGuardPrefs"
    const val KEY_BROKER = "mqtt_broker"
    const val KEY_PORT = "mqtt_port"
    
    const val DEFAULT_BROKER = "test.mosquitto.org"
    const val DEFAULT_PORT = "1883"
    
    const val API_BASE_IP = "172.20.10.8"
    const val API_HISTORIAL_PATH = "/api/historial"
    const val DEFAULT_DEVICE_ID = "esp32-01"
    const val DEFAULT_LIMIT = "10"
    
    // MQTT Topics
    const val TOPIC_SENSOR_ESTADO = "grupoM3/sensor/estado"
    const val TOPIC_SENSOR_TEMP = "grupoM3/sensor/temperatura"
    const val TOPIC_SET_MODO = "grupoM3/set/modo"
    const val TOPIC_COMANDO_ALARMA = "grupoM3/comando"
    
    // Payloads - Commands
    const val CMD_OFF = "APAGAR"
    const val CMD_ON = "ENCENDER"
    
    // Payloads - Modes
    const val MODE_LOW = "BAJO"
    const val MODE_MEDIUM = "MEDIO"
    const val MODE_HIGH = "ALTO"
    
    // States
    const val STATE_IDLE = "IDLE"
    const val STATE_MONITORING = "MONITOREANDO"
    const val STATE_ALERT = "ALERTA"
    
    // Thermal Limits
    const val TEMP_COLD_MIN = 0.0f
    const val TEMP_COLD_MAX = 16.6f
    const val TEMP_MEDIUM_MAX = 33.3f
    const val TEMP_HOT_MAX = 50.0f
    
    const val NOTIFICATION_CHANNEL_ID = "thermoguard_alerts"
    const val NOTIFICATION_ID = 1001
}