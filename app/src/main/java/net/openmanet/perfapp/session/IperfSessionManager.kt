package net.openmanet.perfapp.session

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openmanet.perfapp.iperf.IperfConfig
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IperfSessionManager"

/** Thin wrapper for starting/stopping IperfSessionService from the UI layer. */
@Singleton
class IperfSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start(nodeIp: String, config: IperfConfig) {
        val intent = Intent(context, IperfSessionService::class.java).apply {
            action = IperfSessionService.ACTION_START
            putExtra(IperfSessionService.EXTRA_NODE_IP, nodeIp)
            putExtra(IperfSessionService.EXTRA_HOST, config.host)
            putExtra(IperfSessionService.EXTRA_PORT, config.port)
            putExtra(IperfSessionService.EXTRA_PROTOCOL, config.protocol.name)
            putExtra(IperfSessionService.EXTRA_DURATION_SECONDS, config.durationSeconds)
            putExtra(IperfSessionService.EXTRA_REVERSE, config.reverse)
            putExtra(IperfSessionService.EXTRA_ENGINE, config.engine.name)
            config.maxBitsPerSecond?.let { putExtra(IperfSessionService.EXTRA_MAX_BPS, it) }
        }
        context.startForegroundServiceSafely(intent, TAG)
    }

    fun stop() {
        context.startService(Intent(context, IperfSessionService::class.java).setAction(IperfSessionService.ACTION_STOP))
    }
}
