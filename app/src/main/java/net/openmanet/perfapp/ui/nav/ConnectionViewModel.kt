package net.openmanet.perfapp.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openmanet.perfapp.connectivity.ConnectionState
import net.openmanet.perfapp.connectivity.DefaultGatewayResolver
import net.openmanet.perfapp.connectivity.PendingNode
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.data.dao.NodeProfileDao
import net.openmanet.perfapp.data.entities.NodeProfile
import net.openmanet.perfapp.rpc.AuthRepository
import net.openmanet.perfapp.rpc.SessionTokenHolder
import javax.inject.Inject

/**
 * Single shared instance across the connection-flow screens (see ManetNavHost, which obtains it
 * once at the NavHost's own scope so it survives navigating between screens). The app never
 * touches Wi-Fi - the user joins the mesh SSID themselves via system settings before opening the
 * app - so "connecting" here means logging into openmanetd's real auth endpoint (POST
 * /auth/login, PAM-backed - the same credentials as the device's OpenWrt/LuCI admin login) and
 * capturing the Bearer token every other API call needs. See rpc/AuthRepository.
 */
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val nodeProfileDao: NodeProfileDao,
    private val authRepository: AuthRepository,
    private val sessionTokenHolder: SessionTokenHolder,
    private val defaultGatewayResolver: DefaultGatewayResolver,
    private val clock: AppClock,
) : ViewModel() {

    val savedNodes: StateFlow<List<NodeProfile>> = nodeProfileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.EnteringNodeAddress)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Best-effort prefill for the node-address field; null if no active network/route yet. */
    fun suggestedNodeAddress(): String? = defaultGatewayResolver.currentGatewayAddress()

    fun connect(node: PendingNode, username: String, password: String) {
        _state.value = ConnectionState.Connecting(node)
        viewModelScope.launch {
            authRepository.login(node.ip, username, password).fold(
                onSuccess = {
                    _state.value = ConnectionState.Connected(node)
                    nodeProfileDao.upsert(
                        NodeProfile(
                            ipAddress = node.ip,
                            displayName = node.displayName,
                            lastUsername = username,
                            lastConnectedAtMs = clock.nowMs(),
                        ),
                    )
                },
                onFailure = { e ->
                    _state.value = ConnectionState.Error(
                        ConnectionState.Connecting(node),
                        e.message ?: "Could not reach node at ${node.ip}",
                    )
                },
            )
        }
    }

    fun disconnect() {
        val nodeIp = (_state.value as? ConnectionState.Connected)?.node?.ip
        if (nodeIp != null) {
            viewModelScope.launch { authRepository.logout(nodeIp) }
        }
        sessionTokenHolder.token = null
        _state.value = ConnectionState.EnteringNodeAddress
    }
}
