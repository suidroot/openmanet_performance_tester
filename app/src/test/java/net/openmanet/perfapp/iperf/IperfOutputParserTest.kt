package net.openmanet.perfapp.iperf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IperfOutputParserTest {

    private val tcpConfig = IperfConfig(host = "10.41.1.2", protocol = IperfProtocol.TCP)
    private val udpConfig = IperfConfig(host = "10.41.1.2", protocol = IperfProtocol.UDP)

    @Test
    fun tcpIntervalLine_parsesAsNonSummary() {
        val line = "[  5]   0.00-1.00   sec  11.2 MBytes  94.2 Mbits/sec"

        val result = IperfOutputParser.parseLine(tcpConfig, "session-1", "run-1", 1_000L, line)

        checkNotNull(result)
        assertFalse(result.isSummary)
        assertEquals(0.0, result.intervalStartSec!!, 0.001)
        assertEquals(1.0, result.intervalEndSec!!, 0.001)
        assertEquals(11_200_000L, result.bytesTransferred)
        assertEquals(94_200_000.0, result.bitsPerSecond!!, 0.001)
        assertNull(result.retransmits)
        assertEquals("TCP", result.protocol)
    }

    @Test
    fun tcpSenderSummaryLine_parsesRetransmits() {
        val line = "[  5]   0.00-10.00  sec   112 MBytes  94.0 Mbits/sec  15             sender"

        val result = IperfOutputParser.parseLine(tcpConfig, "session-1", "run-1", 1_000L, line)

        checkNotNull(result)
        assertTrue(result.isSummary)
        assertEquals(15, result.retransmits)
        assertEquals(112_000_000L, result.bytesTransferred)
    }

    @Test
    fun tcpReceiverSummaryLine_hasNoRetransmits() {
        val line = "[  5]   0.00-10.00  sec   111 MBytes  93.2 Mbits/sec                  receiver"

        val result = IperfOutputParser.parseLine(tcpConfig, "session-1", "run-1", 1_000L, line)

        checkNotNull(result)
        assertTrue(result.isSummary)
        assertNull(result.retransmits)
    }

    @Test
    fun udpIntervalLine_parsesWithoutJitter() {
        val line = "[  5]   0.00-1.00   sec  0.12 MBytes  1.05 Mbits/sec  91"

        val result = IperfOutputParser.parseLine(udpConfig, "session-1", "run-1", 1_000L, line)

        checkNotNull(result)
        assertFalse(result.isSummary)
        assertNull(result.jitterMs)
        assertNull(result.lostPackets)
    }

    @Test
    fun udpSummaryLine_parsesJitterAndLoss() {
        val line = "[  5]   0.00-10.00  sec  1.25 MBytes  1.05 Mbits/sec  0.045 ms  3/905 (0.33%)  sender"

        val result = IperfOutputParser.parseLine(udpConfig, "session-1", "run-1", 1_000L, line)

        checkNotNull(result)
        assertTrue(result.isSummary)
        assertEquals(0.045, result.jitterMs!!, 0.0001)
        assertEquals(3, result.lostPackets)
    }

    @Test
    fun connectingHeaderLine_isNotParsed() {
        val line = "Connecting to host 10.41.1.2, port 5201"
        assertNull(IperfOutputParser.parseLine(tcpConfig, "session-1", "run-1", 1_000L, line))
    }

    @Test
    fun columnHeaderLine_isNotParsed() {
        val line = "[ ID] Interval           Transfer     Bitrate         Retr"
        assertNull(IperfOutputParser.parseLine(tcpConfig, "session-1", "run-1", 1_000L, line))
    }

    @Test
    fun dividerLine_isNotParsed() {
        val line = "- - - - - - - - - - - - - - - - - - - - - - - - -"
        assertNull(IperfOutputParser.parseLine(tcpConfig, "session-1", "run-1", 1_000L, line))
    }

    @Test
    fun doneLine_isNotParsed() {
        assertNull(IperfOutputParser.parseLine(tcpConfig, "session-1", "run-1", 1_000L, "iperf Done."))
    }

    @Test
    fun emptyLine_isNotParsedNotThrows() {
        assertNull(IperfOutputParser.parseLine(tcpConfig, "session-1", "run-1", 1_000L, ""))
    }

    @Test
    fun connectionRefusedError_isNotParsedNotThrows() {
        val line = "iperf3: error - unable to connect to server: Connection refused"
        assertNull(IperfOutputParser.parseLine(tcpConfig, "session-1", "run-1", 1_000L, line))
    }

    @Test
    fun reverseDirection_isTaggedReceive() {
        val config = tcpConfig.copy(reverse = true)
        val line = "[  5]   0.00-1.00   sec  11.2 MBytes  94.2 Mbits/sec"

        val result = IperfOutputParser.parseLine(config, "session-1", "run-1", 1_000L, line)

        checkNotNull(result)
        assertEquals("RECEIVE", result.direction)
    }
}
