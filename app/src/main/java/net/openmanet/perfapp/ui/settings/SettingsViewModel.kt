package net.openmanet.perfapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openmanet.perfapp.settings.RefreshSettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val refreshSettingsRepository: RefreshSettingsRepository,
) : ViewModel() {

    val refreshIntervalMs: StateFlow<Long> = refreshSettingsRepository.intervalMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RefreshSettingsRepository.DEFAULT_INTERVAL_MS)

    fun setRefreshIntervalMs(intervalMs: Long) {
        viewModelScope.launch { refreshSettingsRepository.setIntervalMs(intervalMs) }
    }
}
