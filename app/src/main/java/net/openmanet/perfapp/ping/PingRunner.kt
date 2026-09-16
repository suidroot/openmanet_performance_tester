package net.openmanet.perfapp.ping

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shells out to the system ping binary - Android apps cannot open raw ICMP sockets without
 * root, so this is the standard non-root workaround (used by most Android network-diagnostic
 * apps). Routes through the currently active network explicitly by binding the source address
 * to that network's own IPv4 address (`-I <local-ip>`) rather than relying on Android's default
 * routing table, since the phone may have both the mesh Wi-Fi (joined manually by the user via
 * system settings before opening the app) and cellular active at once.
 *
 * Deliberately `-I <local-ip>`, not `-I <interface-name>`: toybox's ping resolves an `-I` value
 * that parses as an interface name (e.g. "wlan0") via `if_nametoindex` and binds with
 * `SO_BINDTODEVICE`, which requires a privileged capability a normal app UID does not have and
 * fails every ping with `SO_BINDTODEVICE: Operation not permitted` - confirmed on a real device,
 * where this was silently turning every ping into a false "timeout" even though the target hosts
 * were reachable. A value that doesn't parse as an interface name is instead used as an ordinary
 * source-address `bind()`, which is unprivileged and achieves the same "go out this network"
 * effect.
 */
@Singleton
class PingRunner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun pingOnce(host: String, timeoutSeconds: Int = 2): String = withContext(Dispatchers.IO) {
        val network = connectivityManager.activeNetwork
        val localIp = network?.let { connectivityManager.getLinkProperties(it) }
            ?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.address?.hostAddress

        val command = buildList {
            add("/system/bin/ping")
            add("-c"); add("1")
            add("-W"); add(timeoutSeconds.toString())
            if (localIp != null) {
                add("-I"); add(localIp)
            }
            add(host)
        }

        try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (e: Exception) {
            "ping failed to start: ${e.message}"
        }
    }
}
