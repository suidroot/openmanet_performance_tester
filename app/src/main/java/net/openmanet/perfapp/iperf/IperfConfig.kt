package net.openmanet.perfapp.iperf

enum class IperfProtocol { TCP, UDP }

/**
 * Which iperf client to run. iperf2 and iperf3 are separately-maintained projects with
 * incompatible wire protocols - a v3 client cannot talk to a v2 server or vice versa - so the
 * app vendors both binaries (see scripts/build_iperf2.sh, build_iperf3.sh) and the user picks
 * per test/profile. Confirmed necessary against a real OpenManet node, which runs `iperf` (v2),
 * not `iperf3`; the app originally only bundled v3 and every test against a real node failed
 * with the server logging "read tcp test info failed" - the two sides were speaking different
 * protocols, not hitting a routing or permissions problem.
 */
enum class IperfEngine {
    V2,
    V3,
    ;

    /** Each engine's conventional default port - iperf2 and iperf3 don't share one. */
    val defaultPort: Int get() = if (this == V2) 5001 else 5201
}

data class IperfConfig(
    val host: String,
    val port: Int = 5201,
    val protocol: IperfProtocol = IperfProtocol.TCP,
    val durationSeconds: Int = 10,
    /** -R: server sends, client receives - measures downlink instead of uplink. */
    val reverse: Boolean = false,
    val engine: IperfEngine = IperfEngine.V3,
)
