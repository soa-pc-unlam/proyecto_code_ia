package com.example.thermoguard_vibecoding

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.thermoguard_vibecoding.databinding.ActivityHistorialBinding
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class TelemetryEntry(val dev: String, val temp: Double, val date: String)

class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private val historyList = mutableListOf<TelemetryEntry>()
    private lateinit var adapter: HistorialAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize fields with defaults
        binding.etIp.setText(Constants.API_BASE_IP)
        binding.etDeviceId.setText(Constants.DEFAULT_DEVICE_ID)
        binding.etLimit.setText(Constants.DEFAULT_LIMIT)

        adapter = HistorialAdapter(historyList)
        binding.rvHistory.adapter = adapter

        binding.btnRefresh.setOnClickListener {
            val ip = binding.etIp.text.toString().trim()
            val deviceId = binding.etDeviceId.text.toString().trim()
            val limit = binding.etLimit.text.toString().trim()

            if (ip.isNotEmpty() && deviceId.isNotEmpty() && limit.isNotEmpty()) {
                fetchHistory(ip, deviceId, limit)
            } else {
                Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchHistory(ip: String, deviceId: String, limit: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRefresh.isEnabled = false
        
        thread {
            try {
                // Construction: http://{IP}:1880/api/historial?limit={LIMIT}&device_id={DEV_ID}
                val urlString = "http://$ip:1880/api/historial?limit=$limit&device_id=$deviceId"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    val jsonArray = jsonResponse.getJSONArray("data")
                    
                    historyList.clear()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        historyList.add(
                            TelemetryEntry(
                                obj.getString("device_id"),
                                obj.getDouble("temperature"),
                                obj.getString("created_at")
                            )
                        )
                    }

                    runOnUiThread {
                        adapter.notifyDataSetChanged()
                        binding.progressBar.visibility = View.GONE
                        binding.btnRefresh.isEnabled = true
                    }
                } else {
                    Log.e("HistorialActivity", "Server error: ${connection.responseCode}")
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.btnRefresh.isEnabled = true
                        Toast.makeText(this, "Server Error: ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("HistorialActivity", "Error fetching history", e)
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRefresh.isEnabled = true
                    Toast.makeText(this, "Connection Error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class HistorialAdapter(private val items: List<TelemetryEntry>) :
        RecyclerView.Adapter<HistorialAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDevice: TextView = view.findViewById(R.id.tvDevice)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val tvTemp: TextView = view.findViewById(R.id.tvHistoryTemp)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvDevice.text = "Device: ${item.dev}"
            holder.tvDate.text = "Date: ${item.date}"
            holder.tvTemp.text = String.format("%.1f °C", item.temp)
        }

        override fun getItemCount() = items.size
    }
}