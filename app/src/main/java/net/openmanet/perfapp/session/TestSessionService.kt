package net.openmanet.perfapp.session

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.data.dao.TestSessionDao
import net.openmanet.perfapp.data.entities.TestSession
import net.openmanet.perfapp.gps.GpsRepository
import net.openmanet.perfapp.ping.PingCollector
import net.openmanet.perfapp.ping.PingTarget
import net.openmanet.perfapp.rpc.NeighborSnapshotCollector
import java.util.UUID
import javax.inject.Inject

/**
 * Foreground service coordinating ping + GPS (device + CoT) collection for a test session, so
 * logging survives the app being backgrounded. iperf tests are a separate, standalone run of
 * their own (see IperfSessionService) and aren't started here - they'll tag their results with
 * whatever session is currently active while one is running.
 */
@AndroidEntryPoint
class TestSessionService : ForegroundSessionService() {

    @Inject lateinit var testSessionDao: TestSessionDao
    @Inject lateinit var pingCollector: PingCollector
    @Inject lateinit var gpsRepository: GpsRepository
    @Inject lateinit var neighborSnapshotCollector: NeighborSnapshotCollector
    @Inject lateinit var activeSessionHolder: ActiveSessionHolder
    @Inject lateinit var clock: AppClock

    private var collectionJobs: List<Job> = emptyList()
    private var currentSessionId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val hosts = intent.getStringArrayListExtra(EXTRA_PING_TARGET_HOSTS).orEmpty()
                val labels = intent.getStringArrayListExtra(EXTRA_PING_TARGET_LABELS).orEmpty()
                startSession(
                    nodeId = intent.getStringExtra(EXTRA_NODE_ID),
                    pingTargets = hosts.zip(labels) { host, label -> PingTarget(host, label) },
                )
            }
            ACTION_STOP -> stopSession()
        }
        return START_NOT_STICKY
    }

    private fun startSession(nodeId: String?, pingTargets: List<PingTarget>) {
        if (currentSessionId != null) return
        val sessionId = UUID.randomUUID().toString()
        currentSessionId = sessionId
        activeSessionHolder.set(sessionId)

        startForegroundCompat(
            NOTIFICATION_ID,
            buildStopNotification(
                NOTIFICATION_CHANNEL_ID,
                "Test session running",
                "Pinging ${pingTargets.size} target(s), logging GPS",
                ACTION_STOP,
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        serviceScope.launch {
            testSessionDao.insert(TestSession(sessionId, nodeId, clock.nowMs(), null, null))
        }

        val jobs = mutableListOf<Job>()
        jobs += pingCollector.collect(sessionId, pingTargets, PingCollector.DEFAULT_INTERVAL_MS, serviceScope)
        jobs += gpsRepository.collectCotFixes(sessionId, serviceScope)
        if (hasLocationPermission()) {
            jobs += gpsRepository.collectDeviceFixes(sessionId, serviceScope)
        }
        if (nodeId != null) {
            jobs += neighborSnapshotCollector.collect(sessionId, nodeId, PingCollector.DEFAULT_INTERVAL_MS, serviceScope)
        }
        collectionJobs = jobs
    }

    private fun stopSession() {
        collectionJobs.forEach { it.cancel() }
        collectionJobs = emptyList()

        val sessionId = currentSessionId
        currentSessionId = null
        activeSessionHolder.set(null)

        if (sessionId != null) {
            serviceScope.launch {
                testSessionDao.getById(sessionId)?.let { session ->
                    testSessionDao.update(session.copy(endedAtMs = clock.nowMs()))
                }
            }
        }

        stopForegroundAndSelf()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START = "net.openmanet.perfapp.session.START"
        const val ACTION_STOP = "net.openmanet.perfapp.session.STOP"
        const val EXTRA_NODE_ID = "node_id"
        const val EXTRA_PING_TARGET_HOSTS = "ping_target_hosts"
        const val EXTRA_PING_TARGET_LABELS = "ping_target_labels"
        const val NOTIFICATION_CHANNEL_ID = "test_session"
        const val NOTIFICATION_ID = 1001
    }
}
