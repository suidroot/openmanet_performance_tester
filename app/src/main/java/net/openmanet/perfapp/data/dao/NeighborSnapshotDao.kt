package net.openmanet.perfapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.openmanet.perfapp.data.entities.NeighborSnapshot

@Dao
interface NeighborSnapshotDao {
    @Query("SELECT * FROM neighbor_snapshot WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeForSession(sessionId: String): Flow<List<NeighborSnapshot>>

    @Query(
        "SELECT * FROM neighbor_snapshot WHERE sessionId = :sessionId " +
            "AND timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC",
    )
    suspend fun getInRange(sessionId: String, fromMs: Long, toMs: Long): List<NeighborSnapshot>

    @Insert
    suspend fun insert(snapshot: NeighborSnapshot): Long

    @Insert
    suspend fun insertAll(snapshots: List<NeighborSnapshot>)

    @Query("DELETE FROM neighbor_snapshot")
    suspend fun deleteAll()
}
