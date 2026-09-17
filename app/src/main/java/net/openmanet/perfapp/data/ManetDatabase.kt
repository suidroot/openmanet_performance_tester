package net.openmanet.perfapp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.openmanet.perfapp.data.dao.GpsFixDao
import net.openmanet.perfapp.data.dao.IperfProfileDao
import net.openmanet.perfapp.data.dao.IperfResultDao
import net.openmanet.perfapp.data.dao.NeighborSnapshotDao
import net.openmanet.perfapp.data.dao.NodeProfileDao
import net.openmanet.perfapp.data.dao.PingResultDao
import net.openmanet.perfapp.data.dao.TestSessionDao
import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.IperfProfile
import net.openmanet.perfapp.data.entities.IperfResult
import net.openmanet.perfapp.data.entities.NeighborSnapshot
import net.openmanet.perfapp.data.entities.NodeProfile
import net.openmanet.perfapp.data.entities.PingResult
import net.openmanet.perfapp.data.entities.TestSession

/**
 * Pre-1.0 schema: destructive migration is wired up in DatabaseModule so iteration doesn't
 * require hand-written Migrations yet - but fallbackToDestructiveMigration only fires on a
 * *version* change, not just an entity/column change, so every schema edit here (renamed/added/
 * removed columns or entities) must bump [version] too, or Room refuses to open an already-
 * installed app's existing database (IllegalStateException: identity hash mismatch) instead of
 * recreating it. Switch to real Migrations once the schema (especially NeighborSnapshot, once
 * real .proto fields are vendored in Phase 1) stabilizes.
 */
@Database(
    entities = [
        NodeProfile::class,
        TestSession::class,
        NeighborSnapshot::class,
        PingResult::class,
        GpsFix::class,
        IperfResult::class,
        IperfProfile::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ManetDatabase : RoomDatabase() {
    abstract fun nodeProfileDao(): NodeProfileDao
    abstract fun testSessionDao(): TestSessionDao
    abstract fun neighborSnapshotDao(): NeighborSnapshotDao
    abstract fun pingResultDao(): PingResultDao
    abstract fun gpsFixDao(): GpsFixDao
    abstract fun iperfResultDao(): IperfResultDao
    abstract fun iperfProfileDao(): IperfProfileDao
}
