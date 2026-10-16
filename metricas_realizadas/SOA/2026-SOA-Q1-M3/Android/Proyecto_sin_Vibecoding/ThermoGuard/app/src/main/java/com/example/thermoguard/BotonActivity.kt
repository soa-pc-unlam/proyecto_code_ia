package com.example.thermoguard

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * ACTIVIDAD: BotonActivity
 * DESCRIPCIÓN: Accionamiento manual para encendido de actuadores remotos.
 */
class BotonActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_boton)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnEncender).setOnClickListener {
            if (MqttManager.isConnected()) {
                MqttManager.mqttPublish(Constants.TOPIC_COMANDO_ALARMA, Constants.MSG_ALARMA_ON, 1)
                Toast.makeText(this, getString(R.string.toast_alarma_encendida), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
