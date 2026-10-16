package com.example.thermoguard

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlin.math.sqrt

/**
 * ACTIVIDAD: SensorActivity
 * DESCRIPCIÓN: Módulo de seguridad que apaga alarmas remotas al agitar el dispositivo.
 */
class SensorActivity : AppCompatActivity(), SensorEventListener {

    private var lastShake = 0L
    private lateinit var sm: SensorManager
    private var accel: Sensor? = null
    private lateinit var tvStatus: TextView

    /** Configura el hardware del acelerómetro. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor)

        tvStatus = findViewById(R.id.tvEstadoSensor)
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accel == null) tvStatus.text = getString(R.string.sensor_no_acelerometro)
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sm.unregisterListener(this)
    }

    /** Monitorea cambios en la aceleración para detectar sacudidas. */
    override fun onSensorChanged(event: SensorEvent) {
        val g = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]) - 9.8f
        if (g > 13f && System.currentTimeMillis() - lastShake > 1500) {
            lastShake = System.currentTimeMillis()
            notificar()
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    /** Ejecuta el comando de apagado vía MQTT. */
    private fun notificar() {
        tvStatus.text = getString(R.string.sensor_agitacion_detectada)
        if (MqttManager.isConnected()) {
            MqttManager.mqttPublish(Constants.TOPIC_COMANDO_ALARMA, Constants.MSG_ALARMA_OFF, 1)
            Toast.makeText(this, getString(R.string.sensor_alarma_apagada), Toast.LENGTH_SHORT).show()
        }
        tvStatus.postDelayed({ tvStatus.text = getString(R.string.estado_reposo) }, 1500)
    }
}
