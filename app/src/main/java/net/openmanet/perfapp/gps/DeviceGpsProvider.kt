package net.openmanet.perfapp.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.GpsSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps FusedLocationProviderClient. Field EUDs (rugged/single-purpose devices) may lack Play
 * Services; a LocationManager fallback is deferred to a later phase since it's not needed for
 * this app's primary target devices, but the [GpsFix]-returning interface stays the same either
 * way so swapping the implementation won't touch callers.
 *
 * sessionId is left blank here - GpsRepository stamps it in when persisting, since this
 * component has no notion of test sessions.
 */
@Singleton
class DeviceGpsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("MissingPermission") // caller (TestSessionService) verifies the permission first
    fun locationUpdates(intervalMs: Long = 2_000L): Flow<GpsFix> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toGpsFix()) }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}

private fun Location.toGpsFix(): GpsFix = GpsFix(
    sessionId = "",
    timestampMs = time,
    source = GpsSource.DEVICE,
    sourceId = null,
    lat = latitude,
    lon = longitude,
    altitudeM = if (hasAltitude()) altitude else null,
    speedMps = if (hasSpeed()) speed.toDouble() else null,
    courseDeg = if (hasBearing()) bearing.toDouble() else null,
    fixQuality = null,
    rawPayload = null,
)
