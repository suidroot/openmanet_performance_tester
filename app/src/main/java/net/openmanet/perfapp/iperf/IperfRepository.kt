package net.openmanet.perfapp.iperf

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.data.dao.IperfResultDao
import net.openmanet.perfapp.data.entities.IperfResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs one iperf3 test (a discrete, user-triggered action - unlike ping/GPS this isn't a
 * continuous background collector) and persists every interval sample plus the final summary
 * row(s) as they arrive, tagging them with a fresh testRunId so a session's iperf_result rows
 * group cleanly by run even across repeated tests against the same target.
 */
@Singleton
class IperfRepository @Inject constructor(
    private val processRunner: IperfProcessRunner,
    private val iperfResultDao: IperfResultDao,
    private val clock: AppClock,
) {
    fun run(sessionId: String, config: IperfConfig): Flow<IperfResult> {
        val testRunId = UUID.randomUUID().toString()
        return processRunner.run(config)
            .mapNotNull { line -> IperfOutputParser.parseLine(config, sessionId, testRunId, clock.nowMs(), line) }
            .onEach { result -> iperfResultDao.insert(result) }
    }
}
