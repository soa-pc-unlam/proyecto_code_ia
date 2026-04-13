package com.example.calendar

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.launch

class EventViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).eventDao()

    fun getEventsByDate(date: Long): LiveData<List<Event>> = dao.getEventsByDate(date)
    fun getAllEvents(): LiveData<List<Event>> = dao.getAllEvents()

    fun insert(event: Event) = viewModelScope.launch {
        dao.insert(event)
    }
}

class EventViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 