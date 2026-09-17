package net.openmanet.perfapp.ui.iperf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openmanet.perfapp.data.dao.IperfProfileDao
import net.openmanet.perfapp.data.dao.IperfResultDao
import net.openmanet.perfapp.data.entities.IperfResult
import net.openmanet.perfapp.iperf.IperfConfig
import net.openmanet.perfapp.iperf.IperfEngine
import net.openmanet.perfapp.iperf.IperfProtocol
import net.openmanet.perfapp.session.ActiveIperfSessionHolder
import net.openmanet.perfapp.session.IperfSessionManager
import javax.inject.Inject

/**
 * Starts/stops the run via IperfSessionService rather than running it directly in
 * viewModelScope, and observes live results/state through ActiveIperfSessionHolder + Room rather
 * than collecting the repository's Flow itself - both survive this ViewModel (and its
 * NavBackStackEntry) being destroyed, e.g. by navigating back to the dashboard while a test is
 * still running.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IperfViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val iperfSessionManager: IperfSessionManager,
    private val activeIperfSessionHolder: ActiveIperfSessionHolder,
    iperfResultDao: IperfResultDao,
    private val iperfProfileDao: IperfProfileDao,
) : ViewModel() {
    private val nodeIp: String = checkNotNull(savedStateHandle["nodeIp"])
    private val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L

    val isRunning: StateFlow<Boolean> = activeIperfSessionHolder.testRunId
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val samples: StateFlow<List<IperfResult>> = activeIperfSessionHolder.testRunId
        .flatMapLatest { testRunId ->
            if (testRunId == null) flowOf(emptyList()) else iperfResultDao.observeForTestRun(testRunId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val error: StateFlow<String?> = activeIperfSessionHolder.lastError

    /** Non-null once a saved profile has loaded, for the screen to seed its form fields from. */
    private val _initialConfig = MutableStateFlow<IperfConfig?>(null)
    val initialConfig: StateFlow<IperfConfig?> = _initialConfig

    init {
        if (profileId >= 0) {
            viewModelScope.launch {
                iperfProfileDao.getById(profileId)?.let { profile ->
                    _initialConfig.value = IperfConfig(
                        host = profile.host,
                        port = profile.port,
                        protocol = if (profile.protocol == "UDP") IperfProtocol.UDP else IperfProtocol.TCP,
                        durationSeconds = profile.durationSeconds,
                        reverse = profile.reverse,
                        engine = if (profile.engine == IperfEngine.V2.name) IperfEngine.V2 else IperfEngine.V3,
                        maxBitsPerSecond = profile.maxBitsPerSecond,
                    )
                }
            }
        }
    }

    fun start(config: IperfConfig) {
        if (isRunning.value) return
        iperfSessionManager.start(nodeIp, config)
    }

    fun stop() {
        iperfSessionManager.stop()
    }
}
