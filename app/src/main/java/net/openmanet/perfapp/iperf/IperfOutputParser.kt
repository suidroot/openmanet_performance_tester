package net.openmanet.perfapp.iperf

import net.openmanet.perfapp.data.entities.IperfResult

/**
 * Parses `iperf3 -i 1 -f m` client output. `-f m` forces every transfer/bitrate figure onto a
 * fixed MBytes/Mbits-sec scale (1000-based, per iperf3's own unit_snprintf convention) so every
 * line uses the same literal units - the parser never has to detect K/M/G, which is exactly why
 * IperfProcessRunner always passes that flag.
 *
 * A summary row (the final "sec  ...  sender"/"receiver" lines after the "- - -" divider) is
 * the only place a trailing "sender"/"receiver" label appears, so that's the isSummary signal.
 * Header/connection-status lines and the divider itself match neither regex and are skipped
 * (return null), same pattern as PingOutputParser/CotXmlParser.
 */
object IperfOutputParser {
    private const val MEGA = 1_000_000.0

    private val TCP_SUMMARY = Regex(
        """^\[\s*\d+]\s+([\d.]+)-([\d.]+)\s+sec\s+([\d.]+)\s+MBytes\s+([\d.]+)\s+Mbits/sec""" +
            """(?:\s+(\d+))?\s+(sender|receiver)\s*$""",
    )
    private val TCP_INTERVAL = Regex(
        """^\[\s*\d+]\s+([\d.]+)-([\d.]+)\s+sec\s+([\d.]+)\s+MBytes\s+([\d.]+)\s+Mbits/sec\s*$""",
    )
    private val UDP_SUMMARY = Regex(
        """^\[\s*\d+]\s+([\d.]+)-([\d.]+)\s+sec\s+([\d.]+)\s+MBytes\s+([\d.]+)\s+Mbits/sec""" +
            """\s+([\d.]+)\s+ms\s+(\d+)/(\d+)\s+\([\d.]+%\)\s+(sender|receiver)\s*$""",
    )
    private val UDP_INTERVAL = Regex(
        """^\[\s*\d+]\s+([\d.]+)-([\d.]+)\s+sec\s+([\d.]+)\s+MBytes\s+([\d.]+)\s+Mbits/sec\s+(\d+)\s*$""",
    )

    fun parseLine(
        config: IperfConfig,
        sessionId: String,
        testRunId: String,
        timestampMs: Long,
        rawLine: String,
    ): IperfResult? {
        val line = rawLine.trim()
        val direction = if (config.reverse) "RECEIVE" else "SEND"

        return if (config.protocol == IperfProtocol.UDP) {
            parseUdp(line, config, sessionId, testRunId, direction, timestampMs)
        } else {
            parseTcp(line, config, sessionId, testRunId, direction, timestampMs)
        }
    }

    private fun parseTcp(
        line: String,
        config: IperfConfig,
        sessionId: String,
        testRunId: String,
        direction: String,
        timestampMs: Long,
    ): IperfResult? {
        TCP_SUMMARY.find(line)?.let { m ->
            val g = m.groupValues
            return IperfResult(
                sessionId = sessionId,
                timestampMs = timestampMs,
                testRunId = testRunId,
                isSummary = true,
                targetHost = config.host,
                targetPort = config.port,
                protocol = "TCP",
                direction = direction,
                intervalStartSec = g[1].toDouble(),
                intervalEndSec = g[2].toDouble(),
                bytesTransferred = (g[3].toDouble() * MEGA).toLong(),
                bitsPerSecond = g[4].toDouble() * MEGA,
                jitterMs = null,
                lostPackets = null,
                retransmits = g[5].toIntOrNull(),
            )
        }
        TCP_INTERVAL.find(line)?.let { m ->
            val g = m.groupValues
            return IperfResult(
                sessionId = sessionId,
                timestampMs = timestampMs,
                testRunId = testRunId,
                isSummary = false,
                targetHost = config.host,
                targetPort = config.port,
                protocol = "TCP",
                direction = direction,
                intervalStartSec = g[1].toDouble(),
                intervalEndSec = g[2].toDouble(),
                bytesTransferred = (g[3].toDouble() * MEGA).toLong(),
                bitsPerSecond = g[4].toDouble() * MEGA,
                jitterMs = null,
                lostPackets = null,
                retransmits = null,
            )
        }
        return null
    }

    private fun parseUdp(
        line: String,
        config: IperfConfig,
        sessionId: String,
        testRunId: String,
        direction: String,
        timestampMs: Long,
    ): IperfResult? {
        UDP_SUMMARY.find(line)?.let { m ->
            val g = m.groupValues
            return IperfResult(
                sessionId = sessionId,
                timestampMs = timestampMs,
                testRunId = testRunId,
                isSummary = true,
                targetHost = config.host,
                targetPort = config.port,
                protocol = "UDP",
                direction = direction,
                intervalStartSec = g[1].toDouble(),
                intervalEndSec = g[2].toDouble(),
                bytesTransferred = (g[3].toDouble() * MEGA).toLong(),
                bitsPerSecond = g[4].toDouble() * MEGA,
                jitterMs = g[5].toDouble(),
                lostPackets = g[6].toIntOrNull(),
                retransmits = null,
            )
        }
        UDP_INTERVAL.find(line)?.let { m ->
            val g = m.groupValues
            return IperfResult(
                sessionId = sessionId,
                timestampMs = timestampMs,
                testRunId = testRunId,
                isSummary = false,
                targetHost = config.host,
                targetPort = config.port,
                protocol = "UDP",
                direction = direction,
                intervalStartSec = g[1].toDouble(),
                intervalEndSec = g[2].toDouble(),
                bytesTransferred = (g[3].toDouble() * MEGA).toLong(),
                bitsPerSecond = g[4].toDouble() * MEGA,
                jitterMs = null,
                lostPackets = null,
                retransmits = null,
            )
        }
        return null
    }
}
