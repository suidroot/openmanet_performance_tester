package net.openmanet.perfapp.ui.ping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.openmanet.perfapp.data.dao.PingResultDao
import net.openmanet.perfapp.data.entities.PingResult
import javax.inject.Inject

@HiltViewModel
class PingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    pingResultDao: PingResultDao,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    val results: StateFlow<List<PingResult>> = pingResultDao.observeForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
