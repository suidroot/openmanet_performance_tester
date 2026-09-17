package net.openmanet.perfapp.cot

/** A decoded position from a single NMEA 0183 sentence - just the fields this app cares about. */
data class NmeaFix(
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val speedMps: Double?,
    val courseDeg: Double?,
    val fixQuality: String?,
)
