package com.example.thermoguard_vibecoding

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.thermoguard_vibecoding.databinding.ActivityBotonBinding

class BotonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBotonBinding
    private lateinit var mqttManager: MqttManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBotonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mqttManager = MqttManager.getInstance(this)

        binding.btnOn.setOnClickListener {
            sendCommand(Constants.CMD_ON)
        }
        binding.btnOff.setOnClickListener {
            sendCommand(Constants.CMD_OFF)
        }
    }

    private fun sendCommand(cmd: String) {
        mqttManager.publish(Constants.TOPIC_COMANDO_ALARMA, cmd)
        Toast.makeText(this, "Command $cmd sent", Toast.LENGTH_SHORT).show()
    }
}