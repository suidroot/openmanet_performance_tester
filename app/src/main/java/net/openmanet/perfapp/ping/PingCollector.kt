package net.openmanet.perfapp.ping

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.data.dao.PingResultDao
import javax.inject.Inject
import javax.inject.Singleton

/** Runs one repeating ping loop per target, persisting each result as it completes. */
@Singleton
class PingCollector @Inject constructor(
    private val pingRunner: PingRunner,
    private val pingResultDao: PingResultDao,
    private val clock: AppClock,
) {
    fun collect(sessionId: String, targets: List<PingTarget>, intervalMs: Long, scope: CoroutineScope): List<Job> =
        targets.map { target ->
            scope.launch {
                while (isActive) {
                    val raw = pingRunner.pingOnce(target.host)
                    val result = PingOutputParser.parse(sessionId, target.host, clock.nowMs(), raw)
                        .copy(targetLabel = target.label)
                    pingResultDao.insert(result)
                    delay(intervalMs)
                }
            }
        }

    companion object {
        const val DEFAULT_INTERVAL_MS = 2_000L
    }
}
