package com.example.thermoguard_vibecoding

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.thermoguard_vibecoding.databinding.ActivityModoBinding

class ModoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModoBinding
    private lateinit var mqttManager: MqttManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mqttManager = MqttManager.getInstance(this)

        binding.tvModoActual.text = getString(R.string.modo_actual_prefix, "---")

        binding.btnLow.setOnClickListener {
            sendMode(Constants.MODE_LOW)
        }
        binding.btnMedium.setOnClickListener {
            sendMode(Constants.MODE_MEDIUM)
        }
        binding.btnHigh.setOnClickListener {
            sendMode(Constants.MODE_HIGH)
        }
        
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun sendMode(mode: String) {
        if (mqttManager.isConnected()) {
            mqttManager.publish(Constants.TOPIC_SET_MODO, mode, 1)
            
            // Update UI feedback
            binding.tvModoActual.text = getString(R.string.modo_actual_prefix, mode)
            Toast.makeText(this, getString(R.string.toast_modo_enviado, mode), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "MQTT not connected", Toast.LENGTH_SHORT).show()
        }
    }
}