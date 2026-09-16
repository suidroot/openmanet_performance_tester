package net.openmanet.perfapp.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class GpsSource { DEVICE, COT, NODE_GNSS }

@Entity(
    tableName = "gps_fix",
    indices = [Index(value = ["sessionId", "timestampMs"]), Index(value = ["source"])],
)
data class GpsFix(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestampMs: Long,
    val source: GpsSource,
    val sourceId: String?,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val speedMps: Double?,
    val courseDeg: Double?,
    val fixQuality: String?,
    val rawPayload: String?,
)
