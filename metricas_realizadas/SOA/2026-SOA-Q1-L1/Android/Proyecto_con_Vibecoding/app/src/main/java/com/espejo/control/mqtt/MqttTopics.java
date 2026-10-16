package com.espejo.control.mqtt;

public final class MqttTopics {

    // Telemetria entrante (suscripcion)
    public static final String SUB_DISTANCIA1 = "espejo/sensor/distancia1";
    public static final String SUB_DISTANCIA2 = "espejo/sensor/distancia2";
    public static final String SUB_LUZ = "espejo/sensor/luz";

    // Comandos salientes (publicacion)
    public static final String PUB_MODO = "espejo/control/modo";
    public static final String PUB_SERVO = "espejo/control/servo";
    public static final String PUB_LUZ = "espejo/control/luz";
    public static final String PUB_LED = "espejo/control/led";

    private MqttTopics() {
    }
}
