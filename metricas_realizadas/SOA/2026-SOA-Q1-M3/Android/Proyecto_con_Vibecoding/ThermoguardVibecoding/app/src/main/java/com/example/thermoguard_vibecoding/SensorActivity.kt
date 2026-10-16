package com.example.thermoguard_vibecoding

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.thermoguard_vibecoding.databinding.ActivitySensorBinding
import kotlin.math.sqrt

class SensorActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivitySensorBinding
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var mqttManager: MqttManager
    
    private var lastShake: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mqttManager = MqttManager.getInstance(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            binding.tvShakeStatus.text = "Error: Accelerometer not found"
            Toast.makeText(this, "Device does not have an accelerometer", Toast.LENGTH_LONG).show()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Algorithm: magnitud = sqrt(x² + y² + z²), fuerza_g = magnitud - 9.8f
            val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val forceG = magnitude - SensorManager.GRAVITY_EARTH

            // Threshold: forceG > 13f
            if (forceG > 13f) {
                val currentTime = System.currentTimeMillis()
                // Cooldown: 1500ms
                if (currentTime - lastShake > 1500) {
                    lastShake = currentTime
                    onShakeDetected()
                }
            }
        }
    }

    private fun onShakeDetected() {
        binding.tvShakeStatus.text = "SHAKE DETECTED!"
        
        if (mqttManager.isConnected()) {
            mqttManager.publish(Constants.TOPIC_COMANDO_ALARMA, Constants.CMD_OFF, 1)
            Toast.makeText(this, "Shake detected! APAGAR command sent.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Shake detected! (MQTT not connected)", Toast.LENGTH_SHORT).show()
        }

        // Reset text after 1.5 seconds
        binding.tvShakeStatus.postDelayed({
            binding.tvShakeStatus.text = "Waiting for Shake..."
        }, 1500)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}