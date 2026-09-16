package net.openmanet.perfapp.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openmanet.perfapp.ping.PingTarget
import net.openmanet.perfapp.rpc.NodeRepository
import net.openmanet.perfapp.session.ActiveSessionHolder
import net.openmanet.perfapp.session.TestSessionManager
import net.openmanet.perfapp.settings.DisabledNodesRepository
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    activeSessionHolder: ActiveSessionHolder,
    private val testSessionManager: TestSessionManager,
    private val nodeRepository: NodeRepository,
    private val disabledNodesRepository: DisabledNodesRepository,
) : ViewModel() {

    val activeSessionId: StateFlow<String?> = activeSessionHolder.sessionId

    private val _pingTargets = MutableStateFlow<List<PingTarget>>(emptyList())
    val pingTargets: StateFlow<List<PingTarget>> = _pingTargets.asStateFlow()

    /** Base hostnames the user has excluded from test sessions - see DisabledNodesRepository. */
    val disabledHostnames: StateFlow<Set<String>> = disabledNodesRepository.disabledHostnames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Guards against re-triggering when the Dashboard recomposes; a genuinely new connection
     * gets a fresh ConnectionViewModel/SessionViewModel instance anyway. */
    private var autoStarted = false
    private var connectedNodeIp: String? = null

    /**
     * Starts a test session automatically, pinging only the OpenManet nodes discovered via
     * NodeService (never an arbitrary/manually-entered host) and not excluded via
     * setNodeDisabled - each target is the node's own hostname paired with its IP address, not a
     * bare address. Safe to call repeatedly (e.g. from a recomposing LaunchedEffect); only the
     * first call after connecting does anything.
     */
    fun autoStart(connectedNodeIp: String) {
        this.connectedNodeIp = connectedNodeIp
        if (autoStarted || activeSessionId.value != null) return
        autoStarted = true
        viewModelScope.launch {
            val targets = buildPingTargets(connectedNodeIp)
            _pingTargets.value = targets
            testSessionManager.start(connectedNodeIp, targets)
        }
    }

    /**
     * Flips a node's excluded-from-testing state and persists it. If a session is currently
     * running, restarts it immediately with the updated target list so the change takes effect
     * right away rather than only on the next reconnect.
     */
    fun setNodeDisabled(hostname: String, disabled: Boolean) {
        viewModelScope.launch {
            disabledNodesRepository.setDisabled(hostname, disabled)
            val nodeIp = connectedNodeIp
            if (nodeIp != null && activeSessionId.value != null) {
                testSessionManager.stop()
                val targets = buildPingTargets(nodeIp)
                _pingTargets.value = targets
                testSessionManager.start(nodeIp, targets)
            }
        }
    }

    private suspend fun buildPingTargets(connectedNodeIp: String): List<PingTarget> {
        val discoveredNodes = nodeRepository.listNodes(connectedNodeIp).getOrDefault(emptyList())
        if (discoveredNodes.isEmpty()) {
            return listOf(PingTarget(host = connectedNodeIp, label = connectedNodeIp))
        }
        val disabled = disabledNodesRepository.disabledHostnames.first()
        return discoveredNodes
            .filter { it.hostname !in disabled }
            .map { node -> PingTarget(host = node.ipAddress, label = node.hostname) }
    }

    fun stop() {
        testSessionManager.stop()
        autoStarted = false
    }
}
