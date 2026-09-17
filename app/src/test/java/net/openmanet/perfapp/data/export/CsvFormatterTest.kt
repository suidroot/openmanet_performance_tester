package net.openmanet.perfapp.data.export

import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.GpsSource
import net.openmanet.perfapp.data.entities.IperfResult
import net.openmanet.perfapp.data.entities.NeighborSnapshot
import net.openmanet.perfapp.data.entities.PingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvFormatterTest {

    companion object {
        /** 2026-09-16 10:00:00 UTC, matching the fixed-zone formatting in CsvFormatter. */
        private const val SEPT_16_2026_1000_UTC = 1_789_552_800_000L
    }

    @Test
    fun sessionLogCsv_emptyInput_isHeaderOnly() {
        val csv = CsvFormatter.sessionLogCsv(emptyList(), emptyList(), emptyList(), emptyList())

        val lines = csv.split("\r\n").filter { it.isNotEmpty() }
        assertEquals(1, lines.size)
        assertEquals(
            "Date,Lat,Log,mode,Device Name,Ping delay,Throughput,Signal (dbm),quality,ip address,iperf measurement",
            lines[0],
        )
    }

    @Test
    fun sessionLogCsv_pingRow_isGeneralModeWithNoIperfMeasurement() {
        val rows = listOf(pingResult(id = 1, host = "10.41.0.1", label = "man-gate", rttMs = 5.0))

        val csv = CsvFormatter.sessionLogCsv(rows, emptyList(), emptyList(), emptyList())

        val dataLine = csv.split("\r\n")[1]
        // Date,Lat,Log,mode,Device Name,Ping delay,Throughput,Signal (dbm),quality,ip address,iperf measurement
        assertEquals("2026-09-16 10:00:00,,,general,man-gate,5.0,,,,10.41.0.1,", dataLine)
    }

    @Test
    fun sessionLogCsv_iperfIntervalRow_isIperfModeWithNoPingOrSignal() {
        val rows = listOf(iperfResult(id = 1, host = "10.41.0.1", bitsPerSecond = 5_340_000.0))

        val csv = CsvFormatter.sessionLogCsv(emptyList(), rows, emptyList(), emptyList())

        val dataLine = csv.split("\r\n")[1]
        assertEquals("2026-09-16 10:00:00,,,iperf,10.41.0.1,,,,,10.41.0.1,5.34", dataLine)
    }

    @Test
    fun sessionLogCsv_iperfSummaryRow_isExcluded() {
        val rows = listOf(iperfResult(id = 1, host = "10.41.0.1", isSummary = true))

        val csv = CsvFormatter.sessionLogCsv(emptyList(), rows, emptyList(), emptyList())

        val lines = csv.split("\r\n").filter { it.isNotEmpty() }
        assertEquals(1, lines.size) // header only
    }

    @Test
    fun sessionLogCsv_iperfRow_resolvesDeviceNameFromPingRowsOnSameHost() {
        val pingRows = listOf(pingResult(id = 1, host = "10.41.0.1", label = "man-gate"))
        val iperfRows = listOf(iperfResult(id = 1, host = "10.41.0.1"))

        val csv = CsvFormatter.sessionLogCsv(pingRows, iperfRows, emptyList(), emptyList())

        val iperfLine = csv.split("\r\n").first { it.contains(",iperf,") }
        assertTrue(iperfLine.contains(",iperf,man-gate,"))
    }

    @Test
    fun sessionLogCsv_picksNearestDeviceGpsFixByTimestamp() {
        val rows = listOf(pingResult(id = 1, host = "10.41.0.1", label = "man-gate", timestampMs = 1_000L))
        val fixes = listOf(
            gpsFix(timestampMs = 500L, lat = 1.0, lon = 1.0),
            gpsFix(timestampMs = 990L, lat = -50.0, lon = 59.0),
            gpsFix(timestampMs = 2_000L, lat = 3.0, lon = 3.0),
        )

        val csv = CsvFormatter.sessionLogCsv(rows, emptyList(), fixes, emptyList())

        val dataLine = csv.split("\r\n")[1]
        assertTrue(dataLine.startsWith("1970-01-01 00:00:01,-50.0,59.0,general,"))
    }

    @Test
    fun sessionLogCsv_picksNearestNeighborSnapshotForSameDevice() {
        val rows = listOf(pingResult(id = 1, host = "10.41.0.1", label = "man-gate", timestampMs = 1_000L))
        val snapshots = listOf(
            neighborSnapshot(timestampMs = 990L, neighbor = "man-gate.wlan0", signalStrength = -50, signal = -60, throughputBps = 7_100_000),
            neighborSnapshot(timestampMs = 990L, neighbor = "unit02.wlan0", signalStrength = -10, signal = -10, throughputBps = 1),
        )

        val csv = CsvFormatter.sessionLogCsv(rows, emptyList(), emptyList(), snapshots)

        val dataLine = csv.split("\r\n")[1]
        assertEquals("1970-01-01 00:00:01,,,general,man-gate,5.0,7.1,-50,-60,10.41.0.1,", dataLine)
    }

    @Test
    fun sessionLogCsv_rowsAreSortedChronologicallyAcrossModes() {
        val pingRows = listOf(pingResult(id = 1, host = "10.41.0.1", label = "man-gate", timestampMs = 2_000L))
        val iperfRows = listOf(iperfResult(id = 1, host = "10.41.0.1", timestampMs = 1_000L))

        val csv = CsvFormatter.sessionLogCsv(pingRows, iperfRows, emptyList(), emptyList())

        val lines = csv.split("\r\n").filter { it.isNotEmpty() }
        assertTrue(lines[1].contains(",iperf,"))
        assertTrue(lines[2].contains(",general,"))
    }

    @Test
    fun sessionLogCsv_fieldContainingComma_isQuoted() {
        val rows = listOf(pingResult(id = 1, host = "10.41.0.1", label = "man-gate, backup"))

        val csv = CsvFormatter.sessionLogCsv(rows, emptyList(), emptyList(), emptyList())

        val dataLine = csv.split("\r\n")[1]
        assertTrue(dataLine.contains("\"man-gate, backup\""))
    }

    private fun pingResult(
        id: Long,
        host: String,
        label: String? = null,
        timestampMs: Long = SEPT_16_2026_1000_UTC,
        rttMs: Double? = 5.0,
    ) = PingResult(
        id = id,
        sessionId = "session-1",
        timestampMs = timestampMs,
        targetHost = host,
        targetLabel = label,
        rttMs = rttMs,
        success = true,
        rawOutputLine = null,
    )

    private fun iperfResult(
        id: Long,
        host: String,
        timestampMs: Long = SEPT_16_2026_1000_UTC,
        isSummary: Boolean = false,
        bitsPerSecond: Double? = 5_340_000.0,
    ) = IperfResult(
        id = id,
        sessionId = "session-1",
        timestampMs = timestampMs,
        testRunId = "run-1",
        isSummary = isSummary,
        targetHost = host,
        targetPort = 5201,
        protocol = "TCP",
        direction = "SEND",
        intervalStartSec = 0.0,
        intervalEndSec = 1.0,
        bytesTransferred = null,
        bitsPerSecond = bitsPerSecond,
        jitterMs = null,
        lostPackets = null,
        retransmits = null,
    )

    private fun gpsFix(timestampMs: Long, lat: Double, lon: Double) = GpsFix(
        id = 0,
        sessionId = "session-1",
        timestampMs = timestampMs,
        source = GpsSource.DEVICE,
        sourceId = null,
        lat = lat,
        lon = lon,
        altitudeM = null,
        speedMps = null,
        courseDeg = null,
        fixQuality = null,
        rawPayload = null,
    )

    private fun neighborSnapshot(
        timestampMs: Long,
        neighbor: String,
        signalStrength: Int,
        signal: Int,
        throughputBps: Int,
    ) = NeighborSnapshot(
        id = 0,
        sessionId = "session-1",
        timestampMs = timestampMs,
        neighbor = neighbor,
        hardwareAddress = "aa:bb:cc:dd:ee:ff",
        signalStrength = signalStrength,
        signal = signal,
        lastSeenMs = 0,
        throughputBps = throughputBps,
        interfaceName = "wlan0",
        txLinkRateJson = "{}",
        rxLinkRateJson = "{}",
        rawJson = "{}",
    )
}
