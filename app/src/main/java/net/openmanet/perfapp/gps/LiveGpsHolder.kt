package net.openmanet.perfapp.gps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.GpsSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Latest fix per [GpsSource], independent of whether a logging session is active - position
 * should be visible on the dashboard at all times, not only while the ping/GPS/CoT "logging"
 * toggle (TestSessionService) happens to be running. Written by GpsRepository's live collectors
 * (started by DashboardViewModel whenever the dashboard is visible, stopped when it isn't) as
 * well as by the session-persisting collectors TestSessionService runs, so the dashboard shows a
 * position update from either path. Keyed by source (rather than a single latest-overall value)
 * so DashboardViewModel can still prefer the user's chosen source and fall back to whichever
 * other source is actually available.
 */
@Singleton
class LiveGpsHolder @Inject constructor() {
    private val _fixesBySource = MutableStateFlow<Map<GpsSource, GpsFix>>(emptyMap())
    val fixesBySource: StateFlow<Map<GpsSource, GpsFix>> = _fixesBySource.asStateFlow()

    fun set(fix: GpsFix) {
        _fixesBySource.value = _fixesBySource.value + (fix.source to fix)
    }
}
