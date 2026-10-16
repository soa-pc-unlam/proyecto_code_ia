package com.example.thermoguard

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * ACTIVIDAD: ThermometerActivity
 * DESCRIPCIÓN: Visualiza la telemetría térmica más reciente desde la base de datos.
 */
class ThermometerActivity : AppCompatActivity() {

    private lateinit var tvTemp: TextView
    private lateinit var tvUpdate: TextView
    private lateinit var etId: TextInputEditText
    private lateinit var btnRefresh: MaterialButton
    private lateinit var pb: ProgressBar
    private lateinit var thermView: ThermometerView

    /** Inicializa componentes y dispara la carga de telemetría. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thermometer)

        thermView = findViewById(R.id.thermometerView)
        tvTemp     = findViewById(R.id.tvTemperature)
        tvUpdate   = findViewById(R.id.tvLastUpdate)
        etId       = findViewById(R.id.etDeviceId)
        btnRefresh = findViewById(R.id.btnRefresh)
        pb         = findViewById(R.id.progressBar)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        etId.setText(Constants.DEFAULT_DEVICE_ID)
        btnRefresh.setOnClickListener { fetchData() }

        fetchData()
    }

    /** Obtiene el último registro asíncronamente vía HTTP GET. */
    private fun fetchData() {
        val devId = etId.text.toString().trim()
        pb.visibility = View.VISIBLE
        btnRefresh.isEnabled = false

        Thread {
            try {
                val ip = Constants.API_BASE_IP
                val url = "http://$ip:1880/api/historial?limit=1" + if (devId.isNotEmpty()) "&device_id=$devId" else ""
                val conn = URL(url).openConnection() as HttpURLConnection
                
                if (conn.responseCode == 200) {
                    val res = conn.inputStream.bufferedReader().use { it.readText() }
                    val dataArr = JSONObject(res).getJSONArray("data")
                    if (dataArr.length() > 0) {
                        val entry = dataArr.getJSONObject(0)
                        runOnUiThread { updateUI(entry.getDouble("temperature").toFloat(), entry.getString("created_at")) }
                    } else {
                        runOnUiThread { resetUI(); Toast.makeText(this, getString(R.string.no_data_available), Toast.LENGTH_SHORT).show() }
                    }
                } else { runOnUiThread { resetUI() } }
            } catch (e: Exception) { Log.e("THERM", "Err"); runOnUiThread { resetUI() } }
            finally { runOnUiThread { pb.visibility = View.GONE; btnRefresh.isEnabled = true } }
        }.start()
    }

    /** Actualiza los elementos visuales y el color del termómetro. */
    private fun updateUI(t: Float, d: String) {
        tvTemp.text = String.format(Locale.getDefault(), getString(R.string.temp_format), t)
        tvUpdate.text = getString(R.string.last_update_format, d)
        
        val cRes = when {
            t < Constants.FRIO_MAX -> R.color.thermometer_frio
            t < Constants.MEDIO_MAX -> R.color.thermometer_medio
            else -> R.color.thermometer_caliente
        }
        val color = ContextCompat.getColor(this, cRes)
        thermView.updateData(t, color)
        tvTemp.setTextColor(color)
        tvTemp.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).withEndAction { tvTemp.animate().scaleX(1f).scaleY(1f).start() }.start()
    }

    /** Restablece la interfaz a su estado por defecto. */
    private fun resetUI() {
        tvTemp.text = getString(R.string.default_temp)
        tvUpdate.text = getString(R.string.status_no_data)
        tvTemp.setTextColor(ContextCompat.getColor(this, R.color.text_sub))
        thermView.updateData(Constants.FRIO_MIN, ContextCompat.getColor(this, R.color.primary))
    }
}
