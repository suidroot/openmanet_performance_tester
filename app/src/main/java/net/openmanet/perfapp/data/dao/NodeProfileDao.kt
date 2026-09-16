package net.openmanet.perfapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.openmanet.perfapp.data.entities.NodeProfile

@Dao
interface NodeProfileDao {
    @Query("SELECT * FROM node_profile ORDER BY lastConnectedAtMs DESC")
    fun observeAll(): Flow<List<NodeProfile>>

    @Query("SELECT * FROM node_profile WHERE ipAddress = :ipAddress")
    suspend fun getById(ipAddress: String): NodeProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: NodeProfile)

    @Query("DELETE FROM node_profile WHERE ipAddress = :ipAddress")
    suspend fun delete(ipAddress: String)
}
