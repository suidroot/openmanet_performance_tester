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

    // --- IperfEngine.V2 (`-y C` CSV output) - lines captured verbatim from a real local
    // iperf 2.2.1 client/server run, not hand-constructed, since the CSV column layout isn't
    // otherwise documented anywhere in this codebase.

    private val v2TcpConfig = IperfConfig(host = "10.41.1.2", protocol = IperfProtocol.TCP, engine = IperfEngine.V2)
    private val v2UdpConfig = IperfConfig(host = "10.41.1.2", protocol = IperfProtocol.UDP, engine = IperfEngine.V2)

    @Test
    fun v2TcpIntervalLine_parsesAsNonSummary() {
        val line = "20260916230043,127.0.0.1,49252,127.0.0.1,15201,1,0.0-1.0,18433835072,147470680576"

        val result = IperfOutputParser.parseLine(v2TcpConfig, "session-1", "run-1", 1_000L, line)

        checkNotNull(result)
        assertFalse(result.isSummary)
        assertEquals("TCP", result.protocol)
        assertEquals(0.0, result.intervalStartSec!!, 0.001)
        assertEquals(1.0, result.intervalEndSec!!, 0.001)
        assertEquals(18_433_835_072L, result.bytesTransferred)
        assertEquals(147_470_680_576.0, result.bitsPerSecond!!, 0.001)
        assertNull(result.jitterMs)
        assertNull(result.lostPackets)
    }

    @Test
    fun v2TcpSummaryLine_spanningWholeTest_isSummary() {
        val line = "20260916230044,127.0.0.1,49252,127.0.0.1,15201,1,0.0-3.0,57402327104,152772978919"

        val result = IperfOutputParser.parseLine(v2TcpConfig, "session-1", "run-1", 1_000L, line)

        checkNotNull(result)
        assertTrue(result.isSummary)
        assertEquals(57_402_327_104L, result.bytesTransferred)
    }

    @Test
    fun v2UdpIntervalLine_parsesJitterAndLoss() {
        val line = "20260916230119,127.0.0.1,58499,127.0.0.1,15201,1,0.0-1.0,655620,5244960,0.000,0,446,0.000,0"

        val result = IperfOutputParser.parseLine(v2UdpConfig, "session-1", "run-1", 1_000L, line)

        checkNotNull(result)
        assertFalse(result.isSummary)
        assertEquals("UDP", result.protocol)
        assertEquals(655_620L, result.bytesTransferred)
        assertEquals(0.0, result.jitterMs!!, 0.001)
        assertEquals(0, result.lostPackets)
    }

    @Test
    fun v2UdpSummaryLine_nanJitterAndNegativeSentinels_areNulledNotThrown() {
        // A real quirk seen locally: the client-side UDP summary can report jitter as the
        // literal (lowercase) text "nan" and a sentinel of -1/-0.000 for fields the server-side
        // "UDP fin" report didn't arrive in time to fill - none of that should crash the parser
        // or produce a bogus negative/NaN value in the stored result.
        val line = "20260916230119,127.0.0.1,58499,127.0.0.1,15201,1,0.0-2.0,1314180,5247864,nan,0,-1,-0.000,0"

        val result = IperfOutputParser.parseLine(v2UdpConfig, "session-1", "run-1", 1_000L, line)

        checkNotNull(result)
        assertTrue(result.isSummary)
        assertNull(result.jitterMs)
    }

    @Test
    fun v2ReverseDirection_isTaggedReceive() {
        val config = v2TcpConfig.copy(reverse = true)
        val line = "20260916230124,127.0.0.1,15201,127.0.0.1,49312,1,0.0-1.0,17833787392,142670299136"

        val result = IperfOutputParser.parseLine(config, "session-1", "run-1", 1_000L, line)

        checkNotNull(result)
        assertEquals("RECEIVE", result.direction)
    }

    @Test
    fun v2ServerBanner_isNotParsedNotThrown() {
        val line = "Running Iperf Server as a daemon"
        assertNull(IperfOutputParser.parseLine(v2TcpConfig, "session-1", "run-1", 1_000L, line))
    }

    @Test
    fun v2ConnectionRefusedError_isNotParsedNotThrown() {
        val line = "[  1] tcp connect to 10.41.1.2 port 5001 failed (Connection refused)"
        assertNull(IperfOutputParser.parseLine(v2TcpConfig, "session-1", "run-1", 1_000L, line))
    }
}
