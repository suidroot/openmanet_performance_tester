package net.openmanet.perfapp.cot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NmeaParserTest {

    @Test
    fun ggaSentence_parsesPositionAndAltitude() {
        val sentence = "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"

        val fix = NmeaParser.parse(sentence)

        checkNotNull(fix)
        assertEquals(48.1173, fix.lat, 0.0001)
        assertEquals(11.5167, fix.lon, 0.0001)
        assertEquals(545.4, fix.altitudeM!!, 0.001)
        assertNull(fix.speedMps)
        assertNull(fix.courseDeg)
    }

    @Test
    fun ggaSentence_southAndWestHemispheres_areNegative() {
        val sentence = "\$GPGGA,123519,4807.038,S,01131.000,W,1,08,0.9,545.4,M,46.9,M,,*48"

        val fix = NmeaParser.parse(sentence)

        checkNotNull(fix)
        assertEquals(-48.1173, fix.lat, 0.0001)
        assertEquals(-11.5167, fix.lon, 0.0001)
    }

    @Test
    fun ggaSentence_invalidFixQuality_returnsNull() {
        val sentence = "\$GPGGA,123519,4807.038,N,01131.000,E,0,08,0.9,545.4,M,46.9,M,,*46"
        assertNull(NmeaParser.parse(sentence))
    }

    @Test
    fun ggaSentence_wrongChecksum_returnsNull() {
        // Same payload as ggaSentence_parsesPositionAndAltitude but with a corrupted lat digit
        // and the ORIGINAL (now-stale) checksum - simulates a bit flip on a lossy mesh RF link.
        val sentence = "\$GPGGA,123519,4907.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"
        assertNull(NmeaParser.parse(sentence))
    }

    @Test
    fun rmcSentence_parsesPositionSpeedAndCourse() {
        val sentence = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"

        val fix = NmeaParser.parse(sentence)

        checkNotNull(fix)
        assertEquals(48.1173, fix.lat, 0.0001)
        assertEquals(11.5167, fix.lon, 0.0001)
        assertEquals(022.4 * 0.514444, fix.speedMps!!, 0.001)
        assertEquals(84.4, fix.courseDeg!!, 0.001)
        assertNull(fix.altitudeM)
    }

    @Test
    fun rmcSentence_voidStatus_returnsNull() {
        val sentence = "\$GPRMC,123519,V,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*7D"
        assertNull(NmeaParser.parse(sentence))
    }

    @Test
    fun gnTalkerId_isParsedSameAsGp() {
        // Multi-constellation receivers report a "GN" talker id instead of "GP" - the sentence
        // type (GGA/RMC) is what matters, not the 2-letter talker prefix.
        val sentence = "\$GNGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*59"
        assertNotNullFix(NmeaParser.parse(sentence))
    }

    @Test
    fun unsupportedSentenceType_returnsNull() {
        assertNull(NmeaParser.parse("\$GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*39"))
    }

    @Test
    fun malformedSentence_returnsNullNotThrows() {
        assertNull(NmeaParser.parse("\$GPGGA,not,enough,fields"))
    }

    @Test
    fun emptyPayload_returnsNullNotThrows() {
        assertNull(NmeaParser.parse(""))
    }

    @Test
    fun nonNmeaPayload_returnsNullNotThrows() {
        assertNull(NmeaParser.parse("<event uid=\"unit-1\"><point lat=\"1.0\" lon=\"2.0\"/></event>"))
    }

    private fun assertNotNullFix(fix: NmeaFix?) = checkNotNull(fix)
}
