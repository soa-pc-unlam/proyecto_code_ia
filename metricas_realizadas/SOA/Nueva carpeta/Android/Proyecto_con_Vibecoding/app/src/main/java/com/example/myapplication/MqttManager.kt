package com.example.myapplication

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.util.UUID

data class RobotState(
    var distanceLeft: Float? = null,
    var distanceRight: Float? = null,
    var light: Int? = null,
    var state: String? = null
)

class MqttManager(
    private val context: Context
) {
    private var serverUri = ""
    private var clientId = ""
    private var username: String? = null
    private var password: String? = null

    private var client: MqttClient? = null

    private val _state = MutableStateFlow(RobotState())
    val stateFlow = _state.asStateFlow()

    fun configure(
        serverUri: String,
        username: String? = null,
        password: String? = null
    ) {
        this.serverUri = serverUri
        this.clientId = UUID.randomUUID().toString()
        this.username = username
        this.password = password
    }

    fun connect() {
        if (serverUri.isBlank()) {
            throw IllegalStateException("Server URI must be configured before connecting.")
        }

        client = MqttClient(serverUri, clientId, null)

        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = false
        }
        options.userName = username
        options.password = password?.toCharArray()

        client?.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                // Handle connection loss if needed
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.toString() ?: return

                Log.i("Callback", "${topic?.padEnd(20)}: $payload")

                when (topic) {
                    "robot/sensors" -> {
                        val parts = payload.split(";")

                        if (parts.size == 3) {
                            _state.value = _state.value.copy(
                                distanceLeft = parts[0].toFloatOrNull(),
                                distanceRight = parts[1].toFloatOrNull(),
                                light = parts[2].toIntOrNull()
                            )
                        }
                    }

                    "robot/state" -> {
                        _state.value = _state.value.copy(state = payload)
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        client?.connect(options)

        subscribeDefaults()
    }

    private fun subscribeDefaults() {
        client?.subscribe("robot/sensors")
        client?.subscribe("robot/state")
    }

    fun disconnect() {
        client?.disconnect()
    }

    fun reconnect() {
        disconnect()
        connect()
    }

    fun sendCommand(command: String) {
        client?.publish("robot/command", MqttMessage(command.toByteArray()))
    }
}