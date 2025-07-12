package com.example.calendar

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class AddEventActivity : AppCompatActivity() {

    private val viewModel: EventViewModel by viewModels { EventViewModelFactory(application) }
    private var date: Long = 0
    private var startTime: Long = 0
    private var endTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_event)

        date = intent.getLongExtra("date", 0)

        val titleEdit = findViewById<EditText>(R.id.titleEdit)
        val descEdit = findViewById<EditText>(R.id.descEdit)
        val startButton = findViewById<Button>(R.id.startTimeButton)
        val endButton = findViewById<Button>(R.id.endTimeButton)
        val saveButton = findViewById<Button>(R.id.saveButton)

        startButton.setOnClickListener {
            showTimePicker { millis ->
                startTime = millis
                startButton.text = formatHour(millis)
            }
        }
        endButton.setOnClickListener {
            showTimePicker { millis ->
                endTime = millis
                endButton.text = formatHour(millis)
            }
        }

        saveButton.setOnClickListener {
            val event = Event(
                title = titleEdit.text.toString(),
                description = descEdit.text.toString(),
                date = date,
                startTime = startTime,
                endTime = endTime
            )
            viewModel.insert(event)
            finish()
        }
    }

    private fun showTimePicker(onTimeSet: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onTimeSet(cal.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun formatHour(millis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        return String.format("%02d:%02d", hour, min)
    }
} 