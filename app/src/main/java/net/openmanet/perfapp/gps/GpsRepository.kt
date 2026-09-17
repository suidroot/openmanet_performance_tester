package net.openmanet.perfapp.gps

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import net.openmanet.perfapp.cot.CotMulticastListener
import net.openmanet.perfapp.cot.CotXmlParser
import net.openmanet.perfapp.cot.NmeaParser
import net.openmanet.perfapp.data.dao.GpsFixDao
import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.GpsSource
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GpsRepository"

/**
 * Merges the app's GPS sources into gps_fix, distinguished by [GpsSource]: the EUD's own device
 * GPS, other units' Cursor-on-Target broadcasts, and the mesh node's own GNSS receiver. The
 * latter two both arrive over the same SA multicast group (239.2.3.1) but on different ports and
 * in different formats - confirmed on a real deployment that openmanetd broadcasts its own GNSS
 * fix as raw NMEA on CotMulticastListener.NMEA_PORT, not CoT - so both ports are listened on (one
 * CotMulticastListener call covers both) and each payload is routed by its leading character
 * ('<' -> CoT XML, '$' -> NMEA) rather than assuming port implies format.
 *
 * Two independent sets of collectors:
 * - collectDeviceFixes/collectCotFixes (session-tagged, called by TestSessionService) persist
 *   every fix to Room under the given sessionId, surviving navigation/backgrounding via the
 *   foreground service - this is the exportable history.
 * - collectLiveDeviceFixes/collectLiveMeshFixes (called by DashboardViewModel whenever the
 *   dashboard is visible) never touch Room - they only update [LiveGpsHolder], so the dashboard
 *   shows a live position at all times, independent of whether a logging session happens to be
 *   running. Both sets can run concurrently without double-counting Room rows (only the
 *   session-tagged ones ever insert), at the cost of briefly joining the mesh multicast group
 *   twice while a session is active and the dashboard is open - a real but minor duplication,
 *   preferred over either regressing "logging survives backgrounding" or gating live display on
 *   logging being on.
 */
@Singleton
class GpsRepository @Inject constructor(
    private val gpsFixDao: GpsFixDao,
    private val deviceGpsProvider: DeviceGpsProvider,
    private val cotMulticastListener: CotMulticastListener,
    private val liveGpsHolder: LiveGpsHolder,
) {
    fun collectDeviceFixes(sessionId: String, scope: CoroutineScope): Job =
        scope.launch {
            deviceGpsProvider.locationUpdates()
                .catch { e -> Log.w(TAG, "Device GPS unavailable/disabled - mesh collection continues independently", e) }
                .collect { fix ->
                    liveGpsHolder.set(fix)
                    gpsFixDao.insert(fix.copy(sessionId = sessionId))
                }
        }

    fun collectCotFixes(sessionId: String, scope: CoroutineScope): Job =
        scope.launch {
            cotMulticastListener.listen(listOf(CotMulticastListener.COT_PORT, CotMulticastListener.NMEA_PORT))
                .catch { e -> Log.w(TAG, "Mesh multicast listener failed - see CotMulticastListener logs for why", e) }
                .collect { raw ->
                    parsePayload(raw)?.let { fix ->
                        liveGpsHolder.set(fix)
                        gpsFixDao.insert(fix.copy(sessionId = sessionId))
                    }
                }
        }

    /** Display-only device GPS collection - see class doc. Never persists to Room. */
    fun collectLiveDeviceFixes(scope: CoroutineScope): Job =
        scope.launch {
            deviceGpsProvider.locationUpdates()
                .catch { e -> Log.w(TAG, "Device GPS unavailable/disabled - mesh collection continues independently", e) }
                .collect { fix -> liveGpsHolder.set(fix) }
        }

    /** Display-only mesh multicast collection - see class doc. Never persists to Room. */
    fun collectLiveMeshFixes(scope: CoroutineScope): Job =
        scope.launch {
            cotMulticastListener.listen(listOf(CotMulticastListener.COT_PORT, CotMulticastListener.NMEA_PORT))
                .catch { e -> Log.w(TAG, "Mesh multicast listener failed - see CotMulticastListener logs for why", e) }
                .collect { raw -> parsePayload(raw)?.let { liveGpsHolder.set(it) } }
        }

    /** sessionId is left blank - both call sites above stamp in the real one (persisting) or
     * ignore it entirely (live-only). */
    private fun parsePayload(raw: String): GpsFix? {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("<") -> CotXmlParser.parse(trimmed)?.let { event ->
                GpsFix(
                    sessionId = "",
                    timestampMs = event.timeMs ?: System.currentTimeMillis(),
                    source = GpsSource.COT,
                    sourceId = event.uid,
                    lat = event.lat,
                    lon = event.lon,
                    altitudeM = event.hae,
                    speedMps = event.speed,
                    courseDeg = event.course,
                    fixQuality = null,
                    rawPayload = trimmed.take(2_000),
                )
            }
            trimmed.startsWith("$") -> NmeaParser.parse(trimmed)?.let { fix ->
                GpsFix(
                    sessionId = "",
                    timestampMs = System.currentTimeMillis(),
                    source = GpsSource.NODE_GNSS,
                    sourceId = null,
                    lat = fix.lat,
                    lon = fix.lon,
                    altitudeM = fix.altitudeM,
                    speedMps = fix.speedMps,
                    courseDeg = fix.courseDeg,
                    fixQuality = fix.fixQuality,
                    rawPayload = trimmed.take(2_000),
                )
            }
            else -> {
                Log.w(TAG, "Unrecognized multicast payload format, skipping: ${trimmed.take(40)}")
                null
            }
        }
    }
}
