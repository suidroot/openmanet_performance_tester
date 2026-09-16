package net.openmanet.perfapp.ui.gps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.openmanet.perfapp.data.dao.GpsFixDao
import net.openmanet.perfapp.data.entities.GpsFix
import javax.inject.Inject

@HiltViewModel
class GpsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    gpsFixDao: GpsFixDao,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    val fixes: StateFlow<List<GpsFix>> = gpsFixDao.observeForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
