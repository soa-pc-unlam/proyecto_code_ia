package com.example.myapplication

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

class SensorActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null

    private lateinit var directionText: TextView
    private lateinit var leftDistanceText: TextView
    private lateinit var rightDistanceText: TextView
    private lateinit var lightText: TextView

    private val mqtt by lazy {
        (application as CustomApplication).mqttManager
    }

    private var lastDirection = ""
    companion object {
        private const val THRESHOLD = 15f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.sensor)

        leftDistanceText = findViewById(R.id.leftDistanceText)
        rightDistanceText = findViewById(R.id.rightDistanceText)
        lightText = findViewById(R.id.lightText)

        val topBar = findViewById<MaterialToolbar>(R.id.topAppBar)

        topBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    ConfigModalFragment()
                        .show(supportFragmentManager, "settings")
                    true
                }

                else -> false
            }
        }

        val btnController = findViewById<Button>(R.id.btnController)

        directionText = findViewById(R.id.txtSensorOutput)

        btnController.setOnClickListener {
            finish()
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mqtt.stateFlow.collect { state ->
                    leftDistanceText.text = "Izquierda: ${state.distanceLeft?.toString() ?: "--"}"
                    rightDistanceText.text = "Derecha: ${state.distanceRight?.toString() ?: "--"}"
                    lightText.text =  "Luz: ${state.light?.toString() ?: "--"}"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        rotationSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        return
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {

            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)

            SensorManager.getRotationMatrixFromVector(
                rotationMatrix,
                event.values
            )

            SensorManager.getOrientation(
                rotationMatrix,
                orientation
            )

            val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()

            val absPitch = kotlin.math.abs(pitch)
            val absRoll = kotlin.math.abs(roll)

            val direction = when {

                absPitch < THRESHOLD &&
                        absRoll < THRESHOLD -> {
                    "stop"
                }

                absPitch >= absRoll -> {

                    when {
                        pitch < -THRESHOLD -> "backward"
                        pitch > THRESHOLD -> "forward"
                        else -> "stop"
                    }
                }

                else -> {

                    when {
                        roll > THRESHOLD -> "right"
                        roll < -THRESHOLD -> "left"
                        else -> "stop"
                    }
                }
            }

            // Solo enviar si cambió la dirección, para no saturar MQTT
            if (direction != lastDirection) {
                lastDirection = direction
                mqtt.sendCommand(direction)
            }

            directionText.text = direction
        }
    }
}