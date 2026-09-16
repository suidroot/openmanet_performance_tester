package net.openmanet.perfapp.iperf

enum class IperfProtocol { TCP, UDP }

data class IperfConfig(
    val host: String,
    val port: Int = 5201,
    val protocol: IperfProtocol = IperfProtocol.TCP,
    val durationSeconds: Int = 10,
    /** -R: server sends, client receives - measures downlink instead of uplink. */
    val reverse: Boolean = false,
)
