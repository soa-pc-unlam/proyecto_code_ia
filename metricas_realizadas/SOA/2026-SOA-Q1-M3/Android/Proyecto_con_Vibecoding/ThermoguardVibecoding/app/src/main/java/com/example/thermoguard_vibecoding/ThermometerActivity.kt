package com.example.thermoguard_vibecoding

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.thermoguard_vibecoding.databinding.ActivityThermometerBinding
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage

class ThermometerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThermometerBinding
    private lateinit var mqttManager: MqttManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThermometerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mqttManager = MqttManager.getInstance(this)
    }

    override fun onResume() {
        super.onResume()
        mqttManager.addListener(mqttCallback)
        mqttManager.subscribe(Constants.TOPIC_SENSOR_TEMP)
    }

    override fun onPause() {
        super.onPause()
        mqttManager.removeListener(mqttCallback)
    }

    private val mqttCallback = object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            mqttManager.subscribe(Constants.TOPIC_SENSOR_TEMP)
        }
        override fun connectionLost(cause: Throwable?) {}
        override fun messageArrived(topic: String?, message: MqttMessage?) {
            if (topic == Constants.TOPIC_SENSOR_TEMP) {
                val rawPayload = message?.toString() ?: "0.0"
                val payload = rawPayload.trim().replace("\"", "")
                val temp = payload.toFloatOrNull() ?: 0.0f
                runOnUiThread {
                    binding.thermometerView.setTemperature(temp)
                    binding.tvTemp.text = getString(R.string.temp_label, temp)
                    
                    val colorRes = when {
                        temp <= Constants.TEMP_COLD_MAX -> R.color.status_blue
                        temp <= Constants.TEMP_MEDIUM_MAX -> R.color.status_green
                        else -> R.color.status_red
                    }
                    binding.tvTemp.setTextColor(ContextCompat.getColor(this@ThermometerActivity, colorRes))
                }
            }
        }
        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
    }
}