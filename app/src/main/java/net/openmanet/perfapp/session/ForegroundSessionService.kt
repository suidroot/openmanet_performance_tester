package net.openmanet.perfapp.session

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.openmanet.perfapp.R

/**
 * Common skeleton shared by this app's two foreground "session" services (TestSessionService:
 * ping+GPS+neighbor logging, IperfSessionService: iperf runs) - a SupervisorJob-backed scope
 * cancelled in onDestroy, no binding, a single-action "Stop" notification, and the
 * API-34-vs-earlier startForeground() split every foreground service needs. Deliberately stops
 * short of also unifying the actual session/collection lifecycle logic: the two services
 * genuinely differ there (one coordinates several concurrent jobs and always owns a fresh
 * session; the other runs a single job and may reuse an already-active session), and forcing
 * that into a shared shape would trade real duplication for a leakier abstraction.
 */
abstract class ForegroundSessionService : Service() {
    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Handles the startForeground(id, notification) vs. startForeground(id, notification, type)
     * split - the latter is required from API 34, but takes a type that didn't exist before. */
    protected fun startForegroundCompat(notificationId: Int, notification: Notification, type: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId, notification, type)
        } else {
            startForeground(notificationId, notification)
        }
    }

    protected fun stopForegroundAndSelf() {
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    /** An ongoing notification with a single "Stop" action that re-delivers `stopAction` to this
     * same service - `this::class.java` resolves to whichever concrete subclass is calling this,
     * so each service doesn't need to pass its own class in. */
    protected fun buildStopNotification(channelId: String, title: String, text: String, stopAction: String): Notification {
        val stopIntent = Intent(this, this::class.java).setAction(stopAction)
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }
}
