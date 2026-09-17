package net.openmanet.perfapp.iperf

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.data.dao.IperfResultDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class IperfRepositoryTest {

    /**
     * Regression test for a real on-device crash: every emitted IperfResult was left at its
     * default id=0 (the DAO's auto-generated id was discarded after insert), and IperfScreen's
     * summary-row LazyColumn keys on `it.id` - the instant a test produced two summary rows
     * sharing that key (routine for iperf3 TCP forward mode, which reports both a "sender" and a
     * "receiver" summary line), Compose crashed with "Key "0" was already used".
     */
    @Test
    fun run_assignsDaoGeneratedIdToEachEmittedResult() = runTest {
        val processRunner: IperfProcessRunner = mock()
        val dao: IperfResultDao = mock()
        val clock: AppClock = mock()
        whenever(clock.nowMs()).thenReturn(1_000L)
        whenever(processRunner.run(any())).thenReturn(
            flowOf(
                "[  5]   0.00-10.00  sec   112 MBytes  94.0 Mbits/sec  15             sender",
                "[  5]   0.00-10.00  sec   111 MBytes  93.2 Mbits/sec                  receiver",
            ),
        )
        whenever(dao.insert(any())).thenReturn(42L, 43L)

        val repository = IperfRepository(processRunner, dao, clock)
        val results = repository.run("session-1", "run-1", IperfConfig(host = "10.41.1.2")).toList()

        assertEquals(2, results.size)
        assertEquals(listOf(42L, 43L), results.map { it.id })
        assertNotEquals(results[0].id, results[1].id)
    }
}
