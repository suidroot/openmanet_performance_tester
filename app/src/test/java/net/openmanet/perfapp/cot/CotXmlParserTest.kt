package net.openmanet.perfapp.cot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * These run against the desktop JVM's own DocumentBuilderFactory (Xerces-based) - including
 * under Robolectric, which was tried and confirmed to still resolve to the desktop JVM's parser
 * here, not a shadow of Android's real org.apache.harmony one. That gap is exactly how a real
 * on-device bug went undetected: CotXmlParser's factory init called
 * setFeature("...disallow-doctype-decl", true), which the desktop parser accepts but Android's
 * built-in parser rejects with ParserConfigurationException - crashing the whole app
 * (ExceptionInInitializerError from this object's <clinit>) the moment the first real CoT packet
 * was ever parsed. No JVM-side test can catch this class of divergence; it's why
 * CLAUDE.md/Verification calls out on-device testing as required, not optional, for this code.
 */
class CotXmlParserTest {

    @Test
    fun validEvent_withTrackAndTime_parsesAllFields() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <event version="2.0" uid="ANDROID-abc123" type="a-f-G-U-C"
                   time="2026-01-15T12:00:00Z" start="2026-01-15T12:00:00Z" stale="2026-01-15T12:05:00Z"
                   how="h-g-i-g-o">
              <point lat="37.7749" lon="-122.4194" hae="15.5" ce="9999999.0" le="9999999.0"/>
              <detail>
                <track speed="1.2" course="90.5"/>
                <contact callsign="EUD-1"/>
              </detail>
            </event>
        """.trimIndent()

        val event = CotXmlParser.parse(xml)

        checkNotNull(event)
        assertEquals("ANDROID-abc123", event.uid)
        assertEquals(37.7749, event.lat, 0.0001)
        assertEquals(-122.4194, event.lon, 0.0001)
        assertEquals(15.5, event.hae!!, 0.0001)
        assertEquals(1.2, event.speed!!, 0.0001)
        assertEquals(90.5, event.course!!, 0.0001)
        assertEquals(1768478400000L, event.timeMs)
    }

    @Test
    fun minimalEvent_withoutTrackOrTime_parsesPositionOnly() {
        val xml = """<event uid="unit-1"><point lat="1.0" lon="2.0"/></event>"""

        val event = CotXmlParser.parse(xml)

        checkNotNull(event)
        assertEquals("unit-1", event.uid)
        assertEquals(1.0, event.lat, 0.0001)
        assertEquals(2.0, event.lon, 0.0001)
        assertNull(event.hae)
        assertNull(event.speed)
        assertNull(event.course)
        assertNull(event.timeMs)
    }

    @Test
    fun missingUid_returnsNull() {
        val xml = """<event><point lat="1.0" lon="2.0"/></event>"""
        assertNull(CotXmlParser.parse(xml))
    }

    @Test
    fun missingPoint_returnsNull() {
        val xml = """<event uid="unit-1"></event>"""
        assertNull(CotXmlParser.parse(xml))
    }

    @Test
    fun missingLatLon_returnsNull() {
        val xml = """<event uid="unit-1"><point hae="10"/></event>"""
        assertNull(CotXmlParser.parse(xml))
    }

    @Test
    fun wrongRootElement_returnsNull() {
        val xml = """<notAnEvent uid="unit-1"><point lat="1.0" lon="2.0"/></notAnEvent>"""
        assertNull(CotXmlParser.parse(xml))
    }

    @Test
    fun malformedXml_returnsNullNotThrows() {
        assertNull(CotXmlParser.parse("<event uid=\"unit-1\"><point lat=\"1.0\" lon=\"2.0\""))
    }

    @Test
    fun emptyPayload_returnsNullNotThrows() {
        assertNull(CotXmlParser.parse(""))
    }

    @Test
    fun truncatedDatagram_returnsNullNotThrows() {
        // Simulates a UDP packet cut off mid-stanza - a real failure mode on a lossy multicast link.
        val xml = """<event uid="unit-1"><point lat="37.77"""
        assertNull(CotXmlParser.parse(xml))
    }

    @Test
    fun invalidTimeAttribute_parsesPositionWithNullTime() {
        val xml = """<event uid="unit-1" time="not-a-timestamp"><point lat="1.0" lon="2.0"/></event>"""

        val event = CotXmlParser.parse(xml)

        checkNotNull(event)
        assertNull(event.timeMs)
    }
}
