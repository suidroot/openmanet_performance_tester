package net.openmanet.perfapp.gps

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import net.openmanet.perfapp.cot.CotMulticastListener
import net.openmanet.perfapp.cot.CotXmlParser
import net.openmanet.perfapp.data.dao.GpsFixDao
import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.GpsSource
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GpsRepository"

/**
 * Merges the two GPS sources requested in the brief - the EUD's own device GPS and the mesh's
 * CoT multicast feed - into gps_fix, distinguished by [GpsSource]. Each source runs as its own
 * job so one failing (e.g. CoT listener can't bind because Wi-Fi isn't joined) doesn't take the
 * other down.
 */
@Singleton
class GpsRepository @Inject constructor(
    private val gpsFixDao: GpsFixDao,
    private val deviceGpsProvider: DeviceGpsProvider,
    private val cotMulticastListener: CotMulticastListener,
) {
    fun collectDeviceFixes(sessionId: String, scope: CoroutineScope): Job =
        scope.launch {
            deviceGpsProvider.locationUpdates()
                .catch { e -> Log.w(TAG, "Device GPS unavailable/disabled - CoT collection continues independently", e) }
                .collect { fix -> gpsFixDao.insert(fix.copy(sessionId = sessionId)) }
        }

    fun collectCotFixes(sessionId: String, scope: CoroutineScope): Job =
        scope.launch {
            cotMulticastListener.listen()
                .catch { e -> Log.w(TAG, "CoT multicast listener failed - see CotMulticastListener logs for why", e) }
                .collect { rawXml ->
                    val event = CotXmlParser.parse(rawXml) ?: return@collect
                    gpsFixDao.insert(
                        GpsFix(
                            sessionId = sessionId,
                            timestampMs = event.timeMs ?: System.currentTimeMillis(),
                            source = GpsSource.COT,
                            sourceId = event.uid,
                            lat = event.lat,
                            lon = event.lon,
                            altitudeM = event.hae,
                            speedMps = event.speed,
                            courseDeg = event.course,
                            fixQuality = null,
                            rawPayload = rawXml.take(2_000),
                        ),
                    )
                }
        }
}
