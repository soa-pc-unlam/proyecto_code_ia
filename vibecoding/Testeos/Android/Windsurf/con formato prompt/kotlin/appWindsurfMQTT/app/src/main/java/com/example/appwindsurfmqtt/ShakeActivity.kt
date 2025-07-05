package com.example.appwindsurfmqtt

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ShakeActivity : AppCompatActivity(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastUpdate: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private val shakeThreshold = 800 // Adjust this value to change shake sensitivity
    
    private val colors = listOf(
        android.R.color.holo_red_dark,
        android.R.color.black,
        android.R.color.holo_blue_dark,
        android.R.color.holo_green_dark,
        android.R.color.holo_orange_dark
    )
    private var currentColorIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shake)
        
        // Initialize the sensor service
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        // Register the sensor listener when the activity is in the foreground
        sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun onPause() {
        super.onPause()
        // Unregister the sensor listener when the activity is paused
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val currentTime = System.currentTimeMillis()
            
            // Only check for shake every 100ms
            if ((currentTime - lastUpdate) > 100) {
                val timeDiff = currentTime - lastUpdate
                lastUpdate = currentTime
                
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val speed = Math.abs(x + y + z - lastX - lastY - lastZ) / timeDiff * 10000
                
                if (speed > shakeThreshold) {
                    // Shake detected, change the background color
                    changeBackgroundColor()
                }
                
                lastX = x
                lastY = y
                lastZ = z
            }
        }
    }
    
    private fun changeBackgroundColor() {
        // Get the next color in the list
        currentColorIndex = (currentColorIndex + 1) % colors.size
        val color = ContextCompat.getColor(this, colors[currentColorIndex])
        
        // Change the background color
        window.decorView.setBackgroundColor(color)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
}
