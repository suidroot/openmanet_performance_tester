package net.openmanet.perfapp.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openmanet.perfapp.ping.PingTarget
import net.openmanet.perfapp.rpc.NodeRepository
import net.openmanet.perfapp.session.ActiveSessionHolder
import net.openmanet.perfapp.session.TestSessionManager
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    activeSessionHolder: ActiveSessionHolder,
    private val testSessionManager: TestSessionManager,
    private val nodeRepository: NodeRepository,
) : ViewModel() {

    val activeSessionId: StateFlow<String?> = activeSessionHolder.sessionId

    private val _pingTargets = MutableStateFlow<List<PingTarget>>(emptyList())
    val pingTargets: StateFlow<List<PingTarget>> = _pingTargets.asStateFlow()

    /** Guards against re-triggering when the Dashboard recomposes; a genuinely new connection
     * gets a fresh ConnectionViewModel/SessionViewModel instance anyway. */
    private var autoStarted = false

    /**
     * Starts a test session automatically, pinging only the OpenManet nodes discovered via
     * NodeService (never an arbitrary/manually-entered host) - each target is the node's own
     * hostname paired with its IP address, not a bare address. Safe to call repeatedly (e.g.
     * from a recomposing LaunchedEffect); only the first call after connecting does anything.
     */
    fun autoStart(connectedNodeIp: String) {
        if (autoStarted || activeSessionId.value != null) return
        autoStarted = true
        viewModelScope.launch {
            val discoveredNodes = nodeRepository.listNodes(connectedNodeIp).getOrDefault(emptyList())
            val targets = discoveredNodes
                .map { node -> PingTarget(host = node.ipAddress, label = node.hostname) }
                .ifEmpty { listOf(PingTarget(host = connectedNodeIp, label = connectedNodeIp)) }
            _pingTargets.value = targets
            testSessionManager.start(connectedNodeIp, targets)
        }
    }

    fun stop() {
        testSessionManager.stop()
        autoStarted = false
    }
}
