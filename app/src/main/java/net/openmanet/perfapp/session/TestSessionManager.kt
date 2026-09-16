package net.openmanet.perfapp.session

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openmanet.perfapp.ping.PingTarget
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper for starting/stopping TestSessionService from the UI layer. */
@Singleton
class TestSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start(nodeId: String, pingTargets: List<PingTarget>) {
        val intent = Intent(context, TestSessionService::class.java).apply {
            action = TestSessionService.ACTION_START
            putExtra(TestSessionService.EXTRA_NODE_ID, nodeId)
            putStringArrayListExtra(TestSessionService.EXTRA_PING_TARGET_HOSTS, ArrayList(pingTargets.map { it.host }))
            putStringArrayListExtra(TestSessionService.EXTRA_PING_TARGET_LABELS, ArrayList(pingTargets.map { it.label }))
        }
        context.startForegroundService(intent)
    }

    fun stop() {
        context.startService(Intent(context, TestSessionService::class.java).setAction(TestSessionService.ACTION_STOP))
    }
}
