package com.example.steeringwheel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.steeringwheel.domain.repository.LocationRepository
import com.example.steeringwheel.domain.repository.SettingsRepository
import com.example.steeringwheel.domain.repository.SteeringWheelRepository
import com.example.steeringwheel.presentation.state.SpeedUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpeedViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val steeringWheelRepository: SteeringWheelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeedUiState())
    val uiState: StateFlow<SpeedUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var lastPublishTime = 0L

    init {
        viewModelScope.launch {
            settingsRepository.getSpeedThreshold().collectLatest { threshold ->
                _uiState.update { it.copy(threshold = threshold) }
            }
        }
        
        viewModelScope.launch {
            steeringWheelRepository.connectionStatus.collectLatest { connected ->
                _uiState.update { it.copy(isMqttConnected = connected) }
                if (connected) {
                    syncThreshold()
                }
            }
        }
    }

    fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationRepository.getLocationUpdates()
                .collect { data ->
                    _uiState.update { it.copy(locationData = data) }
                    publishSpeed(data.speedKmh)
                }
        }
    }

    fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
    }

    private fun publishSpeed(speed: Int) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPublishTime >= 2000 && speed != _uiState.value.lastSentSpeed) {
            viewModelScope.launch {
                steeringWheelRepository.sendCommand("VELOCIDAD:$speed")
                _uiState.update { it.copy(lastSentSpeed = speed) }
                lastPublishTime = currentTime
            }
        }
    }

    fun updateThreshold(newThreshold: Int) {
        if (newThreshold in 1..100) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true) }
                settingsRepository.updateSpeedThreshold(newThreshold)
                steeringWheelRepository.setSpeedThreshold(newThreshold)
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
    
    private suspend fun syncThreshold() {
        val threshold = _uiState.value.threshold
        steeringWheelRepository.setSpeedThreshold(threshold)
    }
}
