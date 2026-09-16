package net.openmanet.perfapp.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets UI (ViewModels) observe whether a TestSessionService run is active without binding to
 * the Service - both live in the same process, so a shared singleton is simpler than a bound
 * Service connection for what's just "is a session running, and if so which one."
 */
@Singleton
class ActiveSessionHolder @Inject constructor() {
    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    internal fun set(sessionId: String?) {
        _sessionId.value = sessionId
    }
}
