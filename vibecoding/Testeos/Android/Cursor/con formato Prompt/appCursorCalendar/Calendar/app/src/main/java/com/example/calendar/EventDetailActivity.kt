package com.example.calendar

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.*

class EventDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        val eventId = intent.getIntExtra("event_id", -1)
        val db = AppDatabase.getDatabase(this)
        val title = findViewById<TextView>(R.id.detailTitle)
        val desc = findViewById<TextView>(R.id.detailDesc)
        val time = findViewById<TextView>(R.id.detailTime)

        lifecycleScope.launch {
            val event = db.eventDao().getEventById(eventId)
            event?.let {
                title.text = it.title
                desc.text = it.description
                time.text = "${formatHour(it.startTime)} - ${formatHour(it.endTime)}"
            }
        }
    }

    private fun formatHour(millis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        return String.format("%02d:%02d", hour, min)
    }
} 