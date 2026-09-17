package net.openmanet.perfapp.session

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openmanet.perfapp.ping.PingTarget
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TestSessionManager"

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
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            // E.g. ForegroundServiceStartNotAllowedException (Android 12+) if the OS considers
            // the app background-restricted at the moment of the call - surfaced here rather
            // than left to crash the caller, since this is reachable from a plain Switch toggle
            // tap where a crash would be especially confusing.
            Log.e(TAG, "startForegroundService failed for TestSessionService", e)
        }
    }

    fun stop() {
        context.startService(Intent(context, TestSessionService::class.java).setAction(TestSessionService.ACTION_STOP))
    }
}
