package net.openmanet.perfapp.iperf

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the vendored iperf3 CLI binary (see scripts/build_iperf3.sh) as a subprocess and streams
 * its stdout line by line - the same non-root-friendly approach PingRunner uses, chosen over a
 * JNI/libiperf bridge per the plan's Phase 3 spike (simpler, and -i 1 human-readable output
 * gives live per-second progress without needing a callback bridge into native code).
 *
 * The binary lives under applicationInfo.nativeLibraryDir (packaged as jniLibs/<abi>/libiperf3exec.so
 * - named like a shared library so Android's APK installer extracts it into the one
 * app-private location exempt from Android 10+'s restrictions on executing app-writable files).
 */
@Singleton
class IperfProcessRunner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val binaryPath: String
        get() = File(context.applicationInfo.nativeLibraryDir, "libiperf3exec.so").absolutePath

    fun run(config: IperfConfig): Flow<String> = callbackFlow {
        val localAddress = connectivityManager.activeNetwork
            ?.let { connectivityManager.getLinkProperties(it) }
            ?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull()
            ?.hostAddress

        val command = buildList {
            add(binaryPath)
            add("-c"); add(config.host)
            add("-p"); add(config.port.toString())
            add("-t"); add(config.durationSeconds.toString())
            add("-i"); add("1")
            add("-f"); add("m") // force Mbit/s + MBytes so the parser doesn't need to detect units
            if (config.protocol == IperfProtocol.UDP) add("-u")
            if (config.reverse) add("-R")
            if (localAddress != null) {
                add("-B"); add(localAddress)
            }
        }

        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(command).redirectErrorStream(true).start()
        }

        val readerJob = launch(Dispatchers.IO + CoroutineName("iperf3-reader")) {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> trySend(line) }
            }
            close()
        }

        awaitClose {
            readerJob.cancel()
            process.destroy()
        }
    }
}
