package net.openmanet.perfapp.ping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PingOutputParserTest {

    @Test
    fun toyboxSuccess_parsesRtt() {
        val output = """
            PING 10.41.1.2 (10.41.1.2) 56(84) bytes of data.
            64 bytes from 10.41.1.2: icmp_seq=1 ttl=64 time=12.3 ms

            --- 10.41.1.2 ping statistics ---
            1 packets transmitted, 1 received, 0% packet loss, time 0ms
            rtt min/avg/max/mdev = 12.300/12.300/12.300/0.000 ms
        """.trimIndent()

        val result = PingOutputParser.parse("session-1", "10.41.1.2", 1_000L, output)

        assertTrue(result.success)
        assertEquals(12.3, result.rttMs!!, 0.001)
        assertEquals("10.41.1.2", result.targetHost)
    }

    @Test
    fun subMillisecondRtt_parsesWithoutDecimal() {
        val output = "64 bytes from 127.0.0.1: icmp_seq=1 ttl=64 time<1ms"

        val result = PingOutputParser.parse("session-1", "127.0.0.1", 1_000L, output)

        assertTrue(result.success)
        assertEquals(1.0, result.rttMs!!, 0.001)
    }

    @Test
    fun timeout_100PercentLoss_isFailureNotCrash() {
        val output = """
            PING 10.41.1.99 (10.41.1.99) 56(84) bytes of data.

            --- 10.41.1.99 ping statistics ---
            1 packets transmitted, 0 received, 100% packet loss, time 0ms
        """.trimIndent()

        val result = PingOutputParser.parse("session-1", "10.41.1.99", 1_000L, output)

        assertFalse(result.success)
        assertNull(result.rttMs)
    }

    @Test
    fun destinationUnreachable_isFailure() {
        val output = """
            PING 10.41.1.5 (10.41.1.5) 56(84) bytes of data.
            From 10.41.1.1 icmp_seq=1 Destination Host Unreachable

            --- 10.41.1.5 ping statistics ---
            1 packets transmitted, 0 received, +1 errors, 100% packet loss, time 0ms
        """.trimIndent()

        val result = PingOutputParser.parse("session-1", "10.41.1.5", 1_000L, output)

        assertFalse(result.success)
        assertNull(result.rttMs)
    }

    @Test
    fun emptyOutput_isFailureNotCrash() {
        val result = PingOutputParser.parse("session-1", "10.41.1.2", 1_000L, "")

        assertFalse(result.success)
        assertNull(result.rttMs)
    }

    @Test
    fun garbageOutput_isFailureNotCrash() {
        val result = PingOutputParser.parse(
            "session-1",
            "10.41.1.2",
            1_000L,
            "sh: /system/bin/ping: not found\n",
        )

        assertFalse(result.success)
        assertNull(result.rttMs)
    }

    @Test
    fun rawOutputLine_isTruncatedAndPreserved() {
        val longOutput = "x".repeat(1000) + " time=5.0 ms"

        val result = PingOutputParser.parse("session-1", "10.41.1.2", 1_000L, longOutput)

        assertTrue(result.success)
        assertTrue(result.rawOutputLine!!.length <= 500)
    }
}
