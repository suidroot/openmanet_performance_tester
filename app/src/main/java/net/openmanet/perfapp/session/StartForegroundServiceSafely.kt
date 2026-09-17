package net.openmanet.perfapp.session

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Shared by TestSessionManager/IperfSessionManager: starts a foreground service, logging rather
 * than crashing the caller if the OS refuses (e.g. ForegroundServiceStartNotAllowedException on
 * Android 12+ when the app is background-restricted at the moment of the call) - both call sites
 * are reachable from a plain Switch/Button tap where an uncaught crash would be confusing.
 */
internal fun Context.startForegroundServiceSafely(intent: Intent, tag: String) {
    try {
        startForegroundService(intent)
    } catch (e: Exception) {
        Log.e(tag, "startForegroundService failed for ${intent.component?.className}", e)
    }
}
