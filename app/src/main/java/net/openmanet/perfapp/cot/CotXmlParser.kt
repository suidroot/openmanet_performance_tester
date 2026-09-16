package net.openmanet.perfapp.cot

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses Cursor-on-Target XML event stanzas broadcast on the SA multicast group (239.2.3.1:6969
 * - see https://openmanet.github.io/docs/gnss). Multicast is lossy and CoT senders are other
 * devices this app doesn't control, so every failure mode here returns null rather than
 * throwing - CotMulticastListener's receive loop must keep running across malformed/partial
 * datagrams.
 *
 * Uses the JDK's built-in DOM parser (no extra dependency) rather than a streaming/pull parser
 * since CoT stanzas are small (well under a UDP datagram's practical size).
 */
object CotXmlParser {
    private val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        // XXE hardening: CoT payloads come from the network, never trust embedded DOCTYPEs.
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        isExpandEntityReferences = false
    }

    fun parse(xml: String): CotEvent? {
        return try {
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val eventEl = doc.documentElement ?: return null
            if (eventEl.tagName != "event") return null

            val uid = eventEl.getAttribute("uid")
            if (uid.isBlank()) return null

            val pointEl = eventEl.getElementsByTagName("point").item(0) as? Element ?: return null
            val lat = pointEl.getAttribute("lat").toDoubleOrNull() ?: return null
            val lon = pointEl.getAttribute("lon").toDoubleOrNull() ?: return null
            val hae = pointEl.getAttribute("hae").toDoubleOrNull()

            var speed: Double? = null
            var course: Double? = null
            val trackEl = eventEl.getElementsByTagName("track").item(0) as? Element
            if (trackEl != null) {
                speed = trackEl.getAttribute("speed").toDoubleOrNull()
                course = trackEl.getAttribute("course").toDoubleOrNull()
            }

            val timeMs = eventEl.getAttribute("time").takeIf { it.isNotBlank() }?.let { parseIsoInstant(it) }

            CotEvent(uid = uid, lat = lat, lon = lon, hae = hae, speed = speed, course = course, timeMs = timeMs)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseIsoInstant(value: String): Long? = try {
        Instant.parse(value).toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}
