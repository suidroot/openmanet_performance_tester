package net.openmanet.perfapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.openmanet.perfapp.data.entities.GpsFix

@Dao
interface GpsFixDao {
    @Query("SELECT * FROM gps_fix WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeForSession(sessionId: String): Flow<List<GpsFix>>

    @Query(
        "SELECT * FROM gps_fix WHERE sessionId = :sessionId " +
            "AND timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC",
    )
    suspend fun getInRange(sessionId: String, fromMs: Long, toMs: Long): List<GpsFix>

    @Query("SELECT COUNT(*) FROM gps_fix WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Insert
    suspend fun insert(fix: GpsFix): Long

    @Query("DELETE FROM gps_fix")
    suspend fun deleteAll()
}
