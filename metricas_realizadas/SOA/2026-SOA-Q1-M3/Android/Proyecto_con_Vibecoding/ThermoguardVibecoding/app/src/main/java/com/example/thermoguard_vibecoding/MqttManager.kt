package com.example.thermoguard_vibecoding

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttManager private constructor(context: Context) {
    private var client: MqttAsyncClient? = null
    private val persistence = MemoryPersistence()
    private val listeners = mutableListOf<MqttCallbackExtended>()

    companion object {
        private const val TAG = "MqttManager"
        @Volatile
        private var instance: MqttManager? = null

        fun getInstance(context: Context): MqttManager {
            return instance ?: synchronized(this) {
                instance ?: MqttManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun connect(serverUri: String, clientId: String, onConnect: () -> Unit = {}, onFailure: (Throwable?) -> Unit = {}) {
        try {
            if (client?.isConnected == true) {
                onConnect()
                return
            }

            client = MqttAsyncClient(serverUri, clientId, persistence)
            client?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d(TAG, "Connected to $serverURI")
                    listeners.forEach { it.connectComplete(reconnect, serverURI) }
                    onConnect()
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.e(TAG, "Connection lost", cause)
                    listeners.forEach { it.connectionLost(cause) }
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val rawPayload = message?.toString() ?: ""
                    val payload = rawPayload.trim().replace("\"", "").uppercase()
                    Log.d(TAG, "Message arrived: $topic -> $payload")
                    listeners.forEach { it.messageArrived(topic, message) }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    listeners.forEach { it.deliveryComplete(token) }
                }
            })

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
            }

            client?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Successfully connected")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to connect", exception)
                    onFailure(exception)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "MqttException: ${e.message}")
            onFailure(e)
        }
    }

    fun subscribe(topic: String, qos: Int = 1) {
        try {
            client?.subscribe(topic, qos)
            Log.d(TAG, "Subscribed to $topic")
        } catch (e: MqttException) {
            Log.e(TAG, "Subscribe failed", e)
        }
    }

    fun publish(topic: String, message: String, qos: Int = 1) {
        try {
            val mqttMessage = MqttMessage(message.toByteArray()).apply { this.qos = qos }
            client?.publish(topic, mqttMessage)
            Log.d(TAG, "Published to $topic: $message")
        } catch (e: MqttException) {
            Log.e(TAG, "Publish failed", e)
        }
    }

    fun disconnect() {
        try {
            client?.disconnect()
        } catch (e: MqttException) {
            Log.e(TAG, "Disconnect failed", e)
        }
    }

    fun isConnected(): Boolean = client?.isConnected ?: false

    fun addListener(callback: MqttCallbackExtended) {
        if (!listeners.contains(callback)) {
            listeners.add(callback)
        }
    }

    fun removeListener(callback: MqttCallbackExtended) {
        listeners.remove(callback)
    }
}