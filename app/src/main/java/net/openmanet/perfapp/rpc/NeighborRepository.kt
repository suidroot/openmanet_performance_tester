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
                    throughputBps = neighbor.throughput,
                    // `interface` is a Kotlin keyword; the generated Java getter still works.
                    interfaceName = neighbor.getInterface(),
                )
            }
        }
    }
}
