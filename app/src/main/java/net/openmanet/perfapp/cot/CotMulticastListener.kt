package net.openmanet.perfapp.cot

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
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
 * Listens for Cursor-on-Target XML broadcast on the SA multicast group (239.2.3.1:6969 - see
 * https://openmanet.github.io/docs/gnss). Must run on the currently active network specifically
 * (the mesh Wi-Fi, joined manually by the user via system settings before opening the app): the
 * socket is bound to it and joins the group via the NetworkInterface-aware overload (not the
 * deprecated plain InetAddress one), since Android would otherwise pick an interface arbitrarily
 * - wrong on a device with both mesh Wi-Fi and cellular/other Wi-Fi active. A
 * WifiManager.MulticastLock is held for the listener's lifetime since Android drops multicast
 * packets by default to save power on Wi-Fi radios.
 */
@Singleton
class CotMulticastListener @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /** Emits raw UDP payload strings; malformed/partial datagrams are handled by the caller
     * (CotXmlParser returns null rather than throwing) so one bad packet never ends the stream. */
    fun listen(): Flow<String> = callbackFlow {
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
            acquire()
        }

        val socket = MulticastSocket(COT_PORT)
        network.bindSocket(socket)
        val group = InetSocketAddress(InetAddress.getByName(COT_GROUP), COT_PORT)
        socket.joinGroup(group, networkInterface)

        val receiveJob = launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    trySend(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
                } catch (e: Exception) {
                    if (isActive) continue else break
                }
            }
        }

        awaitClose {
            receiveJob.cancel()
            runCatching { socket.leaveGroup(group, networkInterface) }
            socket.close()
            lock.release()
        }
    }

    companion object {
        const val COT_GROUP = "239.2.3.1"
        const val COT_PORT = 6969
    }
}
