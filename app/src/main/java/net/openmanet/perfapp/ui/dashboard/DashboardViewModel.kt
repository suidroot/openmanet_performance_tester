package net.openmanet.perfapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openmanet.perfapp.rpc.MeshNeighbor
import net.openmanet.perfapp.rpc.MeshNode
import net.openmanet.perfapp.rpc.MeshStatus
import net.openmanet.perfapp.rpc.NeighborRepository
import net.openmanet.perfapp.rpc.NodeRepository
import net.openmanet.perfapp.rpc.StatusRepository
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = false,
    val status: MeshStatus? = null,
    val nodes: List<MeshNode> = emptyList(),
    val neighbors: List<MeshNeighbor> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val neighborRepository: NeighborRepository,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun refresh(nodeIp: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val statusResult = statusRepository.getServiceStatus(nodeIp)
            val nodesResult = nodeRepository.listNodes(nodeIp)
            val neighborsResult = neighborRepository.listMeshNeighbors(nodeIp)

            _uiState.value = DashboardUiState(
                isLoading = false,
                status = statusResult.getOrNull(),
                nodes = nodesResult.getOrDefault(emptyList()),
                neighbors = neighborsResult.getOrDefault(emptyList()),
                error = listOfNotNull(
                    statusResult.exceptionOrNull()?.message,
                    nodesResult.exceptionOrNull()?.message,
                    neighborsResult.exceptionOrNull()?.message,
                ).firstOrNull(),
            )
        }
    }
}
