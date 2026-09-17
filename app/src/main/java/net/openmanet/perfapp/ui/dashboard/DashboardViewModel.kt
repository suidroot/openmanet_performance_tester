package net.openmanet.perfapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.data.dao.GpsFixDao
import net.openmanet.perfapp.data.dao.PingResultDao
import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.PingResult
import net.openmanet.perfapp.rpc.MeshNeighbor
import net.openmanet.perfapp.rpc.MeshNode
import net.openmanet.perfapp.rpc.MeshStatus
import net.openmanet.perfapp.rpc.MeshTopologyRepository
import net.openmanet.perfapp.rpc.NeighborRepository
import net.openmanet.perfapp.rpc.NodeRepository
import net.openmanet.perfapp.rpc.StatusRepository
import net.openmanet.perfapp.rpc.baseHostname
import net.openmanet.perfapp.session.ActiveSessionHolder
import net.openmanet.perfapp.settings.GpsPreferenceRepository
import net.openmanet.perfapp.settings.RefreshSettingsRepository
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val status: MeshStatus? = null,
    val nodes: List<MeshNode> = emptyList(),
    val neighbors: List<MeshNeighbor> = emptyList(),
    val error: String? = null,
    val lastUpdatedAtMs: Long? = null,
    /** neighbor hostname -> forwarding hops from the connected node, from MeshTopologyService. */
    val hopsByHostname: Map<String, Int> = emptyMap(),
    val gatewayHostname: String? = null,
    val selfHostname: String? = null,
    /** Recent averageLinkQuality samples, oldest first, capped at LINK_QUALITY_HISTORY_SIZE -
     * drawn as a line graph rather than showing a single instantaneous number. */
    val linkQualityHistory: List<Double> = emptyList(),
) {
    val averageHops: Double?
        get() {
            val values = neighbors.mapNotNull { hopsByHostname[it.neighbor.baseHostname()] }
            return if (values.isEmpty()) null else values.average()
        }

    /** Average of MeshNeighbor.signal across current neighbors. The proto documents `signal` as
     * "signal quality to the neighbor node" (distinct from the dBm `signal_strength` field)
     * without pinning a scale, and on real hardware it reads as a negative, dBm-like number, not
     * a 0-100 percent - so this is an estimate in whatever unit the node actually reports, not a
     * calibrated percentage. */
    val averageLinkQuality: Double?
        get() = neighbors.map { it.signal }.takeIf { it.isNotEmpty() }?.average()
}

/** One discovered OpenMANET node (never self), combining its NodeService identity with whatever
 * live data is available for it: hop count/gateway role from MeshTopologyService, direct-link
 * stats from MeshNeighborService (null if it's a multi-hop node, not a direct neighbor), and the
 * most recent ping result from the active test session (null if none yet). Drives one card per
 * node on the dashboard. */
