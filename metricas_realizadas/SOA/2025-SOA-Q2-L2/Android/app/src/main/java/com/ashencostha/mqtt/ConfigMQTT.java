package com.ashencostha.mqtt;

public class ConfigMQTT {

    // ================================
    //      CONFIG SERVIDOR MQTT
    // ================================
    // Usamos el mismo broker que el ESP32:
    // const char* MQTT_BROKER = "broker.emqx.io";
    // const int   MQTT_PORT   = 1883;
    //
    // La mayoría de las libs Java usan el formato "tcp://host:port"
    public static String mqttServer   = "tcp://broker.emqx.io:1883";
    public static String userName     = "";   // vacío si usás broker público sin auth
    public static String userPass     = "";
    public static String CLIENT_ID    = "android_famico_sequencer";

    // ================================
    //             TOPICS
    // ================================
    public static String topicStatus        = "/simulator/status";   // estado actual del ESP
    public static String topicReceiveMatrix = "/simulator/cellval";  // matriz que manda el ESP (cuando la implementes)

    // Android → ESP32
    public static String topicState      = "/simulator/state";    // "PLAY_ALL", "IDLE", "EDIT", "PLAY_LINE"
    public static String topicTempo      = "/simulator/tempo";    // tempo (BPM o ms)
    public static String topicEdit       = "/simulator/edit";     // "r c v"
    public static String topicPlayRow    = "/simulator/playrow";  // "r"
    public static String topicSendMatrix = "/simulator/getcell";  // matriz completa enviada desde el celu
    public static void useServerSequencer() {
        mqttServer   = "tcp://broker.emqx.io:1883";
        userName     = "";
        userPass     = "";
        CLIENT_ID    = "android_famico_sequencer";

        topicStatus        = "/simulator/status";
        topicState         = "/simulator/state";
        topicTempo         = "/simulator/tempo";
        topicEdit          = "/simulator/edit";
        topicPlayRow       = "/simulator/playrow";
        topicSendMatrix    = "/simulator/getcell";
        topicReceiveMatrix = "/simulator/cellval";
    }
}
