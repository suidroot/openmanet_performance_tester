package net.openmanet.perfapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp
import net.openmanet.perfapp.session.IperfSessionService
import net.openmanet.perfapp.session.TestSessionService

@HiltAndroidApp
class ManetPerfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                TestSessionService.NOTIFICATION_CHANNEL_ID,
                "Test session",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shows while ping/GPS/CoT logging is running in the background" },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                IperfSessionService.NOTIFICATION_CHANNEL_ID,
                "iperf test",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shows while an iperf test is running in the background" },
        )
    }
}
