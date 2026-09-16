package net.openmanet.perfapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.openmanet.perfapp.data.entities.TestSession

@Dao
interface TestSessionDao {
    @Query("SELECT * FROM test_session ORDER BY startedAtMs DESC")
    fun observeAll(): Flow<List<TestSession>>

    @Query("SELECT * FROM test_session WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: String): TestSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: TestSession)

    @Update
    suspend fun update(session: TestSession)
}
