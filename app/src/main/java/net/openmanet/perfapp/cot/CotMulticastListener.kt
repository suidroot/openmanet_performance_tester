package net.openmanet.perfapp.cot

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens for SA (situational awareness) multicast on 239.2.3.1 (see
 * https://openmanet.github.io/docs/gnss). The docs describe this as a single Cursor-on-Target
 * feed, but confirmed on a real deployment that isn't the whole picture: openmanetd itself
 * broadcasts raw NMEA position sentences (its own GNSS receiver output) rather than CoT, and the
 * docs' own "secondary NMEA feed" mention put that specifically on port 4349 - so GpsRepository
 * listens on both ports (COT_PORT for CoT-emitting units, NMEA_PORT for openmanetd's own feed)
 * and dispatches each payload by its leading character ('<' vs '$'). Both ports share one
 * network/interface resolution and one MulticastLock (acquired once for as many ports as are
 * requested) rather than each port redoing that work and holding its own lock - there's exactly
 * one mesh network to resolve regardless of how many ports are being listened on.
 *
 * Joins the group via the NetworkInterface-aware overload (not the deprecated plain InetAddress
 * one) so it's the mesh Wi-Fi interface specifically that's joined, not whatever Android would
 * pick arbitrarily on a device with cellular/other Wi-Fi also active. A
 * WifiManager.MulticastLock is held for the listener's lifetime since Android drops multicast
 * packets by default to save power on Wi-Fi radios.
 *
 * Deliberately does NOT also call `network.bindSocket(socket)` - that seemed like the obviously
 * "more correct" way to pin the socket to the mesh network (matching the pattern used for
 * OkHttp/ping/iperf elsewhere), but confirmed on a real device it silently breaks multicast
 * *reception* specifically: ATAK, running on the same device on the same network, received the
 * same traffic fine the whole time this app's socket sat joined-but-silent with no errors.
 * `bindSocket` marks the socket for Android's per-app/per-network fwmark routing and firewall
 * rules, which are built around normal unicast connection tracking - inbound multicast doesn't
 * fit that model the same way, and the mark evidently causes inbound multicast datagrams to be
 * filtered before they ever reach this socket's receive() call. `joinGroup(group,
 * NetworkInterface)` alone is sufficient to pick the right interface without that side effect.
 */
@Singleton
class CotMulticastListener @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /** Emits raw UDP payload strings received on any of `ports`; malformed/partial datagrams are
     * handled by the caller (CotXmlParser/NmeaParser return null rather than throwing) so one bad
     * packet never ends the stream. */
    fun listen(ports: List<Int> = listOf(COT_PORT)): Flow<String> = callbackFlow {
        val network = connectivityManager.activeNetwork
        if (network == null) {
            close(IllegalStateException("No active network - join the mesh Wi-Fi first"))
            return@callbackFlow
        }
        val ifaceName = connectivityManager.getLinkProperties(network)?.interfaceName
        val networkInterface = ifaceName?.let { NetworkInterface.getByName(it) }
        if (networkInterface == null) {
            close(IllegalStateException("Could not resolve mesh network interface"))
            return@callbackFlow
        }

        val lock = wifiManager.createMulticastLock("cot-listener").apply {
            setReferenceCounted(true)
            try {
                acquire()
            } catch (e: SecurityException) {
                // Missing CHANGE_WIFI_MULTICAST_STATE - without it Android silently drops
                // multicast on Wi-Fi, so every packet other apps (e.g. ATAK) receive fine would
                // still never reach this socket even once the group is joined below.
                Log.e(TAG, "MulticastLock.acquire() denied - is CHANGE_WIFI_MULTICAST_STATE granted?", e)
                close(e)
                return@callbackFlow
            }
        }

        val opened = mutableListOf<Pair<MulticastSocket, InetSocketAddress>>()
        for (port in ports) {
            val socket = MulticastSocket(port)
            val group = InetSocketAddress(InetAddress.getByName(COT_GROUP), port)
            try {
                socket.joinGroup(group, networkInterface)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to join $COT_GROUP:$port on ${networkInterface.name}", e)
                socket.close()
                continue // Other ports may still work - e.g. if one port is blocked/in use.
            }
            Log.i(TAG, "Joined $COT_GROUP:$port on ${networkInterface.name}")
            opened += socket to group
        }
        if (opened.isEmpty()) {
            lock.release()
            close(IllegalStateException("Failed to join $COT_GROUP on any of $ports"))
            return@callbackFlow
        }

        val receiveJobs = opened.map { (socket, group) ->
            launch(Dispatchers.IO) {
                val buffer = ByteArray(4096)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        Log.d(TAG, "Received ${packet.length} bytes on port ${group.port} from ${packet.address}")
                        trySend(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.w(TAG, "Receive error on port ${group.port}, continuing", e)
                            continue
                        } else {
                            break
                        }
                    }
                }
            }
        }

        awaitClose {
            receiveJobs.forEach { it.cancel() }
            opened.forEach { (socket, group) ->
                runCatching { socket.leaveGroup(group, networkInterface) }
                socket.close()
            }
            lock.release()
        }
    }

    companion object {
        const val COT_GROUP = "239.2.3.1"
        const val COT_PORT = 6969
        const val NMEA_PORT = 4349
        private const val TAG = "CotMulticastListener"
    }
}
