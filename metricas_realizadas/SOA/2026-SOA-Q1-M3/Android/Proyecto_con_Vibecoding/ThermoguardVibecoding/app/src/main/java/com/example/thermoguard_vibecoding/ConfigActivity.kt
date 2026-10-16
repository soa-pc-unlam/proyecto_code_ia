package com.example.thermoguard_vibecoding

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.thermoguard_vibecoding.databinding.ActivityConfigBinding
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage

class ConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding
    private lateinit var mqttManager: MqttManager
    private var currentState = Constants.STATE_IDLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mqttManager = MqttManager.getInstance(this)

        binding.btnMonitor.setOnClickListener {
            startActivity(Intent(this, ThermometerActivity::class.java))
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }

        binding.btnSensor.setOnClickListener {
            startActivity(Intent(this, SensorActivity::class.java))
        }

        binding.btnModo.setOnClickListener {
            startActivity(Intent(this, ModoActivity::class.java))
        }

        binding.btnToggleState.setOnClickListener {
            // Repurposing this button or adding others for Modo/Boton activities if needed
            // Based on prompt, we might need navigation to ModoActivity and BotonActivity
            startActivity(Intent(this, BotonActivity::class.java))
        }
        
        // Add navigation to ModoActivity - let's adjust layout later if needed or use a long click
        binding.btnToggleState.setOnLongClickListener {
            startActivity(Intent(this, ModoActivity::class.java))
            true
        }
    }

    override fun onResume() {
        super.onResume()
        mqttManager.addListener(mqttCallback)
        updateUi(currentState)
    }

    override fun onPause() {
        super.onPause()
        mqttManager.removeListener(mqttCallback)
    }

    private val mqttCallback = object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            mqttManager.subscribe(Constants.TOPIC_SENSOR_ESTADO)
        }
        override fun connectionLost(cause: Throwable?) {}
        override fun messageArrived(topic: String?, message: MqttMessage?) {
            if (topic == Constants.TOPIC_SENSOR_ESTADO) {
                val rawPayload = message?.toString() ?: ""
                val payload = rawPayload.trim().replace("\"", "").uppercase()
                runOnUiThread {
                    currentState = payload
                    updateUi(payload)
                }
            }
        }
        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
    }

    private fun updateUi(state: String) {
        binding.tvStatus.text = state
        val (colorRes, iconRes) = when (state) {
            Constants.STATE_MONITORING -> R.color.status_green to android.R.drawable.ic_dialog_info
            Constants.STATE_ALERT -> R.color.status_red to android.R.drawable.ic_dialog_alert
            else -> R.color.status_blue to android.R.drawable.ic_dialog_info
        }

        val color = ContextCompat.getColor(this, colorRes)
        binding.ivStatus.setImageResource(iconRes)
        binding.ivStatus.imageTintList = ColorStateList.valueOf(color)
        binding.btnToggleState.text = "COMMANDS"
    }
}