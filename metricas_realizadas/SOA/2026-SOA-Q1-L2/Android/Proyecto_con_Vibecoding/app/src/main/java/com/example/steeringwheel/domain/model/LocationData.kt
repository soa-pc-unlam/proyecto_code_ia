package com.example.steeringwheel.domain.model

data class LocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedKmh: Int = 0,
    val accuracy: Float = 0f,
    val status: GpsStatus = GpsStatus.SEARCHING
)

enum class GpsStatus {
    SEARCHING,
    ACTIVE,
    PERMISSION_DENIED,
    ERROR
}
