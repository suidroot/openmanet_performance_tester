package net.openmanet.perfapp.rpc

import com.google.protobuf.Empty
import com.openmanet.service.v1.NodeServiceClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeRepository @Inject constructor(
    private val clientFactory: OpenManetClientFactory,
    private val meshTopologyRepository: MeshTopologyRepository,
) {
    /**
     * NodeService.ListNodes' raw hostname carries a "_<iface>" suffix and one entry per active
     * interface on multi-homed nodes (see rpc/Hostnames.kt) - confirmed on a real mesh where a
     * 2-physical-node mesh reported 3 raw entries. MeshTopologyService.GetMeshTopology's nodes
     * are already deduplicated to one per physical node with the suffix stripped, so that's used
     * as ground truth to collapse the raw list back down; if the topology call fails, falls back
     * to stripping the suffix without deduping (better than showing the raw interface-qualified
     * name, but may still double-count a multi-homed node).
     */
    suspend fun listNodes(nodeIp: String): Result<List<MeshNode>> {
        val client = NodeServiceClient(clientFactory.create(nodeIp))
        return client.listNodes(Empty.getDefaultInstance()).toResult().map { response ->
            val rawNodes = response.nodesList.map { node ->
                MeshNode(
                    hostname = node.hostname,
                    mac = node.mac,
                    ipAddress = node.ipaddr,
                    latitude = if (node.hasPosition()) node.position.latitude else null,
                    longitude = if (node.hasPosition()) node.position.longitude else null,
                    altitude = if (node.hasPosition()) node.position.altitude else null,
                )
            }
            val canonicalHostnames = meshTopologyRepository.getMeshTopology(nodeIp).getOrNull()
                ?.nodes?.mapNotNull { it.hostname.takeIf(String::isNotBlank) }.orEmpty()
            dedupeByBaseHostname(rawNodes, canonicalHostnames)
        }
    }
}

private fun dedupeByBaseHostname(rawNodes: List<MeshNode>, canonicalHostnames: List<String>): List<MeshNode> {
    val byBase = LinkedHashMap<String, MeshNode>()
    for (node in rawNodes) {
        val base = canonicalHostnames.firstOrNull { node.hostname == it || node.hostname.startsWith("${it}_") }
            ?: node.hostname.baseHostname()
        val cleaned = node.copy(hostname = base)
        val existing = byBase[base]
        // Prefer whichever raw entry actually carries a position fix; otherwise keep the first seen.
        if (existing == null || (existing.latitude == null && cleaned.latitude != null)) {
            byBase[base] = cleaned
        }
    }
    return byBase.values.toList()
}
