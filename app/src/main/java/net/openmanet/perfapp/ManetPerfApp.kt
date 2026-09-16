package net.openmanet.perfapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp
import net.openmanet.perfapp.session.TestSessionService

@HiltAndroidApp
class ManetPerfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            TestSessionService.NOTIFICATION_CHANNEL_ID,
            "Test session",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows while ping/GPS/CoT logging is running in the background"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
