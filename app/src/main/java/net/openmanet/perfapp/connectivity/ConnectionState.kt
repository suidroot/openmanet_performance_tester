package net.openmanet.perfapp.connectivity

/**
 * A node the user is in the process of connecting to, before it's worth saving as a NodeProfile.
 * The app does not manage Wi-Fi - the user joins the mesh SSID themselves via system settings
 * before opening the app, so this is just an address, not credentials.
 */
data class PendingNode(
    val ip: String,
    val displayName: String,
)

/**
 * Drives the Enter Node Address -> Connected flow (see ui/nav.ConnectionViewModel and
 * ui/nodeselect.NodeSelectScreen). "Connecting" does a real reachability check (StatusService.
 * GetServiceStatus) against the entered address rather than just accepting it blindly, since a
 * wrong/unreachable IP is the most likely failure now that Wi-Fi join is out of the picture.
 */
sealed interface ConnectionState {
    data object EnteringNodeAddress : ConnectionState
    data class Connecting(val node: PendingNode) : ConnectionState
    data class Connected(val node: PendingNode) : ConnectionState
    data class Error(val previous: ConnectionState, val message: String) : ConnectionState
}
