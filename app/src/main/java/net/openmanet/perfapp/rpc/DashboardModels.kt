package net.openmanet.perfapp.rpc

data class DeviceInfo(
    val hostname: String,
    val model: String,
    val firmware: String,
    val kernel: String,
    val architecture: String,
)

data class SystemResources(
    val uptimeSeconds: Long,
    val cpuLoadPercent: Float,
    val memoryTotalBytes: Long,
    val memoryUsedBytes: Long,
    val overlayTotalBytes: Long,
    val overlayUsedBytes: Long,
    val cpuTempCelsius: Float,
)

data class NetworkSummaryEntry(
    val interfaceName: String,
    val displayName: String,
    val isConnected: Boolean,
    val detail: String,
    val rxBytes: Long,
    val txBytes: Long,
)

data class DashboardStatus(
    val deviceInfo: DeviceInfo,
    val systemResources: SystemResources,
    val networkInterfaces: List<NetworkSummaryEntry>,
)
