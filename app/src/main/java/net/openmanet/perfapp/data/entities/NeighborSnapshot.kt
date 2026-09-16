package net.openmanet.perfapp.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mirrors openmanet.service.v1.MeshNeighbor (app/src/main/proto/openmanet/service/v1/mesh.proto),
 * vendored from the real schema in Phase 1. [txLinkRateJson]/[rxLinkRateJson] hold the nested
 * LinkRate messages as JSON rather than flattened columns since they're diagnostic detail, not
 * something queried directly. [rawJson] holds the full decoded message as a hedge against future
 * schema changes not yet reflected in this entity's typed columns.
 */
@Entity(
    tableName = "neighbor_snapshot",
    indices = [Index(value = ["sessionId", "timestampMs"])],
)
data class NeighborSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestampMs: Long,
    /** hostname.interface_name, e.g. "manet02.mesh1" */
    val neighbor: String,
    val hardwareAddress: String,
    val signalStrength: Int,
    val signal: Int,
    val lastSeenMs: Long,
    val throughputBps: Int,
    val interfaceName: String,
    val txLinkRateJson: String,
    val rxLinkRateJson: String,
    val rawJson: String,
)
