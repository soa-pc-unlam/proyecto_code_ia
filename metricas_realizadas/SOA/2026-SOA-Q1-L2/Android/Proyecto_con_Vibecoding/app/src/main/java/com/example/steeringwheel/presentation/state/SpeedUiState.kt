package com.example.steeringwheel.presentation.state

import com.example.steeringwheel.domain.model.LocationData

data class SpeedUiState(
    val locationData: LocationData = LocationData(),
    val threshold: Int = 10,
    val isSaving: Boolean = false,
    val lastSentSpeed: Int? = null,
    val isMqttConnected: Boolean = false
)
