package net.openmanet.perfapp.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.openmanet.perfapp.iperf.IperfConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets UI (ViewModels) observe whether an IperfSessionService run is active without binding to
 * the Service - mirrors ActiveSessionHolder's role for the ping/GPS test session. Exposing the
 * running config too (not just an id) lets any screen show "what's running" without its own
 * separate plumbing back to whichever screen originally started it - the run continues in the
 * service regardless of which screen (if any) is currently observing it.
 */
@Singleton
class ActiveIperfSessionHolder @Inject constructor() {
    private val _testRunId = MutableStateFlow<String?>(null)
    val testRunId: StateFlow<String?> = _testRunId.asStateFlow()

    private val _config = MutableStateFlow<IperfConfig?>(null)
    val config: StateFlow<IperfConfig?> = _config.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    internal fun set(testRunId: String?, config: IperfConfig?) {
        _testRunId.value = testRunId
        _config.value = config
        if (testRunId != null) _lastError.value = null
    }

    internal fun setError(message: String?) {
        _lastError.value = message
    }
}
