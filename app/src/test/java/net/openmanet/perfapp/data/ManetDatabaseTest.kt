package net.openmanet.perfapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.GpsSource
import net.openmanet.perfapp.data.entities.NodeProfile
import net.openmanet.perfapp.data.entities.PingResult
import net.openmanet.perfapp.data.entities.TestSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManetDatabaseTest {

    private lateinit var db: ManetDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ManetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun nodeProfile_upsertAndObserve_roundTrips() = runBlocking {
        val profile = NodeProfile(
            ipAddress = "10.41.1.1",
            displayName = "manet01",
            lastUsername = "root",
            lastConnectedAtMs = 1_000L,
        )

        db.nodeProfileDao().upsert(profile)

        val observed = db.nodeProfileDao().observeAll().first()
        assertEquals(listOf(profile), observed)
    }

    @Test
    fun nodeProfile_delete_removesRow() = runBlocking {
        val profile = NodeProfile(
            ipAddress = "10.41.1.1",
            displayName = "manet01",
            lastUsername = "root",
            lastConnectedAtMs = 1_000L,
        )
        db.nodeProfileDao().upsert(profile)

        db.nodeProfileDao().delete("10.41.1.1")

        assertNull(db.nodeProfileDao().getById("10.41.1.1"))
    }

    @Test
    fun pingResult_timeRangeQuery_returnsOnlyRowsInRange() = runBlocking {
        val session = TestSession("session-1", nodeId = null, startedAtMs = 0L, endedAtMs = null, label = null)
        db.testSessionDao().insert(session)

        db.pingResultDao().insert(pingAt(timestampMs = 100L))
        db.pingResultDao().insert(pingAt(timestampMs = 200L))
        db.pingResultDao().insert(pingAt(timestampMs = 300L))

        val inRange = db.pingResultDao().getInRange("session-1", fromMs = 150L, toMs = 250L)

        assertEquals(1, inRange.size)
        assertEquals(200L, inRange.first().timestampMs)
    }

    @Test
    fun gpsFix_sourceEnum_roundTripsThroughConverter() = runBlocking {
        val session = TestSession("session-1", nodeId = null, startedAtMs = 0L, endedAtMs = null, label = null)
        db.testSessionDao().insert(session)

        val fix = GpsFix(
            sessionId = "session-1",
            timestampMs = 500L,
            source = GpsSource.COT,
            sourceId = "unit-42",
            lat = 37.7749,
            lon = -122.4194,
            altitudeM = 15.0,
            speedMps = 1.2,
            courseDeg = 90.0,
            fixQuality = "3D",
            rawPayload = "<event/>",
        )
        db.gpsFixDao().insert(fix)

        val stored = db.gpsFixDao().observeForSession("session-1").first().single()
        assertEquals(GpsSource.COT, stored.source)
    }

    private fun pingAt(timestampMs: Long) = PingResult(
        sessionId = "session-1",
        timestampMs = timestampMs,
        targetHost = "10.41.1.2",
        targetLabel = null,
        rttMs = 12.5,
        success = true,
        rawOutputLine = null,
    )
}
