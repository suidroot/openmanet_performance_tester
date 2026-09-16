package net.openmanet.perfapp.rpc

import com.google.protobuf.Empty
import com.openmanet.service.v1.NodeServiceClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeRepository @Inject constructor(
    private val clientFactory: OpenManetClientFactory,
) {
    suspend fun listNodes(nodeIp: String): Result<List<MeshNode>> {
        val client = NodeServiceClient(clientFactory.create(nodeIp))
        return client.listNodes(Empty.getDefaultInstance()).toResult().map { response ->
            response.nodesList.map { node ->
                MeshNode(
                    hostname = node.hostname,
                    mac = node.mac,
                    ipAddress = node.ipaddr,
                    latitude = if (node.hasPosition()) node.position.latitude else null,
                    longitude = if (node.hasPosition()) node.position.longitude else null,
                    altitude = if (node.hasPosition()) node.position.altitude else null,
                )
            }
        }
    }
}
