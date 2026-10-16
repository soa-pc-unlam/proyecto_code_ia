package com.espejo.control.mqtt;

/**
 * Escucha eventos de telemetria y de estado de conexion, sin importar si el
 * origen es un broker MQTT real o el generador de datos simulados.
 */
public interface TelemetryListener {

    default void onDistancia1(String valor) {
    }

    default void onDistancia2(String valor) {
    }

    default void onLuz(String valor) {
    }

    default void onConnectionLost() {
    }
}
