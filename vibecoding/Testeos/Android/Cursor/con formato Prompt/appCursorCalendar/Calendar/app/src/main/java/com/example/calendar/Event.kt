package com.example.calendar

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val date: Long, // Solo la fecha (sin hora)
    val startTime: Long, // Hora de inicio (en millis)
    val endTime: Long // Hora de fin (en millis)
) 