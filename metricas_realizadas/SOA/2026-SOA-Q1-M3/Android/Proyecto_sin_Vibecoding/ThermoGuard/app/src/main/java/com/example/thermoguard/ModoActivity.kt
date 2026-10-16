package com.example.thermoguard

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * ACTIVIDAD: ModoActivity
 * DESCRIPCIÓN: Permite configurar perfiles de temperatura mediante comandos MQTT.
 */
class ModoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_modo)

        val tvStatus = findViewById<TextView>(R.id.tvModoActual)

        findViewById<MaterialButton>(R.id.btnBajo).setOnClickListener  { dispatch(Constants.MSG_MODO_BAJO, getString(R.string.preset_bajo), tvStatus) }
        findViewById<MaterialButton>(R.id.btnMedio).setOnClickListener { dispatch(Constants.MSG_MODO_MEDIO, getString(R.string.preset_medio), tvStatus) }
        findViewById<MaterialButton>(R.id.btnAlto).setOnClickListener  { dispatch(Constants.MSG_MODO_ALTO, getString(R.string.preset_alto), tvStatus) }
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    /** Envía el payload MQTT y actualiza la etiqueta de estado. */
    private fun dispatch(msg: String, label: String, tv: TextView) {
        if (!MqttManager.isConnected()) return
        MqttManager.mqttPublish(Constants.TOPIC_SET_MODO, msg, 1)
        tv.text = getString(R.string.modo_actual_prefix, label)
        Toast.makeText(this, getString(R.string.toast_modo_enviado, label), Toast.LENGTH_SHORT).show()
    }
}
