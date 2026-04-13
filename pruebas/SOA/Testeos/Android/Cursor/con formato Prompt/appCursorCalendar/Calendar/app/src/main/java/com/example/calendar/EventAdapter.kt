package com.example.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class EventAdapter(private val onClick: (Event) -> Unit) :
    ListAdapter<Event, EventAdapter.EventViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Event, newItem: Event) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EventViewHolder(itemView: View, val onClick: (Event) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.eventTitle)
        private val time: TextView = itemView.findViewById(R.id.eventTime)
        private var currentEvent: Event? = null

        fun bind(event: Event) {
            currentEvent = event
            title.text = event.title
            time.text = "${formatHour(event.startTime)} - ${formatHour(event.endTime)}"
            itemView.setOnClickListener { onClick(event) }
        }

        private fun formatHour(millis: Long): String {
            val cal = Calendar.getInstance()
            cal.timeInMillis = millis
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min = cal.get(Calendar.MINUTE)
            return String.format("%02d:%02d", hour, min)
        }
    }
} 