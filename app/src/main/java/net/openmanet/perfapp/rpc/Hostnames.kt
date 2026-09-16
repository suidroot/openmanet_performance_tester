package net.openmanet.perfapp.rpc

/**
 * OpenMANET's alfred-derived hostname fields carry a per-interface suffix - dot-separated
 * ("hostname.wlan0", per MeshNeighborService's `neighbor` field doc) or underscore-separated
 * ("hostname_wlan0", per network.v1.MeshNeighbors/mesh_topology.v1.MeshNode's doc, and what
 * NodeService.ListNodes' raw `Node.hostname` turned out to carry on a real mesh - confirmed
 * on-device: a node reachable over both a direct RF link and a batman-adv/vxlan0 tunnel shows up
 * as two suffixed ListNodes entries for one physical node, inflating any naive node count and
 * leaking the interface name into anything that displays the raw hostname). Both forms strip down
 * to the same base hostname mesh_topology/mesh_neighbors already report server-side.
 */
private val INTERFACE_SUFFIX = Regex("_(wlan|eth|vxlan|bat|mesh|blos)\\d*$")

fun String.baseHostname(): String = substringBefore(".").let { INTERFACE_SUFFIX.replace(it, "") }
