package net.openmanet.perfapp.ui.session

import android.util.Log
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
import net.openmanet.perfapp.data.dao.TestSessionDao
import net.openmanet.perfapp.iperf.IperfConfig
import net.openmanet.perfapp.ping.PingTarget
import net.openmanet.perfapp.rpc.NodeRepository
import net.openmanet.perfapp.session.ActiveIperfSessionHolder
import net.openmanet.perfapp.session.ActiveSessionHolder
import net.openmanet.perfapp.session.IperfSessionManager
import net.openmanet.perfapp.session.TestSessionManager
import net.openmanet.perfapp.settings.DisabledNodesRepository
import javax.inject.Inject

private const val TAG = "SessionViewModel"

@HiltViewModel
class SessionViewModel @Inject constructor(
    activeSessionHolder: ActiveSessionHolder,
    private val testSessionManager: TestSessionManager,
    private val nodeRepository: NodeRepository,
    private val disabledNodesRepository: DisabledNodesRepository,
    private val testSessionDao: TestSessionDao,
    activeIperfSessionHolder: ActiveIperfSessionHolder,
    private val iperfSessionManager: IperfSessionManager,
) : ViewModel() {

    val activeSessionId: StateFlow<String?> = activeSessionHolder.sessionId

    /** These three just forward IperfSessionService's state/control - iperf is a fully
     * independent, standalone test (see IperfCard), not part of this ping/GPS session; exposed
     * here purely so the Dashboard (which already holds a SessionViewModel) doesn't need a
     * second ViewModel just to show iperf status/Stop next to everything else. */
    val iperfRunningConfig: StateFlow<IperfConfig?> = activeIperfSessionHolder.config
    fun stopIperf() = iperfSessionManager.stop()

    private val _pingTargets = MutableStateFlow<List<PingTarget>>(emptyList())
    val pingTargets: StateFlow<List<PingTarget>> = _pingTargets.asStateFlow()

    /** Base hostnames the user has excluded from test sessions - see DisabledNodesRepository. */
    val disabledHostnames: StateFlow<Set<String>> = disabledNodesRepository.disabledHostnames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Guards against a double-start if the logging toggle is tapped again before the coroutine
     * below finishes; a genuinely new connection gets a fresh SessionViewModel instance anyway.
     * Reset on any failure too (see start()) - otherwise a single failed attempt (a slow/failed
     * NodeService RPC, say) would latch this true forever and silently block every later retry,
     * since the toggle's checked state only reflects activeSessionId, not this flag. */
    private var started = false
    private var connectedNodeIp: String? = null

    /**
     * Starts a test session - user-triggered via the dashboard's logging toggle, not automatic on
     * connect - pinging only the OpenManet nodes discovered via NodeService (never an arbitrary/
     * manually-entered host) and not excluded via setNodeDisabled - each target is the node's own
     * hostname paired with its IP address, not a bare address.
     */
    fun start(connectedNodeIp: String) {
        this.connectedNodeIp = connectedNodeIp
        if (started || activeSessionId.value != null) return
        started = true
        viewModelScope.launch {
            try {
                val targets = buildPingTargets(connectedNodeIp)
                _pingTargets.value = targets
                testSessionManager.start(connectedNodeIp, targets)
                Log.i(TAG, "Requested session start against $connectedNodeIp with ${targets.size} target(s)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start test session against $connectedNodeIp", e)
                started = false
            }
        }
    }

    /** The session to export: whichever is currently active, or failing that, the most recently
     * recorded session for this node - so "Export Data" still works right after logging is
     * stopped, not only while it's running. Null only if nothing has ever been logged for it. */
    suspend fun exportableSessionId(connectedNodeIp: String): String? =
        activeSessionId.value ?: testSessionDao.getMostRecentForNode(connectedNodeIp)?.sessionId

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
        started = false
    }
}
