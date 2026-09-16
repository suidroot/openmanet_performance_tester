package net.openmanet.perfapp.data.export

import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.IperfResult
import net.openmanet.perfapp.data.entities.NeighborSnapshot
import net.openmanet.perfapp.data.entities.PingResult

/**
 * Pure CSV formatting, kept separate from CsvExporter's file I/O so it's unit-testable without
 * Room/Android. One function per entity, each producing a full document (header + rows) with
 * RFC 4180-style escaping for any field that isn't guaranteed comma/quote/newline-free (targetHost
 * is operator-entered, rawOutputLine/rawPayload/rawJson are raw captured text).
 */
object CsvFormatter {
    private const val NEWLINE = "\r\n"

    fun pingResultsCsv(rows: List<PingResult>): String {
        val header = listOf(
            "id", "sessionId", "timestampMs", "targetHost", "targetLabel", "rttMs", "success", "rawOutputLine",
        )
        val lines = rows.map { r ->
            listOf(
                r.id, r.sessionId, r.timestampMs, r.targetHost, r.targetLabel, r.rttMs, r.success, r.rawOutputLine,
            )
        }
        return toCsv(header, lines)
    }

    fun gpsFixesCsv(rows: List<GpsFix>): String {
        val header = listOf(
            "id", "sessionId", "timestampMs", "source", "sourceId", "lat", "lon",
            "altitudeM", "speedMps", "courseDeg", "fixQuality", "rawPayload",
        )
        val lines = rows.map { r ->
            listOf(
                r.id, r.sessionId, r.timestampMs, r.source.name, r.sourceId, r.lat, r.lon,
                r.altitudeM, r.speedMps, r.courseDeg, r.fixQuality, r.rawPayload,
            )
        }
        return toCsv(header, lines)
    }

    fun neighborSnapshotsCsv(rows: List<NeighborSnapshot>): String {
        val header = listOf(
            "id", "sessionId", "timestampMs", "neighbor", "hardwareAddress", "signalStrength",
            "signal", "lastSeenMs", "throughputBps", "interfaceName",
        )
        val lines = rows.map { r ->
            listOf(
                r.id, r.sessionId, r.timestampMs, r.neighbor, r.hardwareAddress, r.signalStrength,
                r.signal, r.lastSeenMs, r.throughputBps, r.interfaceName,
            )
        }
        return toCsv(header, lines)
    }

    fun iperfResultsCsv(rows: List<IperfResult>): String {
        val header = listOf(
            "id", "sessionId", "timestampMs", "testRunId", "isSummary", "targetHost", "targetPort",
            "protocol", "direction", "intervalStartSec", "intervalEndSec", "bytesTransferred",
            "bitsPerSecond", "jitterMs", "lostPackets", "retransmits",
        )
        val lines = rows.map { r ->
            listOf(
                r.id, r.sessionId, r.timestampMs, r.testRunId, r.isSummary, r.targetHost, r.targetPort,
                r.protocol, r.direction, r.intervalStartSec, r.intervalEndSec, r.bytesTransferred,
                r.bitsPerSecond, r.jitterMs, r.lostPackets, r.retransmits,
            )
        }
        return toCsv(header, lines)
    }

    private fun toCsv(header: List<String>, rows: List<List<Any?>>): String {
        val builder = StringBuilder()
        builder.append(header.joinToString(",") { it.escape() }).append(NEWLINE)
        for (row in rows) {
            builder.append(row.joinToString(",") { it?.toString().escape() }).append(NEWLINE)
        }
        return builder.toString()
    }

    private fun String?.escape(): String {
        if (this == null) return ""
        return if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }
    }
}
