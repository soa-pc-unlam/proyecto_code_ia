package com.example.calendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import java.util.*
import android.graphics.Color
import android.text.style.ForegroundColorSpan
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.threetenabp.AndroidThreeTen
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId

class MainActivity : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var addButton: Button
    private lateinit var recyclerView: RecyclerView
    private val viewModel: EventViewModel by viewModels { EventViewModelFactory(application) }
    private var selectedDate: CalendarDay? = null
    private var allEvents: List<Event> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidThreeTen.init(this)
        setContentView(R.layout.activity_main)

        calendarView = findViewById(R.id.calendarView)
        addButton = findViewById(R.id.addButton)
        recyclerView = findViewById(R.id.recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = EventAdapter { event ->
            val intent = Intent(this, EventDetailActivity::class.java)
            intent.putExtra("event_id", event.id)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // Decorador para días según el mes visible
        fun updateMonthDecorators(month: Int) {
            calendarView.removeDecorators()
            // Decorador para días con eventos
            val datesWithEvents = allEvents.map {
                val localDate = Instant.ofEpochMilli(it.date)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                CalendarDay.from(localDate)
            }.toSet()
            calendarView.addDecorator(CurrentMonthDayDecorator(month))
            calendarView.addDecorator(OtherMonthDayDecorator(month))
            calendarView.addDecorator(EventDecorator(datesWithEvents))

        }
        // Inicializar decoradores con el mes actual
        var currentMonth = calendarView.currentDate.month
        updateMonthDecorators(currentMonth)

        // Actualizar decoradores al cambiar de mes
        calendarView.setOnMonthChangedListener { _, date ->
            currentMonth = date.month
            updateMonthDecorators(currentMonth)
        }

        // Selección de día
        calendarView.setOnDateChangedListener { _, date, _ ->
            selectedDate = date
            val millis = getDateMillis(date)
            viewModel.getEventsByDate(millis).observe(this, Observer { events ->
                adapter.submitList(events)
            })
        }

        // Botón para agregar evento
        addButton.setOnClickListener {
            selectedDate?.let {
                val intent = Intent(this, AddEventActivity::class.java)
                intent.putExtra("date", getDateMillis(it))
                startActivity(intent)
            }
        }

        // Decorador para marcar días con eventos y actualizar colores al recibir eventos
        viewModel.getAllEvents().observe(this, Observer { events ->
            allEvents = events
            updateMonthDecorators(currentMonth)
        })
    }

    private fun getDateMillis(date: CalendarDay): Long {
        val cal = Calendar.getInstance()
        cal.set(date.year, date.month - 1, date.day, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Decorador para marcar días con eventos
    class EventDecorator(private val dates: Set<CalendarDay>) : DayViewDecorator {
        override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)
        override fun decorate(view: DayViewFacade) {
            view.addSpan(ForegroundColorSpan(Color.RED))
        }
    }

    // Decorador para días del mes actual (negro)
    class CurrentMonthDayDecorator(private val currentMonth: Int) : DayViewDecorator {
        override fun shouldDecorate(day: CalendarDay): Boolean = day.month == currentMonth
        override fun decorate(view: DayViewFacade) {
            view.addSpan(ForegroundColorSpan(Color.BLACK))
        }
    }

    // Decorador para días de otros meses (gris)
    class OtherMonthDayDecorator(private val currentMonth: Int) : DayViewDecorator {
        override fun shouldDecorate(day: CalendarDay): Boolean = day.month != currentMonth
        override fun decorate(view: DayViewFacade) {
            view.addSpan(ForegroundColorSpan(Color.parseColor("#B0B0B0")))
        }
    }
}