package net.openmanet.perfapp.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ping_result",
    indices = [Index(value = ["sessionId", "timestampMs"])],
)
data class PingResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestampMs: Long,
    val targetHost: String,
    val targetLabel: String?,
    val rttMs: Double?,
    val success: Boolean,
    val rawOutputLine: String?,
)
