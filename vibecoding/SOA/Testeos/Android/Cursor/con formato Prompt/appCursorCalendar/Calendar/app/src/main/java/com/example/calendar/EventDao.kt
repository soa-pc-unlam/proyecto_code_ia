package com.example.calendar

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface EventDao {
    @Query("SELECT * FROM Event WHERE date = :date")
    fun getEventsByDate(date: Long): LiveData<List<Event>>

    @Query("SELECT * FROM Event")
    fun getAllEvents(): LiveData<List<Event>>

    @Insert
    suspend fun insert(event: Event)

    @Query("SELECT * FROM Event WHERE id = :id")
    suspend fun getEventById(id: Int): Event?
} 