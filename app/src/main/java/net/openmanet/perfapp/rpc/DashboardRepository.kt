package net.openmanet.perfapp.rpc

import com.google.protobuf.Empty
import com.openmanet.dashboard.v1.DashboardServiceClient
import com.openmanet.dashboard.v1.NetworkInterfaceState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DashboardService.GetDashboardStatus is explicitly exempt from openmanetd's auth middleware
 * (see AuthHeaderInterceptor's kdoc / OpenMANET/openmanetd internal/auth/middleware.go), but the
 * interceptor attaches the header unconditionally when a token is present, which is harmless -
 * the server just ignores it on this path.
 */
@Singleton
class DashboardRepository @Inject constructor(
    private val clientFactory: OpenManetClientFactory,
) {
    suspend fun getDashboardStatus(nodeIp: String): Result<DashboardStatus> {
        val client = DashboardServiceClient(clientFactory.create(nodeIp))
        return client.getDashboardStatus(Empty.getDefaultInstance()).toResult().map { response ->
            val info = response.deviceInfo
            val resources = response.systemResources
            DashboardStatus(
                deviceInfo = DeviceInfo(
                    hostname = info.hostname,
                    model = info.model,
                    firmware = info.firmware,
                    kernel = info.kernel,
                    architecture = info.architecture,
                ),
                systemResources = SystemResources(
                    uptimeSeconds = resources.uptime.seconds,
                    cpuLoadPercent = resources.cpuLoadPercent,
                    memoryTotalBytes = resources.memoryTotalBytes,
                    memoryUsedBytes = resources.memoryUsedBytes,
                    overlayTotalBytes = resources.overlayTotalBytes,
                    overlayUsedBytes = resources.overlayUsedBytes,
                    cpuTempCelsius = resources.cpuTempCelsius,
                ),
                networkInterfaces = response.networkSummary.entriesList.map { entry ->
                    NetworkSummaryEntry(
                        interfaceName = entry.interfaceName,
                        displayName = entry.displayName,
                        isConnected = entry.state == NetworkInterfaceState.NETWORK_INTERFACE_STATE_CONNECTED,
                        detail = entry.detail,
                        rxBytes = entry.rxBytes,
                        txBytes = entry.txBytes,
                    )
                },
            )
        }
    }
}
