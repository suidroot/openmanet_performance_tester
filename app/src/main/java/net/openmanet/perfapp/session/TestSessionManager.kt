package net.openmanet.perfapp.session

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper for starting/stopping TestSessionService from the UI layer. */
@Singleton
class TestSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start(nodeId: String, pingTargets: List<String>) {
        val intent = Intent(context, TestSessionService::class.java).apply {
            action = TestSessionService.ACTION_START
            putExtra(TestSessionService.EXTRA_NODE_ID, nodeId)
            putStringArrayListExtra(TestSessionService.EXTRA_PING_TARGETS, ArrayList(pingTargets))
        }
        context.startForegroundService(intent)
    }

    fun stop() {
        context.startService(Intent(context, TestSessionService::class.java).setAction(TestSessionService.ACTION_STOP))
    }
}
