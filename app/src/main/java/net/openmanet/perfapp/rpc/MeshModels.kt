package net.openmanet.perfapp.rpc

/**
 * App-level read models for the dashboard's live RPC calls (Phase 1: display only). Distinct
 * from data/entities' Room entities, which persist time-series readings captured during a test
 * session (wired up starting Phase 2) - these are scratch snapshots, not stored.
 */
data class MeshNode(
    val hostname: String,
    val mac: String,
    val ipAddress: String,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Float?,
)

data class MeshNeighbor(
    val neighbor: String,
    val hardwareAddress: String,
    val signalStrength: Int,
    val signal: Int,
    val lastSeenMs: Long,
    val throughputBps: Int,
    val interfaceName: String,
)

data class MeshStatus(
    val isConnected: Boolean,
    val connectedNeighbors: Int,
    val activeMeshInterfaces: Int,
    val isMeshGateway: Boolean,
    val selectedGatewayMac: String,
    val latitude: Double?,
    val longitude: Double?,
)
