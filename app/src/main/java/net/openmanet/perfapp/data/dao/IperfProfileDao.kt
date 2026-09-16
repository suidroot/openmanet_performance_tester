package net.openmanet.perfapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.openmanet.perfapp.data.entities.IperfProfile

@Dao
interface IperfProfileDao {
    @Query("SELECT * FROM iperf_profile ORDER BY name ASC")
    fun observeAll(): Flow<List<IperfProfile>>

    @Query("SELECT * FROM iperf_profile WHERE id = :id")
    suspend fun getById(id: Long): IperfProfile?

    @Insert
    suspend fun insert(profile: IperfProfile): Long

    @Delete
    suspend fun delete(profile: IperfProfile)
}
