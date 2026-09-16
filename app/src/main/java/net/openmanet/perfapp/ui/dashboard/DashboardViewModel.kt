package net.openmanet.perfapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.data.dao.GpsFixDao
import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.GpsSource
import net.openmanet.perfapp.rpc.MeshNeighbor
import net.openmanet.perfapp.rpc.MeshNode
import net.openmanet.perfapp.rpc.MeshStatus
import net.openmanet.perfapp.rpc.MeshTopologyRepository
import net.openmanet.perfapp.rpc.NeighborRepository
import net.openmanet.perfapp.rpc.NodeRepository
import net.openmanet.perfapp.rpc.StatusRepository
import net.openmanet.perfapp.session.ActiveSessionHolder
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
) {
    val averageHops: Double?
        get() {
            val values = neighbors.mapNotNull { hopsByHostname[it.neighbor.substringBefore(".")] }
            return if (values.isEmpty()) null else values.average()
        }

    /** Average of MeshNeighbor.signal across current neighbors, as an approximate link-quality
     * percentage - the proto documents `signal` as "signal quality to the neighbor node"
     * (distinct from the dBm `signal_strength` field) without pinning an exact 0-100 scale, so
     * this is presented as an estimate, not a precise calibrated percentage. */
    val averageLinkQualityPercent: Double?
        get() = neighbors.map { it.signal }.takeIf { it.isNotEmpty() }?.average()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val neighborRepository: NeighborRepository,
    private val statusRepository: StatusRepository,
    private val meshTopologyRepository: MeshTopologyRepository,
    private val gpsFixDao: GpsFixDao,
    private val activeSessionHolder: ActiveSessionHolder,
    private val refreshSettingsRepository: RefreshSettingsRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val refreshIntervalMs: StateFlow<Long> = refreshSettingsRepository.intervalMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RefreshSettingsRepository.DEFAULT_INTERVAL_MS)

    /** Most recent device-GPS fix for whichever session is currently active, or null if there's
     * no active session yet or it hasn't produced a fix. */
    val latestDeviceGpsFix: StateFlow<GpsFix?> = activeSessionHolder.sessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList()) else gpsFixDao.observeForSession(sessionId)
        }
        .map { fixes -> fixes.lastOrNull { it.source == GpsSource.DEVICE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

            _uiState.value = DashboardUiState(
                isLoading = false,
                hasLoadedOnce = true,
                status = statusResult.getOrNull() ?: _uiState.value.status,
                nodes = nodesResult.getOrNull() ?: _uiState.value.nodes,
                neighbors = neighborsResult.getOrNull() ?: _uiState.value.neighbors,
                hopsByHostname = hopsByHostname.ifEmpty { _uiState.value.hopsByHostname },
                gatewayHostname = gatewayHostname ?: _uiState.value.gatewayHostname,
                error = listOfNotNull(
                    statusResult.exceptionOrNull()?.message,
                    nodesResult.exceptionOrNull()?.message,
                    neighborsResult.exceptionOrNull()?.message,
                ).firstOrNull(),
                lastUpdatedAtMs = clock.nowMs(),
            )
        }
    }
}
