package net.openmanet.perfapp.ping

/** A ping target paired with the OpenManet node hostname it belongs to, from NodeService's
 * discovered node list - not an arbitrary/manually-entered host. */
data class PingTarget(
    val host: String,
    val label: String,
)