data class NodePeerUiState(
    val hostname: String,
    val ipAddress: String,
    val isGateway: Boolean,
    val hops: Int?,
    val neighbor: MeshNeighbor?,
    val latestPing: PingResult?,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val neighborRepository: NeighborRepository,
    private val statusRepository: StatusRepository,
    private val meshTopologyRepository: MeshTopologyRepository,
    private val gpsFixDao: GpsFixDao,
    private val pingResultDao: PingResultDao,
    private val activeSessionHolder: ActiveSessionHolder,
    private val refreshSettingsRepository: RefreshSettingsRepository,
    private val gpsPreferenceRepository: GpsPreferenceRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val refreshIntervalMs: StateFlow<Long> = refreshSettingsRepository.intervalMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RefreshSettingsRepository.DEFAULT_INTERVAL_MS)

    /** Most recent GPS fix for whichever session is currently active. Prefers the user's chosen
     * source (Settings - device GPS or the mesh's CoT multicast feed) if it has produced a fix;
     * falls back to whichever source is actually available otherwise, rather than showing
     * nothing just because the preferred source hasn't reported yet. */
    val latestGpsFix: StateFlow<GpsFix?> = combine(
        activeSessionHolder.sessionId.flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList()) else gpsFixDao.observeForSession(sessionId)
        },
        gpsPreferenceRepository.preferredSource,
    ) { fixes, preferred ->
        fixes.lastOrNull { it.source == preferred } ?: fixes.lastOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Most recent ping result per target host for whichever session is currently active. */
    private val latestPingByHost: StateFlow<Map<String, PingResult>> = activeSessionHolder.sessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList()) else pingResultDao.observeForSession(sessionId)
        }
        .map { results -> results.groupBy { it.targetHost }.mapValues { (_, v) -> v.maxBy { it.timestampMs } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** One card's worth of data per discovered node (self excluded), combining identity, live
     * topology/neighbor stats and the latest ping result - see NodePeerUiState. */
    val peerCards: StateFlow<List<NodePeerUiState>> = combine(uiState, latestPingByHost) { state, pingByHost ->
        val neighborsByHostname = state.neighbors.associateBy { it.neighbor.baseHostname() }
        state.nodes
            .filter { it.hostname != state.selfHostname }
            .map { node ->
                NodePeerUiState(
                    hostname = node.hostname,
                    ipAddress = node.ipAddress,
                    isGateway = node.hostname == state.gatewayHostname,
                    hops = state.hopsByHostname[node.hostname],
                    neighbor = neighborsByHostname[node.hostname],
                    latestPing = pingByHost[node.ipAddress],
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Refreshes dashboard data in place: isLoading flips on/off around the fetch (for a subtle
     * indicator), but the previously-loaded fields are only replaced once the new data has
     * actually arrived, so the UI never blanks out or reflows between ticks - that was the
     * cause of the visible "twitch" on every auto-refresh.
     */
    fun refresh(nodeIp: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val statusResult = statusRepository.getServiceStatus(nodeIp)
            val nodesResult = nodeRepository.listNodes(nodeIp)
            val neighborsResult = neighborRepository.listMeshNeighbors(nodeIp)
            val topologyResult = meshTopologyRepository.getMeshTopology(nodeIp)

            val topology = topologyResult.getOrNull()
            val hopsByHostname = topology?.nodes?.associate { it.hostname to it.hopsFromSelf }.orEmpty()
            val gatewayHostname = topology?.nodes?.firstOrNull { it.isGateway }?.hostname
            val neighbors = neighborsResult.getOrNull() ?: _uiState.value.neighbors
            val newQuality = neighbors.map { it.signal }.takeIf { it.isNotEmpty() }?.average()
            val linkQualityHistory = if (newQuality != null) {
                (_uiState.value.linkQualityHistory + newQuality).takeLast(LINK_QUALITY_HISTORY_SIZE)
            } else {
                _uiState.value.linkQualityHistory
            }

            _uiState.value = DashboardUiState(
                isLoading = false,
                hasLoadedOnce = true,
                status = statusResult.getOrNull() ?: _uiState.value.status,
                nodes = nodesResult.getOrNull() ?: _uiState.value.nodes,
                neighbors = neighbors,
                hopsByHostname = hopsByHostname.ifEmpty { _uiState.value.hopsByHostname },
                gatewayHostname = gatewayHostname ?: _uiState.value.gatewayHostname,
                selfHostname = topology?.selfHostname?.takeIf { it.isNotBlank() } ?: _uiState.value.selfHostname,
                linkQualityHistory = linkQualityHistory,
                error = listOfNotNull(
                    statusResult.exceptionOrNull()?.message,
                    nodesResult.exceptionOrNull()?.message,
                    neighborsResult.exceptionOrNull()?.message,
                ).firstOrNull(),
                lastUpdatedAtMs = clock.nowMs(),
            )
        }
    }

    companion object {
        private const val LINK_QUALITY_HISTORY_SIZE = 30
    }
}
