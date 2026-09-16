package net.openmanet.perfapp.rpc

import android.content.Context
import android.net.ConnectivityManager
import com.connectrpc.ProtocolClientConfig
import com.connectrpc.extensions.GoogleJavaProtobufStrategy
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import com.connectrpc.protocols.NetworkProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a Connect-RPC client scoped to the phone's currently active network and a specific
 * node IP. openmanetd speaks plain HTTP/2 (h2c, no TLS) on port 8087 with a 30s timeout - see
 * https://openmanet.github.io/docs/openmanetd/protobuf-api - so this forces
 * Protocol.H2_PRIOR_KNOWLEDGE (skip ALPN/TLS negotiation entirely) and ConnectionSpec.CLEARTEXT.
 *
 * The app does not join Wi-Fi itself (the user connects to the mesh SSID via system settings
 * before opening the app), so "the mesh network" is simply whatever network is currently active.
 * The OkHttpClient is still bound to that specific Network via Network.socketFactory - rather
 * than left to route however the OS default network selection picks - so this keeps working
 * correctly if the phone also has cellular active concurrently (e.g. for the separate
 * CloudHttpClient export upload in di/NetworkModule).
 *
 * openmanetd's auth middleware requires a Bearer token on every RPC here except
 * DashboardService.GetDashboardStatus and SetupService's wizard endpoints (confirmed against a
 * real node: 401 {"error":"unauthorized"} without it) - see AuthRepository for how the token is
 * obtained via POST /auth/login. AuthHeaderInterceptor attaches it automatically once
 * SessionTokenHolder has one, so callers don't need to pass it per-request.
 */
@Singleton
class OpenManetClientFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionTokenHolder: SessionTokenHolder,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun create(nodeIp: String, port: Int = DEFAULT_PORT): ProtocolClient {
        val network = connectivityManager.activeNetwork
            ?: error("No active network - join the mesh Wi-Fi before connecting to a node")

        val okHttpClient = OkHttpClient.Builder()
            .socketFactory(network.socketFactory)
            .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
            .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
            .addInterceptor(AuthHeaderInterceptor(sessionTokenHolder))
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        return ProtocolClient(
            httpClient = ConnectOkHttpClient(okHttpClient),
            ProtocolClientConfig(
                host = "http://$nodeIp:$port",
                serializationStrategy = GoogleJavaProtobufStrategy(),
                networkProtocol = NetworkProtocol.CONNECT,
            ),
        )
    }

    companion object {
        const val DEFAULT_PORT = 8087
    }
}
