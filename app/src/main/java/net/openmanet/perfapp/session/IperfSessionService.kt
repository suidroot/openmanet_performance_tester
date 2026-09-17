package net.openmanet.perfapp.session

import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.data.dao.TestSessionDao
import net.openmanet.perfapp.data.entities.TestSession
import net.openmanet.perfapp.iperf.IperfConfig
import net.openmanet.perfapp.iperf.IperfEngine
import net.openmanet.perfapp.iperf.IperfProtocol
import net.openmanet.perfapp.iperf.IperfRepository
import java.util.UUID
import javax.inject.Inject

private const val TAG = "IperfSessionService"

/**
 * Foreground service running one iperf test, so it (a) survives navigating away from the iperf
 * screen or backgrounding the app, and (b) is controllable via a Start/Stop button regardless of
 * which screen (if any) is currently visible - both were explicit asks, and neither was possible
 * when IperfViewModel ran the test directly in its own viewModelScope, which died the moment its
 * NavBackStackEntry did.
 *
 * Deliberately independent of the ping/GPS TestSessionService and its "logging" toggle - iperf is
 * a standalone test (see IperfCard's doc comment) and must be runnable with that toggle off. If a
 * ping/GPS session happens to be active, this reuses its sessionId so the iperf rows correlate
 * with the same session_log.csv export; otherwise it creates its own lightweight TestSession row
 * purely so the run has a proper identity (visible in the Sessions list, exportable) rather than
 * requiring logging to be on first.
 */
@AndroidEntryPoint
class IperfSessionService : ForegroundSessionService() {

    @Inject lateinit var iperfRepository: IperfRepository
    @Inject lateinit var testSessionDao: TestSessionDao
    @Inject lateinit var activeSessionHolder: ActiveSessionHolder
    @Inject lateinit var activeIperfSessionHolder: ActiveIperfSessionHolder
    @Inject lateinit var clock: AppClock

    private var runJob: Job? = null

    /** Set only when this service created its own standalone session (no ping/GPS session was
     * active) - only that kind gets its endedAtMs finalized when the run ends; a reused ping/GPS
     * session's lifecycle belongs to TestSessionService, not this one. */
    private var ownedSessionId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRun(
                nodeIp = intent.getStringExtra(EXTRA_NODE_IP),
                config = IperfConfig(
                    host = intent.getStringExtra(EXTRA_HOST).orEmpty(),
                    port = intent.getIntExtra(EXTRA_PORT, 5201),
                    protocol = if (intent.getStringExtra(EXTRA_PROTOCOL) == "UDP") IperfProtocol.UDP else IperfProtocol.TCP,
                    durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 10),
                    reverse = intent.getBooleanExtra(EXTRA_REVERSE, false),
                    engine = if (intent.getStringExtra(EXTRA_ENGINE) == IperfEngine.V2.name) IperfEngine.V2 else IperfEngine.V3,
                    maxBitsPerSecond = intent.getLongExtra(EXTRA_MAX_BPS, -1L).takeIf { it > 0 },
                ),
            )
            ACTION_STOP -> stopRun()
        }
        return START_NOT_STICKY
    }

    private fun startRun(nodeIp: String?, config: IperfConfig) {
        if (runJob != null) return

        // Captured before the coroutine starts: if a ping/GPS session is active right now, reuse
        // its id for correlation; whether it's *still* active when this run finishes is checked
        // again in the finally block below (see its comment).
        val reusedSessionId = activeSessionHolder.sessionId.value
        val testRunId = UUID.randomUUID().toString()

        startForegroundCompat(
            NOTIFICATION_ID,
            buildStopNotification(
                NOTIFICATION_CHANNEL_ID,
                "iperf test running",
                "${config.host}:${config.port} (${if (config.engine == IperfEngine.V2) "iperf2" else "iperf3"})",
                ACTION_STOP,
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        runJob = serviceScope.launch {
            // The insert (if any) and the matching endedAtMs update in the finally block below
            // both run in this same coroutine, in sequence - not as two independently-launched,
            // unordered coroutines - so the update can never race ahead of the insert it depends
            // on and silently no-op via getById() returning null.
            val sessionId = reusedSessionId ?: UUID.randomUUID().toString().also { newId ->
                ownedSessionId = newId
                testSessionDao.insert(TestSession(newId, nodeIp, clock.nowMs(), null, null))
            }
            activeIperfSessionHolder.set(testRunId, config)

            try {
                iperfRepository.run(sessionId, testRunId, config).collect { }
            } catch (e: CancellationException) {
                throw e // Stop button/normal cancellation, not a failure - don't report an error.
            } catch (e: Exception) {
                Log.e(TAG, "iperf run failed", e)
                activeIperfSessionHolder.setError(e.message ?: "iperf failed")
            } finally {
                // withContext(NonCancellable): this coroutine may already be cancelled (Stop was
                // tapped) by the time we get here, and a plain suspend DB call in a cancelled
                // coroutine throws immediately instead of running - these writes need to happen
                // regardless.
                withContext(NonCancellable) {
                    // If we reused an active ping/GPS session but it was stopped elsewhere (the
                    // "LOG" toggle turned off) while this run was still going, TestSessionService
                    // already finalized its endedAtMs - before this run's own tail of iperf rows
                    // were written after that timestamp. Extend it to this run's actual end so
                    // the session's recorded window covers everything tagged with its id.
                    val idToFinalize = ownedSessionId
                        ?: sessionId.takeIf { activeSessionHolder.sessionId.value != it }
                    idToFinalize?.let { id ->
                        testSessionDao.getById(id)?.let { session -> testSessionDao.update(session.copy(endedAtMs = clock.nowMs())) }
                    }
                }
                activeIperfSessionHolder.set(null, null)
                ownedSessionId = null
                runJob = null
                stopForegroundAndSelf()
            }
        }
    }

    private fun stopRun() {
        val job = runJob
        if (job == null) {
            // A stray/duplicate STOP (e.g. two Stop taps racing) can deliver this to a fresh
            // Service instance with nothing running - without this, that instance would never
            // call stopSelf() and would linger indefinitely.
            stopSelf()
            return
        }
        job.cancel()
    }

    companion object {
        const val ACTION_START = "net.openmanet.perfapp.session.IPERF_START"
        const val ACTION_STOP = "net.openmanet.perfapp.session.IPERF_STOP"
        const val EXTRA_NODE_IP = "node_ip"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"
        const val EXTRA_REVERSE = "reverse"
        const val EXTRA_ENGINE = "engine"
        const val EXTRA_MAX_BPS = "max_bps"
        const val NOTIFICATION_CHANNEL_ID = "iperf_session"
        const val NOTIFICATION_ID = 1002
    }
}
