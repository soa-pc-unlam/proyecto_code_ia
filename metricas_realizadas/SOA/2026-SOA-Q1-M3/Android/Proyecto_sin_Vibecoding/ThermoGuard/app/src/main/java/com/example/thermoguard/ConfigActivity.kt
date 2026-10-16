package com.example.thermoguard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * ACTIVIDAD: ConfigActivity
 * DESCRIPCIÓN: Dashboard principal. Visualiza el estado de la FSM del hardware
 * mediante icono + texto y permite la navegación modular.
 */
class ConfigActivity : AppCompatActivity() {

    private lateinit var tvStatusMqtt: TextView
    private lateinit var tvStatusHardware: TextView
    private var ivStatusHardware: ImageView? = null   // nullable: no crashea si falta el id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        tvStatusMqtt = findViewById(R.id.tvEstado)
        tvStatusHardware = findViewById(R.id.tvEstadoHardware)
        ivStatusHardware = findViewById(R.id.ivStatusHardware)

        if (ivStatusHardware == null) {
            Log.e("ConfigActivity", "ivStatusHardware NO existe en activity_config.xml. Revisá el id.")
        }

        // Navegación de módulos
        findViewById<MaterialCardView>(R.id.cardTermometro).setOnClickListener { startActivity(Intent(this, ThermometerActivity::class.java)) }
        findViewById<MaterialCardView>(R.id.cardModo).setOnClickListener { startActivity(Intent(this, ModoActivity::class.java)) }
        findViewById<MaterialCardView>(R.id.cardSensor).setOnClickListener { startActivity(Intent(this, SensorActivity::class.java)) }
        findViewById<MaterialCardView>(R.id.cardBoton).setOnClickListener { startActivity(Intent(this, BotonActivity::class.java)) }
        findViewById<MaterialCardView>(R.id.cardHistorial).setOnClickListener { startActivity(Intent(this, HistorialActivity::class.java)) }

        findViewById<MaterialButton>(R.id.btnDesconectar).setOnClickListener {
            MqttManager.mqttDisconnect()
            finish()
        }
    }

    /**
     * Cambia dinámicamente el icono y el texto del hardware según el estado de la FSM.
     */
    private fun updateHardwareStatus(status: String) {
        val colorRes: Int
        val desc: String
        val iconRes: Int

        when (status) {
            Constants.STATE_ALARMA -> {
                desc = Constants.DESC_ESTADO_ALARMA
                colorRes = R.color.status_red
                iconRes = R.drawable.alarm_bell_icon_icons_com_68596
            }
            Constants.STATE_MONITOREANDO -> {
                desc = Constants.DESC_ESTADO_MONITOREANDO
                colorRes = R.color.status_green
                iconRes = R.drawable.smart_cctv_camera_monitoring_camera_protection_safety_icon_218664
            }
            Constants.STATE_IDLE -> {
                desc = Constants.DESC_ESTADO_IDLE
                colorRes = R.color.text_secondary
                iconRes = R.drawable.idle_4
            }
            else -> {
                Log.w("ConfigActivity", "Estado NO reconocido: [$status]")
                desc = status
                colorRes = R.color.text_sub
                iconRes = R.drawable.idle_4
            }
        }

        runOnUiThread {
            // Texto (esto ya funcionaba, lo dejo como respaldo visible)
            tvStatusHardware.text = desc
            tvStatusHardware.setTextColor(ContextCompat.getColor(this, colorRes))

            // Icono
            ivStatusHardware?.apply {
                setImageDrawable(ContextCompat.getDrawable(this@ConfigActivity, iconRes))
                setColorFilter(ContextCompat.getColor(this@ConfigActivity, colorRes))
                visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncMqttStatus()

        MqttManager.onMessage = { topic, msg ->
            if (topic == Constants.TOPIC_SENSOR_ESTADO) {
                val limpio = msg.trim()
                    .removeSurrounding("\"")   
                    .trim()
                    .uppercase()
                updateHardwareStatus(limpio)
            }
        }
    }

    private fun syncMqttStatus() {
        if (MqttManager.isConnected()) {
            tvStatusMqtt.text = getString(R.string.status_connected)
            tvStatusMqtt.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            finish()
        }
    }
}