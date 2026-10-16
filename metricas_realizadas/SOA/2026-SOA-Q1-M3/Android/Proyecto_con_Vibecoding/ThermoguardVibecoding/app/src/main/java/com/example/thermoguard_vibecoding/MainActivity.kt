package com.example.thermoguard_vibecoding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.thermoguard_vibecoding.databinding.ActivityMainBinding
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mqttManager: MqttManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mqttManager = MqttManager.getInstance(this)
        
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val savedBroker = prefs.getString(Constants.KEY_BROKER, Constants.DEFAULT_BROKER)
        val savedPort = prefs.getString(Constants.KEY_PORT, Constants.DEFAULT_PORT)
        
        binding.etBroker.setText(savedBroker)
        binding.etPort.setText(savedPort)

        binding.btnConnect.setOnClickListener {
            val broker = binding.etBroker.text.toString().trim()
            val port = binding.etPort.text.toString().trim()
            
            if (broker.isNotEmpty() && port.isNotEmpty()) {
                prefs.edit().apply {
                    putString(Constants.KEY_BROKER, broker)
                    putString(Constants.KEY_PORT, port)
                }.apply()
                connectToMqtt(broker, port)
            } else {
                Toast.makeText(this, "Please enter broker and port", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToMqtt(broker: String, port: String) {
        val serverUri = "tcp://$broker:$port"
        binding.btnConnect.isEnabled = false
        
        mqttManager.connect(serverUri, "ThermoGuardClient_${System.currentTimeMillis()}", {
            runOnUiThread {
                Toast.makeText(this, "Connected successfully", Toast.LENGTH_SHORT).show()
                mqttManager.subscribe(Constants.TOPIC_SENSOR_TEMP)
                startService(broker, port)
                navigateToDashboard()
            }
        }, {
            runOnUiThread {
                binding.btnConnect.isEnabled = true
                Toast.makeText(this, "Connection failed: ${it?.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun startService(broker: String, port: String) {
        val intent = Intent(this, MqttService::class.java).apply {
            putExtra("broker", broker)
            putExtra("port", port)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, ConfigActivity::class.java)
        startActivity(intent)
        finish()
    }
}