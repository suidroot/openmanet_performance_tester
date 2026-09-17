package net.openmanet.perfapp.data

import androidx.room.withTransaction
import net.openmanet.perfapp.data.dao.GpsFixDao
import net.openmanet.perfapp.data.dao.IperfResultDao
import net.openmanet.perfapp.data.dao.NeighborSnapshotDao
import net.openmanet.perfapp.data.dao.PingResultDao
import net.openmanet.perfapp.data.dao.TestSessionDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes all recorded test-session history - sessions plus every ping/GPS/neighbor/iperf reading
 * tied to one, i.e. everything session_log.csv could ever export - in one atomic transaction.
 * Deliberately leaves NodeProfile (saved node addresses) and IperfProfile (saved iperf presets)
 * alone: those are user-configured settings, not recorded data, and "clear session history"
 * shouldn't make the user re-enter them.
 */
@Singleton
class SessionHistoryRepository @Inject constructor(
    private val database: ManetDatabase,
    private val testSessionDao: TestSessionDao,
    private val pingResultDao: PingResultDao,
    private val gpsFixDao: GpsFixDao,
    private val neighborSnapshotDao: NeighborSnapshotDao,
    private val iperfResultDao: IperfResultDao,
) {
    suspend fun clearAll() {
        database.withTransaction {
            pingResultDao.deleteAll()
            gpsFixDao.deleteAll()
            neighborSnapshotDao.deleteAll()
            iperfResultDao.deleteAll()
            testSessionDao.deleteAll()
        }
    }
}
