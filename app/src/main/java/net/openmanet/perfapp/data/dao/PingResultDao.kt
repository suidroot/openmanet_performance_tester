package net.openmanet.perfapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.openmanet.perfapp.data.entities.PingResult

@Dao
interface PingResultDao {
    @Query("SELECT * FROM ping_result WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeForSession(sessionId: String): Flow<List<PingResult>>

    @Query(
        "SELECT * FROM ping_result WHERE sessionId = :sessionId " +
            "AND timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC",
    )
    suspend fun getInRange(sessionId: String, fromMs: Long, toMs: Long): List<PingResult>

    @Insert
    suspend fun insert(result: PingResult): Long

    @Query("DELETE FROM ping_result")
    suspend fun deleteAll()
}
