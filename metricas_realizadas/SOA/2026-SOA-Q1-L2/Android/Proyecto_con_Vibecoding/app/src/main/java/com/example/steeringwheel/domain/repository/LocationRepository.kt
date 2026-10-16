package com.example.steeringwheel.domain.repository

import com.example.steeringwheel.domain.model.LocationData
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getLocationUpdates(): Flow<LocationData>
}
