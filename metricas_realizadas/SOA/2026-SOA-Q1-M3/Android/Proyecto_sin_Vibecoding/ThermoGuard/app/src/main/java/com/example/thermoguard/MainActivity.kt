package com.example.thermoguard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

/**
 * ACTIVIDAD: MainActivity
 * DESCRIPCIÓN: Gestiona el portal de acceso y conexión inicial al broker MQTT.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var etUrl: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var btn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etBrokerUrl)
        etPort = findViewById(R.id.etPort)
        btn    = findViewById(R.id.btnConectar)

        // Cargar datos guardados o usar los por defecto
        val prefs = getSharedPreferences("MQTT_PREFS", Context.MODE_PRIVATE)
        etUrl.setText(prefs.getString("broker", Constants.DEFAULT_BROKER))
        etPort.setText(prefs.getString("port", Constants.DEFAULT_PORT))

        syncUI()

        btn.setOnClickListener {
            if (MqttManager.isConnected()) {
                MqttManager.mqttDisconnect()
                btn.postDelayed({ syncUI() }, 300)
                return@setOnClickListener
            }

            val broker = etUrl.text.toString().trim()
            val portStr = etPort.text.toString().trim()
            val port = portStr.toIntOrNull() ?: 1883

            // Guardar datos para la próxima vez
            prefs.edit().apply {
                putString("broker", broker)
                putString("port", portStr)
                apply()
            }

            btn.isEnabled = false
            btn.text = getString(R.string.status_connecting)

            MqttManager.mqttConnect(broker, port, {
                runOnUiThread {
                    MqttManager.mqttSubscribe(Constants.TOPIC_SENSOR_TEMP, 0)
                    MqttManager.mqttSubscribe(Constants.TOPIC_SENSOR_ESTADO, 1)
                    startActivity(Intent(this, ConfigActivity::class.java))
                }
            }, { err ->
                runOnUiThread {
                    syncUI()
                    Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        syncUI()
    }

    private fun syncUI() {
        btn.isEnabled = true
        if (MqttManager.isConnected()) {
            btn.text = getString(R.string.btn_desconectar)
            btn.setBackgroundColor(getColor(R.color.status_red))
        } else {
            btn.text = getString(R.string.btn_conectar)
            btn.setBackgroundColor(getColor(R.color.primary))
        }
    }
}
