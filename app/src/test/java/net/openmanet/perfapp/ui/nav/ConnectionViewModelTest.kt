package net.openmanet.perfapp.ui.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.openmanet.perfapp.connectivity.ConnectionState
import net.openmanet.perfapp.connectivity.DefaultGatewayResolver
import net.openmanet.perfapp.connectivity.PendingNode
import net.openmanet.perfapp.core.AppClock
import net.openmanet.perfapp.data.dao.NodeProfileDao
import net.openmanet.perfapp.data.entities.NodeProfile
import net.openmanet.perfapp.rpc.AuthRepository
import net.openmanet.perfapp.rpc.SessionTokenHolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var nodeProfileDao: FakeNodeProfileDao
    private lateinit var authRepository: AuthRepository
    private lateinit var sessionTokenHolder: SessionTokenHolder
    private lateinit var defaultGatewayResolver: DefaultGatewayResolver
    private lateinit var viewModel: ConnectionViewModel

    private val testNode = PendingNode(ip = "10.41.1.1", displayName = "manet01")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        nodeProfileDao = FakeNodeProfileDao()
        authRepository = mock()
        sessionTokenHolder = SessionTokenHolder()
        defaultGatewayResolver = mock()
        whenever(defaultGatewayResolver.currentGatewayAddress()).thenReturn(null)
        viewModel = ConnectionViewModel(
            nodeProfileDao,
            authRepository,
            sessionTokenHolder,
            defaultGatewayResolver,
            FakeAppClock(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isEnteringNodeAddress() {
        assertEquals(ConnectionState.EnteringNodeAddress, viewModel.state.value)
    }

    @Test
    fun connect_movesToConnectingImmediately() {
        viewModel.connect(testNode, "root", "hunter2")
        assertEquals(ConnectionState.Connecting(testNode), viewModel.state.value)
    }

    @Test
    fun connect_success_movesToConnectedAndSavesProfile() = runTest(dispatcher) {
        whenever(authRepository.login(testNode.ip, "root", "hunter2")).thenReturn(Result.success("token-1"))

        viewModel.connect(testNode, "root", "hunter2")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ConnectionState.Connected)
        assertEquals(testNode, (state as ConnectionState.Connected).node)
        assertEquals(testNode.ip, nodeProfileDao.saved.single().ipAddress)
        assertEquals("root", nodeProfileDao.saved.single().lastUsername)
    }

    @Test
    fun connect_failure_movesToErrorWrappingConnecting() = runTest(dispatcher) {
        whenever(authRepository.login(testNode.ip, "root", "wrong"))
            .thenReturn(Result.failure(Exception("invalid credentials")))

        viewModel.connect(testNode, "root", "wrong")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ConnectionState.Error)
        assertEquals(ConnectionState.Connecting(testNode), (state as ConnectionState.Error).previous)
        assertEquals("invalid credentials", state.message)
        assertTrue(nodeProfileDao.saved.isEmpty())
    }

    @Test
    fun retryAfterError_succeeds() = runTest(dispatcher) {
        whenever(authRepository.login(testNode.ip, "root", "wrong"))
            .thenReturn(Result.failure(Exception("invalid credentials")))
        viewModel.connect(testNode, "root", "wrong")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value is ConnectionState.Error)

        whenever(authRepository.login(testNode.ip, "root", "right")).thenReturn(Result.success("token-1"))
        viewModel.connect(testNode, "root", "right")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value is ConnectionState.Connected)
    }

    @Test
    fun disconnect_resetsToEnteringNodeAddressAndClearsToken() = runTest(dispatcher) {
        whenever(authRepository.login(testNode.ip, "root", "hunter2")).thenReturn(Result.success("token-1"))
        viewModel.connect(testNode, "root", "hunter2")
        dispatcher.scheduler.advanceUntilIdle()
        sessionTokenHolder.token = "token-1"

        viewModel.disconnect()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ConnectionState.EnteringNodeAddress, viewModel.state.value)
        assertEquals(null, sessionTokenHolder.token)
    }
}

private class FakeNodeProfileDao : NodeProfileDao {
    val saved = mutableListOf<NodeProfile>()
    private val flow = MutableStateFlow<List<NodeProfile>>(emptyList())

    override fun observeAll(): Flow<List<NodeProfile>> = flow

    override suspend fun getById(ipAddress: String): NodeProfile? = saved.find { it.ipAddress == ipAddress }

    override suspend fun upsert(profile: NodeProfile) {
        saved.removeAll { it.ipAddress == profile.ipAddress }
        saved.add(profile)
        flow.value = saved.toList()
    }

    override suspend fun delete(ipAddress: String) {
        saved.removeAll { it.ipAddress == ipAddress }
        flow.value = saved.toList()
    }
}

private class FakeAppClock : AppClock {
    override fun nowMs(): Long = 42_000L
}
