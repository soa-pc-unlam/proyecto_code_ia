package com.example.thermoguard

import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * OBJETO: MqttManager
 * DESCRIPCIÓN: Encapsula la comunicación MQTT mediante hilos secundarios para no bloquear la UI.
 */
object MqttManager {

    private const val TAG = "MQTT_MANAGER"
    private var mqttClient: MqttClient? = null

    /** Listener de mensajes entrantes inyectado por las actividades. */
    var onMessage: ((String, String) -> Unit)? = null

    /**
     * Conexión asíncrona al broker.
     */
    fun mqttConnect(broker: String, port: Int, onOk: () -> Unit, onErr: (String) -> Unit) {
        Thread {
            try {
                mqttClient = MqttClient("tcp://$broker:$port", MqttClient.generateClientId(), MemoryPersistence())
                mqttClient?.setCallback(object : MqttCallback {
                    override fun connectionLost(c: Throwable?) { Log.e(TAG, "Vínculo perdido") }
                    override fun messageArrived(t: String, m: MqttMessage) { onMessage?.invoke(t, String(m.payload)) }
                    override fun deliveryComplete(tk: IMqttDeliveryToken?) {}
                })
                mqttClient?.connect(MqttConnectOptions().apply { isCleanSession = true })
                onOk()
            } catch (e: Exception) { onErr(e.message ?: "Falla de red") }
        }.start()
    }

    /**
     * Suscripción asíncrona a un tópico.
     */
    fun mqttSubscribe(topic: String, qos: Int) {
        Thread { try { mqttClient?.subscribe(topic, qos) } catch (e: Exception) { Log.e(TAG, "Error sub") } }.start()
    }

    /**
     * Publicación asíncrona de un mensaje de texto.
     */
    fun mqttPublish(topic: String, msg: String, qos: Int) {
        Thread {
            try {
                val m = MqttMessage(msg.toByteArray()).apply { this.qos = qos }
                mqttClient?.publish(topic, m)
            } catch (e: Exception) { Log.e(TAG, "Error pub") }
        }.start()
    }

    /**
     * Cierre de sesión y liberación de recursos.
     */
    fun mqttDisconnect() {
        Thread {
            try {
                if (mqttClient?.isConnected == true) mqttClient?.disconnect()
                mqttClient?.close()
                mqttClient = null
            } catch (e: Exception) { Log.e(TAG, "Error disc") }
        }.start()
    }

    /**
     * Retorna el estado actual de la sesión.
     */
    fun isConnected(): Boolean = mqttClient?.isConnected == true
}
