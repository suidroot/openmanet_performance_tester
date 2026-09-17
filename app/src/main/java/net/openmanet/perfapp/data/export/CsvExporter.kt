package net.openmanet.perfapp.data.export

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openmanet.perfapp.data.dao.GpsFixDao
import net.openmanet.perfapp.data.dao.IperfResultDao
import net.openmanet.perfapp.data.dao.NeighborSnapshotDao
import net.openmanet.perfapp.data.dao.PingResultDao
import net.openmanet.perfapp.data.entities.GpsSource
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a session's single combined "session_log.csv" into app-scoped external storage
 * (Context.getExternalFilesDir - no WRITE_EXTERNAL_STORAGE permission needed on API 29+ scoped
 * storage): one row per ping sample and one row per iperf3 interval sample, interleaved
 * chronologically, each carrying the nearest device-GPS position and neighbor signal/throughput
 * snapshot for that device - see CsvFormatter.sessionLogCsv for the exact format (matches a
 * field-operator-supplied example). Filtering by session covers the "time-series export"
 * requirement without a separate time-range UI: a session's own start/end already bounds it.
 */
@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pingResultDao: PingResultDao,
    private val gpsFixDao: GpsFixDao,
    private val neighborSnapshotDao: NeighborSnapshotDao,
    private val iperfResultDao: IperfResultDao,
) {
    suspend fun exportSession(sessionId: String): List<File> {
        val dir = File(context.getExternalFilesDir(null), "exports/$sessionId").apply { mkdirs() }

        val pingRows = pingResultDao.getInRange(sessionId, 0L, Long.MAX_VALUE)
        val iperfRows = iperfResultDao.getInRange(sessionId, 0L, Long.MAX_VALUE)
        if (pingRows.isEmpty() && iperfRows.isEmpty()) return emptyList()

        val deviceFixes = gpsFixDao.getInRange(sessionId, 0L, Long.MAX_VALUE).filter { it.source == GpsSource.DEVICE }
        val neighborSnapshots = neighborSnapshotDao.getInRange(sessionId, 0L, Long.MAX_VALUE)

        val csv = CsvFormatter.sessionLogCsv(pingRows, iperfRows, deviceFixes, neighborSnapshots)
        return listOf(writeCsv(dir, "session_log.csv", csv))
    }

    private fun writeCsv(dir: File, fileName: String, content: String): File =
        File(dir, fileName).apply { writeText(content) }
}
