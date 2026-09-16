package net.openmanet.perfapp.data.export

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openmanet.perfapp.data.dao.GpsFixDao
import net.openmanet.perfapp.data.dao.IperfResultDao
import net.openmanet.perfapp.data.dao.NeighborSnapshotDao
import net.openmanet.perfapp.data.dao.PingResultDao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes one CSV file per non-empty entity type for a session into app-scoped external storage
 * (Context.getExternalFilesDir - no WRITE_EXTERNAL_STORAGE permission needed on API 29+ scoped
 * storage). Filtering by session covers the "time-series export" requirement without a separate
 * time-range UI: a session's own start/end already bounds it, and getInRange's (0, MAX_VALUE)
 * call here just reuses the same DAO query Phase 2's live screens use rather than adding
 * duplicate "getAllForSession" methods.
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
        val files = mutableListOf<File>()

        pingResultDao.getInRange(sessionId, 0L, Long.MAX_VALUE)
            .takeIf { it.isNotEmpty() }
            ?.let { files += writeCsv(dir, "ping_results.csv", CsvFormatter.pingResultsCsv(it)) }

        gpsFixDao.getInRange(sessionId, 0L, Long.MAX_VALUE)
            .takeIf { it.isNotEmpty() }
            ?.let { files += writeCsv(dir, "gps_fixes.csv", CsvFormatter.gpsFixesCsv(it)) }

        neighborSnapshotDao.getInRange(sessionId, 0L, Long.MAX_VALUE)
            .takeIf { it.isNotEmpty() }
            ?.let { files += writeCsv(dir, "neighbor_snapshots.csv", CsvFormatter.neighborSnapshotsCsv(it)) }

        iperfResultDao.getInRange(sessionId, 0L, Long.MAX_VALUE)
            .takeIf { it.isNotEmpty() }
            ?.let { files += writeCsv(dir, "iperf_results.csv", CsvFormatter.iperfResultsCsv(it)) }

        return files
    }

    private fun writeCsv(dir: File, fileName: String, content: String): File =
        File(dir, fileName).apply { writeText(content) }
}
