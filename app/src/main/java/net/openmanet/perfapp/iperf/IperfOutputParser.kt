package net.openmanet.perfapp.iperf

import net.openmanet.perfapp.data.entities.IperfResult

/**
 * Parses client output from either vendored iperf binary - see IperfConfig.engine for why there
 * are two. Deliberately dispatches on `config.engine` rather than sniffing the line format,
 * since the two engines' output is unambiguous by construction (one is `-f m` text, the other is
 * `-y C` CSV) and sniffing could misparse a stray line from either as the other's format.
 */
object IperfOutputParser {
    fun parseLine(
        config: IperfConfig,
        sessionId: String,
        testRunId: String,
        timestampMs: Long,
        rawLine: String,
    ): IperfResult? {
        val line = rawLine.trim()
        return if (config.engine == IperfEngine.V2) {
            V2.parseLine(config, sessionId, testRunId, timestampMs, line)
        } else {
            V3.parseLine(config, sessionId, testRunId, timestampMs, line)
        }
    }

    /**
     * iperf3 `-i 1 -f m` text output. `-f m` forces every transfer/bitrate figure onto a fixed
     * MBytes/Mbits-sec scale (1000-based, per iperf3's own unit_snprintf convention) so every
     * line uses the same literal units - the parser never has to detect K/M/G, which is exactly
     * why IperfProcessRunner always passes that flag for this engine.
     *
     * A summary row (the final "sec  ...  sender"/"receiver" lines after the "- - -" divider) is
     * the only place a trailing "sender"/"receiver" label appears, so that's the isSummary
     * signal. Header/connection-status lines and the divider itself match neither regex and are
     * skipped (return null), same pattern as PingOutputParser/CotXmlParser.
     */
    private object V3 {
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
            line: String,
        ): IperfResult? {
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

    /**
     * iperf2 `-i 1 -y C` CSV output - one line per report, TCP has 9 comma-separated fields,
     * UDP has 14:
     *   timestamp,srcIp,srcPort,dstIp,dstPort,threadId,intervalRange,bytes,bitsPerSec
     *     [,jitterMs,lostPackets,totalPackets,lostPercent,outOfOrder]
     * Verified directly against a real local iperf 2.2.1 client/server pair - CSV mode reports
     * raw byte/bit counts regardless of any -f flag, unlike iperf3's human-readable output, so no
     * unit scaling is needed here. There's no explicit "this is the summary" marker the way
     * iperf3 has a trailing "sender"/"receiver" label; the summary row is distinguished by its
     * interval spanning the whole test (since IperfProcessRunner always requests -i 1, any row
     * whose interval is longer than ~1.5s is the final summary, not a per-second sample).
     * Non-CSV lines (banners, "iperf3: error: ..."-style connection failures, blank lines) simply
     * don't have enough comma-separated fields and are skipped, same defensive pattern as V3.
     */
    private object V2 {
        private const val SUMMARY_SPAN_THRESHOLD_SEC = 1.5

        fun parseLine(
            config: IperfConfig,
            sessionId: String,
            testRunId: String,
            timestampMs: Long,
            line: String,
        ): IperfResult? {
            val fields = line.split(",")
            if (fields.size < 9) return null

            val interval = fields[6].split("-")
            if (interval.size != 2) return null
            val intervalStart = interval[0].toDoubleOrNull() ?: return null
            val intervalEnd = interval[1].toDoubleOrNull() ?: return null
            val bytes = fields[7].toLongOrNull() ?: return null
            val bitsPerSecond = fields[8].toDoubleOrNull() ?: return null
            val isSummary = (intervalEnd - intervalStart) > SUMMARY_SPAN_THRESHOLD_SEC
            val direction = if (config.reverse) "RECEIVE" else "SEND"

            val isUdp = fields.size >= 14
            return IperfResult(
                sessionId = sessionId,
                timestampMs = timestampMs,
                testRunId = testRunId,
                isSummary = isSummary,
                targetHost = config.host,
                targetPort = config.port,
                protocol = if (isUdp) "UDP" else "TCP",
                direction = direction,
                intervalStartSec = intervalStart,
                intervalEndSec = intervalEnd,
                bytesTransferred = bytes,
                bitsPerSecond = bitsPerSecond,
                jitterMs = if (isUdp) fields[9].toDoubleOrNull() else null,
                lostPackets = if (isUdp) fields[10].toIntOrNull()?.takeIf { it >= 0 } else null,
                retransmits = null,
            )
        }
    }
}
