package net.openmanet.perfapp.iperf

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.data.dao.IperfResultDao
import net.openmanet.perfapp.data.entities.IperfResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs one iperf test (a discrete, user-triggered action - unlike ping/GPS this isn't a
 * continuous background collector) and persists every interval sample plus the final summary
 * row(s) as they arrive, tagging them with the caller-supplied testRunId so a session's
 * iperf_result rows group cleanly by run even across repeated tests against the same target.
 * The caller (IperfSessionService) supplies testRunId rather than this repository generating its
 * own, so it can publish that id to ActiveIperfSessionHolder before the run actually starts
 * producing rows - letting any screen attach to the live result stream via
 * IperfResultDao.observeForTestRun regardless of whether it's the screen that started the run.
 *
 * Copies Room's auto-generated id back onto the emitted result (rather than leaving every result
 * at its default id=0) - IperfScreen's summary-row LazyColumn keys on `it.id`, and a real test
 * commonly reports more than one summary line (e.g. iperf3 TCP forward mode prints both a
 * "sender" and a "receiver" summary), so leaving every emitted row's id at 0 crashed with
 * "Key "0" was already used" the moment two summary rows existed in the same run - confirmed on
 * a real device, right after the native-lib extraction fix let a test actually complete for the
 * first time.
 */
@Singleton
class IperfRepository @Inject constructor(
    private val processRunner: IperfProcessRunner,
    private val iperfResultDao: IperfResultDao,
    private val clock: AppClock,
) {
    fun run(sessionId: String, testRunId: String, config: IperfConfig): Flow<IperfResult> {
        return processRunner.run(config)
            .mapNotNull { line -> IperfOutputParser.parseLine(config, sessionId, testRunId, clock.nowMs(), line) }
            .map { result -> result.copy(id = iperfResultDao.insert(result)) }
    }
}
