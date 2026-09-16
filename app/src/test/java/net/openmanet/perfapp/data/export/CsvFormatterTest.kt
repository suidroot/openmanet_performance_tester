package net.openmanet.perfapp.data.export

import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.GpsSource
import net.openmanet.perfapp.data.entities.IperfResult
import net.openmanet.perfapp.data.entities.PingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvFormatterTest {

    @Test
    fun pingResultsCsv_emptyList_isHeaderOnly() {
        val csv = CsvFormatter.pingResultsCsv(emptyList())

        val lines = csv.split("\r\n").filter { it.isNotEmpty() }
        assertEquals(1, lines.size)
        assertEquals("id,sessionId,timestampMs,targetHost,targetLabel,rttMs,success,rawOutputLine", lines[0])
    }

    @Test
    fun pingResultsCsv_rowCountMatchesInputRowCount() {
        val rows = listOf(
            pingResult(id = 1, host = "10.41.1.1"),
            pingResult(id = 2, host = "10.41.1.2"),
        )

        val csv = CsvFormatter.pingResultsCsv(rows)

        val lines = csv.split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, lines.size) // header + 2 rows
    }

    @Test
    fun pingResultsCsv_nullFields_renderAsEmptyNotLiteralNull() {
        val rows = listOf(pingResult(id = 1, host = "10.41.1.1", rttMs = null, rawOutputLine = null))

        val csv = CsvFormatter.pingResultsCsv(rows)

        val dataLine = csv.split("\r\n")[1]
        assertEquals("1,session-1,1000,10.41.1.1,,,true,", dataLine)
    }

    @Test
    fun pingResultsCsv_fieldContainingComma_isQuoted() {
        val rows = listOf(pingResult(id = 1, host = "10.41.1.1", rawOutputLine = "64 bytes, time=12ms"))

        val csv = CsvFormatter.pingResultsCsv(rows)

        val dataLine = csv.split("\r\n")[1]
        assertTrue(dataLine.endsWith("\"64 bytes, time=12ms\""))
    }

    @Test
    fun pingResultsCsv_fieldContainingQuote_isEscapedByDoubling() {
        val rows = listOf(pingResult(id = 1, host = "10.41.1.1", rawOutputLine = """say "hi""""))

        val csv = CsvFormatter.pingResultsCsv(rows)

        val dataLine = csv.split("\r\n")[1]
        assertTrue(dataLine.endsWith("\"say \"\"hi\"\"\""))
    }

    @Test
    fun pingResultsCsv_fieldContainingNewline_isQuoted() {
        val rows = listOf(pingResult(id = 1, host = "10.41.1.1", rawOutputLine = "line1\nline2"))

        val csv = CsvFormatter.pingResultsCsv(rows)

        // The embedded newline must not create an extra CSV row.
        val lines = csv.split("\r\n").filter { it.isNotEmpty() }
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"line1\nline2\""))
    }

    @Test
    fun gpsFixesCsv_sourceEnum_rendersAsName() {
        val fix = GpsFix(
            id = 1,
            sessionId = "session-1",
            timestampMs = 1000L,
            source = GpsSource.COT,
            sourceId = "unit-1",
            lat = 1.0,
            lon = 2.0,
            altitudeM = null,
            speedMps = null,
            courseDeg = null,
            fixQuality = null,
            rawPayload = null,
        )

        val csv = CsvFormatter.gpsFixesCsv(listOf(fix))

        assertTrue(csv.contains(",COT,"))
    }

    @Test
    fun iperfResultsCsv_booleanIsSummary_rendersAsTrueFalse() {
        val result = IperfResult(
            id = 1,
            sessionId = "session-1",
            timestampMs = 1000L,
            testRunId = "run-1",
            isSummary = true,
            targetHost = "10.41.1.2",
            targetPort = 5201,
            protocol = "TCP",
            direction = "SEND",
            intervalStartSec = 0.0,
            intervalEndSec = 10.0,
            bytesTransferred = 112_000_000L,
            bitsPerSecond = 94_000_000.0,
            jitterMs = null,
            lostPackets = null,
            retransmits = 15,
        )

        val csv = CsvFormatter.iperfResultsCsv(listOf(result))

        val dataLine = csv.split("\r\n")[1]
        assertTrue(dataLine.contains(",true,"))
    }

    private fun pingResult(
        id: Long,
        host: String,
        rttMs: Double? = 12.5,
        rawOutputLine: String? = null,
    ) = PingResult(
        id = id,
        sessionId = "session-1",
        timestampMs = 1000L,
        targetHost = host,
        targetLabel = null,
        rttMs = rttMs,
        success = true,
        rawOutputLine = rawOutputLine,
    )
}
