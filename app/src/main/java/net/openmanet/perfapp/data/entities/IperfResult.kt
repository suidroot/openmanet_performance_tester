package net.openmanet.perfapp.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "iperf_result",
    indices = [Index(value = ["sessionId", "timestampMs"]), Index(value = ["testRunId"])],
)
data class IperfResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestampMs: Long,
    val testRunId: String,
    val isSummary: Boolean,
    val targetHost: String,
    val targetPort: Int,
    val protocol: String,
    val direction: String,
    val intervalStartSec: Double?,
    val intervalEndSec: Double?,
    val bytesTransferred: Long?,
    val bitsPerSecond: Double?,
    val jitterMs: Double?,
    val lostPackets: Int?,
    val retransmits: Int?,
)
