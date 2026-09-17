package net.openmanet.perfapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openmanet.perfapp.data.SessionHistoryRepository
import net.openmanet.perfapp.data.entities.GpsSource
import net.openmanet.perfapp.session.TestSessionManager
import net.openmanet.perfapp.settings.GpsPreferenceRepository
import net.openmanet.perfapp.settings.RefreshSettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val refreshSettingsRepository: RefreshSettingsRepository,
    private val gpsPreferenceRepository: GpsPreferenceRepository,
    private val sessionHistoryRepository: SessionHistoryRepository,
    private val testSessionManager: TestSessionManager,
) : ViewModel() {

    val refreshIntervalMs: StateFlow<Long> = refreshSettingsRepository.intervalMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RefreshSettingsRepository.DEFAULT_INTERVAL_MS)

    val preferredGpsSource: StateFlow<GpsSource> = gpsPreferenceRepository.preferredSource
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GpsSource.DEVICE)

    private val _isClearingHistory = MutableStateFlow(false)
    val isClearingHistory: StateFlow<Boolean> = _isClearingHistory.asStateFlow()

    private val _historyCleared = MutableStateFlow(false)
    val historyCleared: StateFlow<Boolean> = _historyCleared.asStateFlow()

    fun setRefreshIntervalMs(intervalMs: Long) {
        viewModelScope.launch { refreshSettingsRepository.setIntervalMs(intervalMs) }
    }

    fun setPreferredGpsSource(source: GpsSource) {
        viewModelScope.launch { gpsPreferenceRepository.setPreferredSource(source) }
    }

    /** Stops any currently-running session first - otherwise it would keep writing new rows
     * tagged with a sessionId this just deleted, making "cleared" not actually mean cleared. */
    fun clearSessionHistory() {
        viewModelScope.launch {
            _isClearingHistory.value = true
            testSessionManager.stop()
            sessionHistoryRepository.clearAll()
            _isClearingHistory.value = false
            _historyCleared.value = true
        }
    }

    fun dismissHistoryClearedNotice() {
        _historyCleared.value = false
    }
}
