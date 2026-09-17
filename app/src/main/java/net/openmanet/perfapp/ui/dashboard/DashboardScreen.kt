package net.openmanet.perfapp.ui.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.openmanet.perfapp.connectivity.ConnectionState
import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.GpsSource
import net.openmanet.perfapp.data.entities.PingResult
import net.openmanet.perfapp.ui.nav.ConnectionViewModel
import net.openmanet.perfapp.ui.session.SessionViewModel
import net.openmanet.perfapp.ui.theme.Sparkline
import net.openmanet.perfapp.ui.theme.StatRow
import net.openmanet.perfapp.ui.theme.StatusDot
import net.openmanet.perfapp.ui.theme.TerminalCard
import net.openmanet.perfapp.ui.theme.TerminalGreen
import net.openmanet.perfapp.ui.theme.TerminalOutline
import net.openmanet.perfapp.ui.theme.TerminalTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    connectionViewModel: ConnectionViewModel,
    onOpenGps: (sessionId: String) -> Unit,
    onOpenIperf: (sessionId: String) -> Unit,
    onOpenSessions: () -> Unit,
    onOpenExport: (sessionId: String) -> Unit,
    onOpenSettings: () -> Unit,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()
    val connected = connectionState as? ConnectionState.Connected ?: return
    val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val peerCards by dashboardViewModel.peerCards.collectAsStateWithLifecycle()
    val refreshIntervalMs by dashboardViewModel.refreshIntervalMs.collectAsStateWithLifecycle()
    val latestGpsFix by dashboardViewModel.latestGpsFix.collectAsStateWithLifecycle()
    val activeSessionId by sessionViewModel.activeSessionId.collectAsStateWithLifecycle()
    val pingTargets by sessionViewModel.pingTargets.collectAsStateWithLifecycle()
    val disabledHostnames by sessionViewModel.disabledHostnames.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    // Permissions are requested only when the user actually flips the logging toggle on, not
    // automatically on connect - logging is opt-in now, not implicit.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { sessionViewModel.start(connected.node.ip) }

    LaunchedEffect(connected.node.ip) {
        dashboardViewModel.refresh(connected.node.ip)
    }

    // Auto-refresh on a configurable interval (Settings), restarting the loop whenever the
    // interval changes so an edit takes effect immediately rather than after the next tick.
    // refresh() itself preserves stale data on partial failure and never blanks the UI, so this
    // loop doesn't cause the content to flash/reflow - that was the cause of the "twitch" bug.
    LaunchedEffect(connected.node.ip, refreshIntervalMs) {
        while (isActive) {
            delay(refreshIntervalMs)
            dashboardViewModel.refresh(connected.node.ip)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(connected.node.displayName.uppercase(), style = MaterialTheme.typography.titleLarge)
                        Text(connected.node.ip, style = MaterialTheme.typography.bodySmall, color = TerminalTextSecondary)
                    }
                },
                actions = {
                    Text(
                        "LOG",
                        style = MaterialTheme.typography.labelLarge,
                        color = TerminalTextSecondary,
                    )
                    Switch(
                        checked = activeSessionId != null,
                        onCheckedChange = { checked ->
                            if (checked) {
                                permissionLauncher.launch(sessionPermissions())
                            } else {
                                sessionViewModel.stop()
                            }
                        },
                    )
                    TextButton(onClick = onOpenSettings) { Text("SETTINGS") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DashboardHeader(refreshIntervalMs, uiState.lastUpdatedAtMs)

            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            MeshPeersCard(uiState)
            LinkQualityCard(uiState, refreshIntervalMs)
            NodesSection(
                peers = peerCards,
                disabledHostnames = disabledHostnames,
                pingTargetCount = pingTargets.size,
                onSetDisabled = sessionViewModel::setNodeDisabled,
            )
            IperfCard(onOpenIperf = { onOpenIperf(activeSessionId ?: return@IperfCard) })
            GpsStatusCard(
                fix = latestGpsFix,
                onOpenGps = { onOpenGps(activeSessionId ?: return@GpsStatusCard) },
            )
            ExportDataCard(
                onExport = {
                    coroutineScope.launch {
                        sessionViewModel.exportableSessionId(connected.node.ip)?.let(onOpenExport)
                    }
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { dashboardViewModel.refresh(connected.node.ip) }) { Text("Refresh") }
                Button(onClick = onOpenSessions) { Text("Sessions") }
                Button(
                    onClick = {
                        sessionViewModel.stop()
                        connectionViewModel.disconnect()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Disconnect") }
            }
        }
    }
}

@Composable
private fun DashboardHeader(refreshIntervalMs: Long, lastUpdatedAtMs: Long?) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    Column {
        Text("◇ DASHBOARD", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        val refreshSeconds = refreshIntervalMs / 1000.0
        val lastUpdated = lastUpdatedAtMs?.let { "LAST UPDATE ${timeFormat.format(Date(it))}" } ?: "LOADING…"
        Text(
            "OVERVIEW · LIVE TELEMETRY · ${formatSeconds(refreshSeconds)}S REFRESH · $lastUpdated",
            style = MaterialTheme.typography.bodySmall,
            color = TerminalTextSecondary,
        )
    }
}

@Composable
private fun MeshPeersCard(uiState: DashboardUiState) {
    TerminalCard(title = "Mesh Peers") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${uiState.nodes.size}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                " NODES",
                style = MaterialTheme.typography.titleSmall,
                color = TerminalTextSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        StatRow("Gateway", uiState.gatewayHostname ?: uiState.status?.selectedGatewayMac?.takeIf { it.isNotBlank() } ?: "—")
        StatRow("Hops avg", uiState.averageHops?.let { "%.1f".format(it) } ?: "—")
        StatRow("Throughput", formatBitsPerSecond(uiState.neighbors.sumOf { it.throughputBps.toLong() }))
    }
}

