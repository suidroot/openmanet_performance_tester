package net.openmanet.perfapp.cot

/**
 * Parses NMEA 0183 position sentences ($--GGA, $--RMC). The two-letter talker id (GP, GN, GL,
 * GA, GB, ...) varies by receiver/constellation, so this matches on the 3-letter sentence type
 * suffix only, not the full 5-character header.
 *
 * Needed because a real OpenManet deployment's node-GNSS multicast feed turned out to carry raw
 * NMEA sentences from openmanetd, not Cursor-on-Target XML - the docs' "GNSS" framing (and this
 * app's original assumption) didn't match what's actually on the wire. See CotMulticastListener/
 * GpsRepository for how a raw payload is routed here vs. to CotXmlParser.
 *
 * Deliberately as defensive as CotXmlParser: NMEA over UDP is just as lossy as CoT, so any
 * malformed/truncated/short-field sentence returns null rather than throwing. Unlike CoT (which
 * has no checksum at all), NMEA's trailing `*XX` is a cheap, well-defined check, so it's verified
 * up front - a bit-flipped lat/lon digit from a lossy mesh RF link would otherwise still have the
 * right comma-delimited shape and parse into a plausible-looking but wrong fix with no way to
 * later tell it apart from a genuine one.
 */
object NmeaParser {
    fun parse(sentence: String): NmeaFix? {
        val trimmed = sentence.trim().removePrefix("$")
        if (!hasValidChecksum(trimmed)) return null
        val body = trimmed.substringBefore("*")
        val fields = body.split(",")
        val header = fields.getOrNull(0) ?: return null
        if (header.length != 5) return null
        return when (header.substring(2)) {
            "GGA" -> parseGga(fields)
            "RMC" -> parseRmc(fields)
            else -> null
        }
    }

    /** NMEA checksum: two-digit hex XOR of every character between `$` and `*`. */
    private fun hasValidChecksum(trimmed: String): Boolean {
        val starIndex = trimmed.indexOf('*')
        if (starIndex == -1 || starIndex + 3 > trimmed.length) return false
        val expected = trimmed.substring(starIndex + 1, starIndex + 3).toIntOrNull(16) ?: return false
        val actual = trimmed.take(starIndex).fold(0) { acc, c -> acc xor c.code }
        return actual == expected
    }

    /** $--GGA,time,lat,N/S,lon,E/W,fixQuality,numSats,hdop,altitude,M,geoidSep,M,age,stationId */
    private fun parseGga(fields: List<String>): NmeaFix? {
        if (fields.size < 10) return null
        val fixQuality = fields[6]
        if (fixQuality == "0" || fixQuality.isBlank()) return null // "0" = invalid/no fix
        val lat = parseLat(fields[2], fields[3]) ?: return null
        val lon = parseLon(fields[4], fields[5]) ?: return null
        return NmeaFix(
            lat = lat,
            lon = lon,
            altitudeM = fields[9].toDoubleOrNull(),
            speedMps = null,
            courseDeg = null,
            fixQuality = fixQuality,
        )
    }

    /** $--RMC,time,status(A/V),lat,N/S,lon,E/W,speedKnots,courseDeg,date,magvar,magvarDir,mode */
    private fun parseRmc(fields: List<String>): NmeaFix? {
        if (fields.size < 9) return null
        if (fields[2] != "A") return null // "V" = void/invalid, only "A" (active) is a real fix
        val lat = parseLat(fields[3], fields[4]) ?: return null
        val lon = parseLon(fields[5], fields[6]) ?: return null
        val speedKnots = fields[7].toDoubleOrNull()
        return NmeaFix(
            lat = lat,
            lon = lon,
            altitudeM = null,
            speedMps = speedKnots?.let { it * KNOTS_TO_MPS },
            courseDeg = fields[8].toDoubleOrNull(),
            fixQuality = null,
        )
    }

    /** ddmm.mmmm -> decimal degrees; the whole-degrees part is always exactly 2 digits for lat. */
    private fun parseLat(raw: String, hemisphere: String): Double? {
        if (raw.length < 3) return null
        val degrees = raw.take(2).toDoubleOrNull() ?: return null
        val minutes = raw.substring(2).toDoubleOrNull() ?: return null
        val value = degrees + minutes / 60.0
        return when (hemisphere) {
            "N" -> value
            "S" -> -value
            else -> null
        }
    }

    /** dddmm.mmmm -> decimal degrees; the whole-degrees part is always exactly 3 digits for lon. */
    private fun parseLon(raw: String, hemisphere: String): Double? {
        if (raw.length < 4) return null
        val degrees = raw.take(3).toDoubleOrNull() ?: return null
        val minutes = raw.substring(3).toDoubleOrNull() ?: return null
        val value = degrees + minutes / 60.0
        return when (hemisphere) {
            "E" -> value
            "W" -> -value
            else -> null
        }
    }

    private const val KNOTS_TO_MPS = 0.514444
}
