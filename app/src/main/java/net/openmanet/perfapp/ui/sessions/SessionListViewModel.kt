package net.openmanet.perfapp.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.openmanet.perfapp.data.dao.TestSessionDao
import net.openmanet.perfapp.data.entities.TestSession
import javax.inject.Inject

@HiltViewModel
class SessionListViewModel @Inject constructor(
    testSessionDao: TestSessionDao,
) : ViewModel() {
    val sessions: StateFlow<List<TestSession>> = testSessionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
