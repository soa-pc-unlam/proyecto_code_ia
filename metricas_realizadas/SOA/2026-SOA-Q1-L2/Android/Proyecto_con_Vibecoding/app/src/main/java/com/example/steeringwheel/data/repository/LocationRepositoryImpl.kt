package com.example.steeringwheel.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.example.steeringwheel.domain.model.GpsStatus
import com.example.steeringwheel.domain.model.LocationData
import com.example.steeringwheel.domain.repository.LocationRepository
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationRepository {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var lastLocation: Location? = null

    @SuppressLint("MissingPermission")
    override fun getLocationUpdates(): Flow<LocationData> = callbackFlow {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val speedKmh = calculateSpeed(location)
                    trySend(
                        LocationData(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            speedKmh = if (speedKmh < 1) 0 else speedKmh,
                            accuracy = location.accuracy,
                            status = GpsStatus.ACTIVE
                        )
                    )
                    lastLocation = location
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                trySend(LocationData(status = GpsStatus.ERROR))
            }
        } catch (e: Exception) {
            trySend(LocationData(status = GpsStatus.ERROR))
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun calculateSpeed(currentLocation: Location): Int {
        if (currentLocation.hasSpeed()) {
            return (currentLocation.speed * 3.6f).roundToInt()
        }

        val prevLocation = lastLocation ?: return 0
        val distance = currentLocation.distanceTo(prevLocation)
        val timeElapsed = (currentLocation.time - prevLocation.time) / 1000.0

        if (timeElapsed <= 0) return 0
        
        val speedMs = distance / timeElapsed
        return (speedMs * 3.6).roundToInt()
    }
}
