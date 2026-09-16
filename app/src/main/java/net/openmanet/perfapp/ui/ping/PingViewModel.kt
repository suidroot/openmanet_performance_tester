package net.openmanet.perfapp.ui.ping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openmanet.perfapp.data.dao.PingResultDao
import net.openmanet.perfapp.data.entities.PingResult
import net.openmanet.perfapp.rpc.NeighborRepository
import net.openmanet.perfapp.rpc.NodeRepository
import net.openmanet.perfapp.rpc.baseHostname
import javax.inject.Inject

@HiltViewModel
class PingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    pingResultDao: PingResultDao,
    private val nodeRepository: NodeRepository,
    private val neighborRepository: NeighborRepository,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])
    private val nodeIp: String = savedStateHandle["nodeIp"] ?: ""

    val results: StateFlow<List<PingResult>> = pingResultDao.observeForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Target host -> batman-adv-estimated available bandwidth (bit/s), from MeshNeighbor.throughput. */
    private val _expectedThroughputBpsByHost = MutableStateFlow<Map<String, Int>>(emptyMap())
    val expectedThroughputBpsByHost: StateFlow<Map<String, Int>> = _expectedThroughputBpsByHost.asStateFlow()

    init {
        if (nodeIp.isNotBlank()) {
            viewModelScope.launch {
                val hostnameToIp = nodeRepository.listNodes(nodeIp).getOrDefault(emptyList())
                    .associate { it.hostname to it.ipAddress }
                val neighbors = neighborRepository.listMeshNeighbors(nodeIp).getOrDefault(emptyList())

                _expectedThroughputBpsByHost.value = neighbors.mapNotNull { neighbor ->
                    val hostname = neighbor.neighbor.baseHostname()
                    hostnameToIp[hostname]?.let { ip -> ip to neighbor.throughputBps }
                }.toMap()
            }
        }
    }
}
