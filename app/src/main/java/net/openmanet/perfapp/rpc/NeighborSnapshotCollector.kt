package net.openmanet.perfapp.rpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.data.dao.NeighborSnapshotDao
import net.openmanet.perfapp.data.entities.NeighborSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Periodically persists MeshNeighborService's live per-neighbor stats (signal, throughput, ...)
 * as a time series, so a session's exported log can join a ping sample against "what was this
 * link's signal/throughput at roughly that moment" instead of only ever seeing the current
 * instantaneous value. [NeighborSnapshot] existed as an entity since Phase 1 but had no writer -
 * this is it.
 *
 * [MeshNeighbor] (the RPC read model) doesn't retain the original decoded proto message, so
 * `txLinkRateJson`/`rxLinkRateJson`/`rawJson` are written as empty objects here rather than a
 * real serialization - none of this collector's consumers (the CSV export's signal/throughput
 * columns) read them; they exist on the entity as a hedge for future direct inspection.
 */
@Singleton
class NeighborSnapshotCollector @Inject constructor(
    private val neighborRepository: NeighborRepository,
    private val neighborSnapshotDao: NeighborSnapshotDao,
    private val clock: AppClock,
) {
    fun collect(sessionId: String, nodeIp: String, intervalMs: Long, scope: CoroutineScope): Job =
        scope.launch {
            while (isActive) {
                neighborRepository.listMeshNeighbors(nodeIp).getOrNull()?.forEach { neighbor ->
                    neighborSnapshotDao.insert(
                        NeighborSnapshot(
                            sessionId = sessionId,
                            timestampMs = clock.nowMs(),
                            neighbor = neighbor.neighbor,
                            hardwareAddress = neighbor.hardwareAddress,
                            signalStrength = neighbor.signalStrength,
                            signal = neighbor.signal,
                            lastSeenMs = neighbor.lastSeenMs,
                            throughputBps = neighbor.throughputBps,
                            interfaceName = neighbor.interfaceName,
                            txLinkRateJson = "{}",
                            rxLinkRateJson = "{}",
                            rawJson = "{}",
                        ),
                    )
                }
                delay(intervalMs)
            }
        }
}
