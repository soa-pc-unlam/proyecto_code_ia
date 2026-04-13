package com.example.appwindsurfmqtt

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MainActivity : AppCompatActivity() {
    private lateinit var mqttClient: MqttClient
    private val serverUri = "tcp://test.mosquitto.org:1883"
    private val clientId = "AndroidClient_${System.currentTimeMillis()}"
    private val persistence = MemoryPersistence()
    private val subscriptionTopic = "/casa/cmd"
    private val publishTopic = "/casa/luz"
    
    private lateinit var messageEditText: EditText
    private lateinit var btnPublish: Button
    private lateinit var btnOpenShake: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize UI components
        messageEditText = findViewById(R.id.messageEditText)
        btnPublish = findViewById(R.id.btnPublish)
        btnOpenShake = findViewById(R.id.btnOpenShake)
        
        // Set up button click listeners
        btnPublish.setOnClickListener {
            publishMessage()
        }
        
        btnOpenShake.setOnClickListener {
            startActivity(Intent(this, ShakeActivity::class.java))
        }
        
        // Initialize MQTT client
        initializeMqttClient()
    }
    
    private fun initializeMqttClient() {
        try {
            mqttClient = MqttClient(serverUri, clientId, persistence)
            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable) {
                    Log.e("MQTT", "Connection lost: ${cause.message}")
                    // Try to reconnect
                    connectToMqttBroker()
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    // This is called when a message arrives from the server
                    val msg = String(message.payload)
                    Log.d("MQTT", "Message arrived: $msg")
                    
                    // Update UI on the main thread
                    runOnUiThread {
                        messageEditText.setText(msg)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {
                    // Not used in this example
                    Log.d("MQTT", "Message delivered")
                }
            })

            // Connect to MQTT broker
            connectToMqttBroker()
        } catch (e: MqttException) {
            e.printStackTrace()
            Log.e("MQTT", "Error initializing MQTT client: ${e.message}")
        }
    }
    
    private fun connectToMqttBroker() {
        try {
            if (!mqttClient.isConnected) {
                val connOpts = MqttConnectOptions()
                connOpts.isCleanSession = true
                connOpts.isAutomaticReconnect = true
                
                // Connect to the MQTT broker
                mqttClient.connect(connOpts)
                Log.d("MQTT", "Connected to MQTT broker")
                
                // Subscribe to topic after successful connection
                subscribeToTopic()
                
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Connected to MQTT broker",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: MqttException) {
            Log.e("MQTT", "Error connecting to MQTT broker: ${e.message}")
            e.printStackTrace()
            
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Connection failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun subscribeToTopic() {
        try {
            mqttClient.subscribe(subscriptionTopic, 0)
            Log.d("MQTT", "Subscribed to $subscriptionTopic")
            
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Subscribed to $subscriptionTopic",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: MqttException) {
            Log.e("MQTT", "Error subscribing to topic: ${e.message}")
            e.printStackTrace()
            
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Subscription failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun publishMessage() {
        try {
            val message = "ON" // You can modify this to send different messages
            val mqttMessage = MqttMessage(message.toByteArray())
            mqttMessage.qos = 0
            mqttMessage.isRetained = false
            
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.publish(publishTopic, mqttMessage)
                Log.d("MQTT", "Message published to $publishTopic")
                
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Message published: $message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Log.e("MQTT", "MQTT client not connected")
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Not connected to MQTT broker",
                        Toast.LENGTH_LONG
                    ).show()
                    // Try to reconnect
                    connectToMqttBroker()
                }
            }
        } catch (e: MqttException) {
            Log.e("MQTT", "Error publishing message: ${e.message}")
            e.printStackTrace()
            
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Publish failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.disconnect()
                Log.d("MQTT", "Disconnected from MQTT broker")
            }
        } catch (e: MqttException) {
            Log.e("MQTT", "Error disconnecting from MQTT broker: ${e.message}")
            e.printStackTrace()
        }
    }
}