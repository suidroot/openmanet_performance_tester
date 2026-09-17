package net.openmanet.perfapp.ui.iperf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openmanet.perfapp.data.dao.IperfProfileDao
import net.openmanet.perfapp.data.entities.IperfResult
import net.openmanet.perfapp.iperf.IperfConfig
import net.openmanet.perfapp.iperf.IperfEngine
import net.openmanet.perfapp.iperf.IperfProtocol
import net.openmanet.perfapp.iperf.IperfRepository
import javax.inject.Inject

@HiltViewModel
class IperfViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val iperfRepository: IperfRepository,
    private val iperfProfileDao: IperfProfileDao,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])
    private val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _samples = MutableStateFlow<List<IperfResult>>(emptyList())
    val samples: StateFlow<List<IperfResult>> = _samples.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Non-null once a saved profile has loaded, for the screen to seed its form fields from. */
    private val _initialConfig = MutableStateFlow<IperfConfig?>(null)
    val initialConfig: StateFlow<IperfConfig?> = _initialConfig.asStateFlow()

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
                    )
                }
            }
        }
    }

    fun start(config: IperfConfig) {
        if (_isRunning.value) return
        _samples.value = emptyList()
        _error.value = null
        _isRunning.value = true
        viewModelScope.launch {
            try {
                iperfRepository.run(sessionId, config).collect { result ->
                    _samples.value = _samples.value + result
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "iperf failed"
            } finally {
                _isRunning.value = false
            }
        }
    }
}
