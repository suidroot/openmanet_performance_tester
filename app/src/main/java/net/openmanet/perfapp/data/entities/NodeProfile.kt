package net.openmanet.perfapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A previously-used node address, remembered for one-tap reconnect. The app no longer manages
 * Wi-Fi (the user joins the mesh SSID themselves via system settings before opening the app), so
 * this is just an address + label + last-used username - never the password (re-entered every
 * session; openmanetd's real login is PAM-backed against the device's admin account).
 */
@Entity(tableName = "node_profile")
data class NodeProfile(
    @PrimaryKey val ipAddress: String,
    val displayName: String,
    val lastUsername: String?,
    val lastConnectedAtMs: Long,
)
