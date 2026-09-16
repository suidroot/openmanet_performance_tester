package net.openmanet.perfapp.rpc

import com.google.protobuf.Empty
import com.openmanet.service.v1.MeshNeighborServiceClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeighborRepository @Inject constructor(
    private val clientFactory: OpenManetClientFactory,
) {
    suspend fun listMeshNeighbors(nodeIp: String): Result<List<MeshNeighbor>> {
        val client = MeshNeighborServiceClient(clientFactory.create(nodeIp))
        return client.listMeshNeighbors(Empty.getDefaultInstance()).toResult().map { response ->
            response.neighborsList.map { neighbor ->
                MeshNeighbor(
                    neighbor = neighbor.neighbor,
                    hardwareAddress = neighbor.hardwareAddress,
                    signalStrength = neighbor.signalStrength,
                    signal = neighbor.signal,
                    lastSeenMs = neighbor.lastSeen,
                    // The proto doc claims `throughput` is already scaled to bit/s ("kbit/s
                    // scaled up"), but on a real mesh the value is still kbit/s unscaled -
                    // displaying it as-is under-reported a 400+ Mbps link as "400 Kbps". Scale it
                    // here so every consumer (dashboard, ping's expected-throughput hint, CSV
                    // export) works in real bit/s without needing to know about the mismatch.
                    throughputBps = neighbor.throughput * 1_000,
                    // `interface` is a Kotlin keyword; the generated Java getter still works.
                    interfaceName = neighbor.getInterface(),
                )
            }
        }
    }
}
