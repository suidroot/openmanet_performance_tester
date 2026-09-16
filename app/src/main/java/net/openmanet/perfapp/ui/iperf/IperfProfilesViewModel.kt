package net.openmanet.perfapp.ui.iperf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openmanet.perfapp.data.dao.IperfProfileDao
import net.openmanet.perfapp.data.entities.IperfProfile
import javax.inject.Inject

@HiltViewModel
class IperfProfilesViewModel @Inject constructor(
    private val iperfProfileDao: IperfProfileDao,
) : ViewModel() {

    val profiles: StateFlow<List<IperfProfile>> = iperfProfileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(profile: IperfProfile) {
        viewModelScope.launch { iperfProfileDao.insert(profile) }
    }

    fun delete(profile: IperfProfile) {
        viewModelScope.launch { iperfProfileDao.delete(profile) }
    }
}
