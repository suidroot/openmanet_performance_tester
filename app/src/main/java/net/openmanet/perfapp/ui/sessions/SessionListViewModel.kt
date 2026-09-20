package net.openmanet.perfapp.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.openmanet.perfapp.data.dao.GpsFixDao
import net.openmanet.perfapp.data.dao.IperfResultDao
import net.openmanet.perfapp.data.dao.PingResultDao
import net.openmanet.perfapp.data.dao.TestSessionDao
import net.openmanet.perfapp.data.entities.TestSession
import javax.inject.Inject

data class SessionSummary(
    val session: TestSession,
    val pingCount: Int,
    val gpsCount: Int,
    val iperfCount: Int,
) {
    val isEmpty: Boolean get() = pingCount + gpsCount + iperfCount == 0
}

@HiltViewModel
class SessionListViewModel @Inject constructor(
    testSessionDao: TestSessionDao,
    pingResultDao: PingResultDao,
    gpsFixDao: GpsFixDao,
    iperfResultDao: IperfResultDao,
) : ViewModel() {
    val sessions: StateFlow<List<SessionSummary>> = testSessionDao.observeAll()
        .map { list ->
            list.map { s ->
                SessionSummary(
                    session = s,
                    pingCount = pingResultDao.countForSession(s.sessionId),
                    gpsCount = gpsFixDao.countForSession(s.sessionId),
                    iperfCount = iperfResultDao.countForSession(s.sessionId),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
