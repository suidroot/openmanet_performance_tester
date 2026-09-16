package net.openmanet.perfapp.rpc

import com.google.protobuf.Empty
import com.openmanet.service.v1.StatusServiceClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusRepository @Inject constructor(
    private val clientFactory: OpenManetClientFactory,
) {
    suspend fun getServiceStatus(nodeIp: String): Result<MeshStatus> {
        val client = StatusServiceClient(clientFactory.create(nodeIp))
        return client.getServiceStatus(Empty.getDefaultInstance()).toResult().map { response ->
            val status = response.status
            MeshStatus(
                isConnected = status.isConnected,
                connectedNeighbors = status.connectedNeighbors,
                activeMeshInterfaces = status.activeMeshInterfaces,
                isMeshGateway = status.isMeshGateway,
                selectedGatewayMac = status.selectedGatewayMac,
                latitude = if (status.hasPosition()) status.position.latitude else null,
                longitude = if (status.hasPosition()) status.position.longitude else null,
            )
        }
    }
}