@Composable
private fun LinkQualityCard(uiState: DashboardUiState, refreshIntervalMs: Long) {
    TerminalCard(title = "Link Quality", meta = "${formatSeconds(refreshIntervalMs / 1000.0)}S") {
        val quality = uiState.averageLinkQuality
        Text(
            quality?.let { "%.0f".format(it) } ?: "—",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (uiState.linkQualityHistory.size >= 2) {
            Sparkline(
                values = uiState.linkQualityHistory,
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp),
            )
        }
    }
}

/**
 * The "Nodes" section: an Enabled group (one full card per node - name, live API stats, latest
 * ping result, each with an exclude toggle) and, if any nodes are excluded, a compact Disabled
 * group listing just their names with a switch to bring them back.
 */
@Composable
private fun NodesSection(
    peers: List<NodePeerUiState>,
    disabledHostnames: Set<String>,
    pingTargetCount: Int,
    onSetDisabled: (hostname: String, disabled: Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Nodes", meta = if (pingTargetCount > 0) "Pinging $pingTargetCount" else null)

        if (peers.isEmpty()) {
            TerminalCard(title = "Mesh Peers", meta = "Live") {
                Text("No neighbors discovered yet.", color = TerminalTextSecondary)
            }
            return
        }

        val (disabled, enabled) = peers.partition { it.hostname in disabledHostnames }

        SectionLabel("Enabled", small = true)
        if (enabled.isEmpty()) {
            Text("All nodes excluded.", color = TerminalTextSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                enabled.forEach { peer ->
                    NodePeerCard(peer = peer, onExclude = { onSetDisabled(peer.hostname, true) })
                }
            }
        }

        if (disabled.isNotEmpty()) {
            SectionLabel("Disabled", small = true)
            DisabledNodesCard(disabled, onInclude = { hostname -> onSetDisabled(hostname, false) })
        }
    }
}

@Composable
private fun SectionLabel(text: String, meta: String? = null, small: Boolean = false) {
    Row {
        Text(
            text.uppercase(),
            style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
            color = if (small) TerminalTextSecondary else MaterialTheme.colorScheme.primary,
        )
        if (meta != null) {
            Text(
                "  ·  ${meta.uppercase()}",
                style = MaterialTheme.typography.labelLarge,
                color = TerminalTextSecondary,
            )
        }
    }
}

/**
 * One card per enabled node: name header + exclude toggle, then the node's live API stats, then
 * its most recent ping result - each section divided, matching the field-ops reference layout.
 * Excluding a node persists (DisabledNodesRepository), moves it to the Disabled group, and if a
 * session is currently running, restarts it immediately with the updated target list.
 */
