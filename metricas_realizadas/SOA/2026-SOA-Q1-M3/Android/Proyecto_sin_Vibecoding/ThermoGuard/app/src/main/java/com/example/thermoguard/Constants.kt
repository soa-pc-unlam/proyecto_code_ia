package com.example.thermoguard

/**
 * CLASE: Constants
 * DESCRIPCIÓN: Repositorio central de configuraciones del sistema (MQTT, API y Rangos).
 */
object Constants {
    // Tópicos MQTT
    const val TOPIC_SET_MODO = "grupoM3/set/modo"
    const val TOPIC_SENSOR_TEMP = "grupoM3/sensor/temperatura"
    const val TOPIC_SENSOR_ESTADO = "grupoM3/sensor/estado"
    const val TOPIC_COMANDO_ALARMA = "grupoM3/comando"

    // Configuración de Red
    const val DEFAULT_BROKER = "test.mosquitto.org"
    const val DEFAULT_PORT = "1883"
    const val API_BASE_IP = "192.168.1.93"
    const val API_HISTORIAL_PATH = "/api/historial"
    const val DEFAULT_DEVICE_ID = "esp32-01"

    // Mensajes MQTT (Payloads)
    const val MSG_ALARMA_OFF = "APAGAR"
    const val MSG_ALARMA_ON = "ENCENDER"
    const val MSG_MODO_BAJO = "BAJO"
    const val MSG_MODO_MEDIO = "MEDIO"
    const val MSG_MODO_ALTO = "ALTO"

    // Estados de la FSM (Hardware)
    const val STATE_IDLE = "IDLE"
    const val STATE_MONITOREANDO = "MONITOREANDO"
    const val STATE_ALARMA = "ALARMA"

    // Textos de visualización para estados
    const val DESC_ESTADO_IDLE = "IDLE"
    const val DESC_ESTADO_MONITOREANDO = "MONITOREANDO"
    const val DESC_ESTADO_ALARMA = "¡ALERTA!"
    const val DESC_ESTADO_ESPERA = "Esperando reporte de hardware..."

    // Límites de Temperatura
    const val FRIO_MIN = 0f
    const val FRIO_MAX = 16.6f
    const val MEDIO_MAX = 33.3f
    const val CALIENTE_MAX = 50f
}
