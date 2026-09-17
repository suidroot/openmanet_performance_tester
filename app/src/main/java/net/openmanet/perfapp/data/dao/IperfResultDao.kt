package net.openmanet.perfapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.openmanet.perfapp.data.entities.IperfResult

@Dao
interface IperfResultDao {
    @Query("SELECT * FROM iperf_result WHERE testRunId = :testRunId ORDER BY timestampMs ASC")
    fun observeForTestRun(testRunId: String): Flow<List<IperfResult>>

    @Query(
        "SELECT * FROM iperf_result WHERE sessionId = :sessionId " +
            "AND timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC",
    )
    suspend fun getInRange(sessionId: String, fromMs: Long, toMs: Long): List<IperfResult>

    @Insert
    suspend fun insert(result: IperfResult): Long

    @Query("DELETE FROM iperf_result")
    suspend fun deleteAll()
}
