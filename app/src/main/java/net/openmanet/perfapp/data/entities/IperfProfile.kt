package net.openmanet.perfapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved iperf test configuration the user can re-run without re-entering settings. */
@Entity(tableName = "iperf_profile")
data class IperfProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: String, // "TCP" | "UDP"
    val durationSeconds: Int,
    val reverse: Boolean,
    val engine: String = "V3", // "V2" | "V3" - see iperf/IperfEngine
)
