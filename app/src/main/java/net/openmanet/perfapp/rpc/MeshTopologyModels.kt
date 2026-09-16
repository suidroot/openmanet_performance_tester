package net.openmanet.perfapp.rpc

data class MeshTopologyNode(
    val mac: String,
    val hostname: String,
    /** Forwarding hops from the serving (connected) node; 0 for self, 99 means "unknown". */
    val hopsFromSelf: Int,
    val isSelf: Boolean,
    val isGateway: Boolean,
)

data class MeshTopologySnapshot(
    val selfHostname: String,
    val nodes: List<MeshTopologyNode>,
)
