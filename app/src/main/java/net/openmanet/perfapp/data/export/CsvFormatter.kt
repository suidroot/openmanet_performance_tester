package net.openmanet.perfapp.data.export

import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.IperfResult
import net.openmanet.perfapp.data.entities.NeighborSnapshot
import net.openmanet.perfapp.data.entities.PingResult
import net.openmanet.perfapp.rpc.baseHostname
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure CSV formatting, kept separate from CsvExporter's file I/O so it's unit-testable without
 * Room/Android. RFC 4180-style escaping for any field that isn't guaranteed comma/quote/newline
 * free (device/host names are operator- or node-supplied text).
 */
object CsvFormatter {
    private const val NEWLINE = "\r\n"
    private val HEADER = listOf(
        "Date", "Lat", "Log", "mode", "Device Name", "Ping delay", "Throughput",
        "Signal (dbm)", "quality", "ip address", "iperf measurement",
    )

    /**
     * The session's single combined log: one "general" row per ping sample (ping delay + the
     * nearest neighbor signal/throughput snapshot for that device + the nearest device GPS fix)
     * and one "iperf" row per iperf3 interval sample (measured throughput instead of ping/
     * signal), interleaved chronologically. Matches a field-operator-supplied example format
     * directly rather than the app's own entity field names.
     *
     * `deviceGpsFixes` should already be filtered to GpsSource.DEVICE - this is "where was I",
     * not other units' CoT positions. An iperf row's `targetHost` is usually a bare IP (typed
     * into the iperf form or a saved profile), so it's resolved to the same device name a ping
     * row for that node would use via `pingRows`; unresolved iperf targets fall back to the host
     * string itself.
     */
    fun sessionLogCsv(
        pingRows: List<PingResult>,
        iperfRows: List<IperfResult>,
        deviceGpsFixes: List<GpsFix>,
        neighborSnapshots: List<NeighborSnapshot>,
    ): String {
        // UTC, not the device's local zone: the exported file may be read on a different
        // machine/timezone than the one that recorded it, and a fixed zone keeps timestamps
        // sortable/comparable across sessions regardless of where each was captured.
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val sortedFixes = deviceGpsFixes.sortedBy { it.timestampMs }
        val snapshotsByDevice = neighborSnapshots.groupBy { it.neighbor.baseHostname() }
            .mapValues { (_, snapshots) -> snapshots.sortedBy { it.timestampMs } }
        val deviceNameByIp = pingRows.associate { it.targetHost to (it.targetLabel ?: it.targetHost) }

        val generalRows = pingRows.map { r ->
            val deviceName = r.targetLabel ?: r.targetHost
            val fix = nearestByTimestamp(sortedFixes, r.timestampMs) { it.timestampMs }
            val snapshot = nearestByTimestamp(snapshotsByDevice[deviceName].orEmpty(), r.timestampMs) { it.timestampMs }
            r.timestampMs to listOf(
                dateFormat.format(Date(r.timestampMs)), fix?.lat, fix?.lon, "general", deviceName,
                r.rttMs, snapshot?.throughputBps?.toMbps(), snapshot?.signalStrength, snapshot?.signal,
                r.targetHost, null,
            )
        }
        val iperfLogRows = iperfRows.filter { !it.isSummary }.map { r ->
            val deviceName = deviceNameByIp[r.targetHost] ?: r.targetHost
            val fix = nearestByTimestamp(sortedFixes, r.timestampMs) { it.timestampMs }
            val snapshot = nearestByTimestamp(snapshotsByDevice[deviceName].orEmpty(), r.timestampMs) { it.timestampMs }
            r.timestampMs to listOf(
                dateFormat.format(Date(r.timestampMs)), fix?.lat, fix?.lon, "iperf", deviceName,
                null, snapshot?.throughputBps?.toMbps(), null, null,
                r.targetHost, r.bitsPerSecond?.let { it / 1_000_000.0 },
            )
        }

        val rows = (generalRows + iperfLogRows).sortedBy { it.first }.map { it.second }
        return toCsv(HEADER, rows)
    }

    private fun Int.toMbps(): Double = this / 1_000_000.0

    /** Binary search for the entry closest in time to `timestampMs`; null if `sorted` is empty. */
    private fun <T> nearestByTimestamp(sorted: List<T>, timestampMs: Long, tsOf: (T) -> Long): T? {
        if (sorted.isEmpty()) return null
        var lo = 0
        var hi = sorted.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (tsOf(sorted[mid]) < timestampMs) lo = mid + 1 else hi = mid
        }
        return listOfNotNull(sorted.getOrNull(lo - 1), sorted.getOrNull(lo))
            .minByOrNull { Math.abs(tsOf(it) - timestampMs) }
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
