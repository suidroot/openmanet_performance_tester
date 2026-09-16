package net.openmanet.perfapp.ping

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shells out to the system ping binary - Android apps cannot open raw ICMP sockets without
 * root, so this is the standard non-root workaround (used by most Android network-diagnostic
 * apps). Routes through the currently active network's interface explicitly (`-I <iface>`)
 * rather than relying on Android's routing table, since the phone may have both the mesh Wi-Fi
 * (joined manually by the user via system settings before opening the app) and cellular active
 * at once.
 */
@Singleton
class PingRunner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun pingOnce(host: String, timeoutSeconds: Int = 2): String = withContext(Dispatchers.IO) {
        val network = connectivityManager.activeNetwork
        val iface = network?.let { connectivityManager.getLinkProperties(it)?.interfaceName }

        val command = buildList {
            add("/system/bin/ping")
            add("-c"); add("1")
            add("-W"); add(timeoutSeconds.toString())
            if (iface != null) {
                add("-I"); add(iface)
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
