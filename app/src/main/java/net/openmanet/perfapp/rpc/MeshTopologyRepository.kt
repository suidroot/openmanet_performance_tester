package net.openmanet.perfapp.rpc

import com.google.protobuf.Empty
import com.openmanet.mesh_topology.v1.MeshTopologyServiceClient
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps MeshTopologyService.GetMeshTopology - the source of real per-node hop counts and
 * gateway role (batman-adv/alfred derived), used to fill in the dashboard's HOPS and GATEWAY
 * fields rather than leaving them blank or fabricating a value. */
@Singleton
class MeshTopologyRepository @Inject constructor(
    private val clientFactory: OpenManetClientFactory,
) {
    suspend fun getMeshTopology(nodeIp: String): Result<MeshTopologySnapshot> {
        val client = MeshTopologyServiceClient(clientFactory.create(nodeIp))
        return client.getMeshTopology(Empty.getDefaultInstance()).toResult().map { response ->
            val topology = response.topology
            MeshTopologySnapshot(
                selfHostname = topology.selfHostname,
                nodes = topology.nodesList.map { node ->
                    MeshTopologyNode(
                        mac = node.mac,
                        hostname = node.hostname,
                        hopsFromSelf = node.hopsFromSelf,
                        isSelf = node.isSelf,
                        isGateway = node.isGateway,
                    )
                },
            )
        }
    }
}
