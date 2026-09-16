package net.openmanet.perfapp.ui.session

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.openmanet.perfapp.session.ActiveSessionHolder
import net.openmanet.perfapp.session.TestSessionManager
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    activeSessionHolder: ActiveSessionHolder,
    private val testSessionManager: TestSessionManager,
) : ViewModel() {

    val activeSessionId: StateFlow<String?> = activeSessionHolder.sessionId

    private val _pingTargets = MutableStateFlow<List<String>>(emptyList())
    val pingTargets: StateFlow<List<String>> = _pingTargets.asStateFlow()

    /** Pre-fills the target list with the connected node's own IP the first time it's known. */
    fun seedTargetIfEmpty(nodeIp: String) {
        if (_pingTargets.value.isEmpty() && nodeIp.isNotBlank()) {
            _pingTargets.value = listOf(nodeIp)
        }
    }

    fun addTarget(host: String) {
        val trimmed = host.trim()
        if (trimmed.isNotBlank() && trimmed !in _pingTargets.value) {
            _pingTargets.value = _pingTargets.value + trimmed
        }
    }

    fun removeTarget(host: String) {
        _pingTargets.value = _pingTargets.value - host
    }

    fun start(nodeId: String) {
        testSessionManager.start(nodeId, _pingTargets.value)
    }

    fun stop() {
        testSessionManager.stop()
    }
}
