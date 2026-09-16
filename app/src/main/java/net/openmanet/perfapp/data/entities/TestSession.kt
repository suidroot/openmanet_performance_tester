package net.openmanet.perfapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "test_session")
data class TestSession(
    @PrimaryKey val sessionId: String,
    val nodeId: String?,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val label: String?,
)