@Composable
private fun NodePeerCard(peer: NodePeerUiState, onExclude: () -> Unit) {
    TerminalCard(title = peer.hostname, meta = if (peer.isGateway) "Gateway" else null) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Include in test session",
                style = MaterialTheme.typography.bodyMedium,
                color = TerminalTextSecondary,
            )
            Switch(checked = true, onCheckedChange = { checked -> if (!checked) onExclude() })
        }

        StatRow("IP address", peer.ipAddress)
        StatRow("Hops", peer.hops?.toString() ?: "—")
        val neighbor = peer.neighbor
        if (neighbor != null) {
            StatRow("Signal", "${neighbor.signalStrength} dBm  ·  quality ${neighbor.signal}")
            StatRow("Throughput", formatBitsPerSecond(neighbor.throughputBps.toLong()))
            StatRow("Interface", neighbor.interfaceName)
        } else {
            Text(
                "Not a direct neighbor (multi-hop).",
                color = TerminalTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = TerminalOutline)

        PingResultRow(peer.latestPing)
    }
}

@Composable
private fun DisabledNodesCard(disabled: List<NodePeerUiState>, onInclude: (hostname: String) -> Unit) {
    TerminalCard(title = "Disabled", meta = "${disabled.size}") {
        disabled.forEachIndexed { index, peer ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = TerminalOutline)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(peer.hostname, style = MaterialTheme.typography.bodyMedium)
                Switch(checked = false, onCheckedChange = { checked -> if (checked) onInclude(peer.hostname) })
            }
        }
    }
}

@Composable
private fun PingResultRow(ping: PingResult?) {
    if (ping == null) {
        Text("No ping data yet.", color = TerminalTextSecondary, style = MaterialTheme.typography.bodySmall)
        return
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(active = ping.success, modifier = Modifier.padding(end = 8.dp))
        Text(
            if (ping.success) "${ping.rttMs?.let { "%.0f".format(it) } ?: "?"} ms" else "TIMEOUT",
            color = if (ping.success) TerminalGreen else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "  ·  ${timeFormat.format(Date(ping.timestampMs))}",
            color = TerminalTextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * iperf (v2 or v3, picked per test/profile - see IperfEngine) is a standalone, user-triggered
 * test, not part of the continuous ping/GPS session - its own card so it doesn't read as gated
 * by (or part of) that session, even though a run still gets tagged with whatever session
 * happens to be active for time-series correlation.
 */
@Composable
private fun IperfCard(onOpenIperf: () -> Unit) {
    TerminalCard(title = "iperf") {
        Text("Standalone throughput test against a configured server.", color = TerminalTextSecondary)
        Button(onClick = onOpenIperf, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Run iperf test")
        }
    }
}

/** Tapping the card opens the full GPS fix history (GpsScreen). */
@Composable
private fun GpsStatusCard(fix: GpsFix?, onOpenGps: () -> Unit) {
    TerminalCard(title = "GPS / GNSS", modifier = Modifier.clickable(onClick = onOpenGps)) {
        if (fix == null) {
            Text("No fix yet.", color = TerminalTextSecondary)
            return@TerminalCard
        }
        val ageMs = System.currentTimeMillis() - fix.timestampMs
        val synced = ageMs < 15_000
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(active = synced, modifier = Modifier.padding(end = 8.dp))
            Text(
                if (synced) "SYNCED" else "STALE (%.0fs ago)".format(ageMs / 1000.0),
                color = if (synced) TerminalGreen else TerminalTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        StatRow("Source", fix.source.label())
        StatRow("Latitude", "%.5f".format(fix.lat))
        StatRow("Longitude", "%.5f".format(fix.lon))
        fix.altitudeM?.let { StatRow("Altitude", "%.1f m".format(it)) }
    }
}

private fun GpsSource.label(): String = when (this) {
    GpsSource.DEVICE -> "Device"
    GpsSource.COT -> "Multicast (CoT)"
    GpsSource.NODE_GNSS -> "Node GNSS"
}

@Composable
private fun ExportDataCard(onExport: () -> Unit) {
    TerminalCard(title = "Export Data") {
        Text("Export this session's ping/GPS/iperf log as one combined CSV.", color = TerminalTextSecondary)
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Open Export")
        }
    }
}

private fun formatSeconds(seconds: Double): String =
    if (seconds == seconds.toLong().toDouble()) seconds.toLong().toString() else "%.1f".format(seconds)

private fun formatBitsPerSecond(bitsPerSecond: Long): String = when {
    bitsPerSecond >= 1_000_000 -> "%.1f Mbps".format(bitsPerSecond / 1_000_000.0)
    bitsPerSecond >= 1_000 -> "%.0f Kbps".format(bitsPerSecond / 1_000.0)
    else -> "$bitsPerSecond bps"
}

private fun sessionPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
