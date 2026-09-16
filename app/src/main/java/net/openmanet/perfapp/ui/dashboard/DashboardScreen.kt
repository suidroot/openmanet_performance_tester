package net.openmanet.perfapp.ui.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.openmanet.perfapp.connectivity.ConnectionState
import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.ping.PingTarget
import net.openmanet.perfapp.rpc.MeshNeighbor
import net.openmanet.perfapp.ui.nav.ConnectionViewModel
import net.openmanet.perfapp.ui.session.SessionViewModel
import net.openmanet.perfapp.ui.theme.StatRow
import net.openmanet.perfapp.ui.theme.StatusDot
import net.openmanet.perfapp.ui.theme.TerminalCard
import net.openmanet.perfapp.ui.theme.TerminalCyan
import net.openmanet.perfapp.ui.theme.TerminalGreen
import net.openmanet.perfapp.ui.theme.TerminalTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    connectionViewModel: ConnectionViewModel,
    onOpenPing: (sessionId: String, nodeIp: String) -> Unit,
    onOpenGps: (sessionId: String) -> Unit,
    onOpenIperf: (sessionId: String) -> Unit,
    onOpenSessions: () -> Unit,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()
    val connected = connectionState as? ConnectionState.Connected ?: return
    val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val refreshIntervalMs by dashboardViewModel.refreshIntervalMs.collectAsStateWithLifecycle()
    val latestGpsFix by dashboardViewModel.latestDeviceGpsFix.collectAsStateWithLifecycle()
    val activeSessionId by sessionViewModel.activeSessionId.collectAsStateWithLifecycle()
    val pingTargets by sessionViewModel.pingTargets.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { sessionViewModel.autoStart(connected.node.ip) }

    LaunchedEffect(connected.node.ip) {
        dashboardViewModel.refresh(connected.node.ip)
        permissionLauncher.launch(sessionPermissions())
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
            MeshPeersLiveCard(uiState)
            GpsStatusCard(latestGpsFix)
            TestSessionCard(
                activeSessionId = activeSessionId,
                pingTargets = pingTargets,
                onOpenPing = { onOpenPing(activeSessionId ?: return@TestSessionCard, connected.node.ip) },
                onOpenGps = { onOpenGps(activeSessionId ?: return@TestSessionCard) },
                onOpenIperf = { onOpenIperf(activeSessionId ?: return@TestSessionCard) },
                onStop = { sessionViewModel.stop() },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { dashboardViewModel.refresh(connected.node.ip) }) { Text("Refresh") }
                Button(onClick = onOpenSessions) { Text("Sessions") }
                Button(
                    onClick = { connectionViewModel.disconnect() },
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
        val quality = uiState.averageLinkQualityPercent
        Text(
            quality?.let { "%.0f".format(it) } ?: "—",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(
            progress = { ((quality ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = TerminalCyan,
            trackColor = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun MeshPeersLiveCard(uiState: DashboardUiState) {
    TerminalCard(title = "Mesh Peers", meta = "Live") {
        if (uiState.neighbors.isEmpty()) {
            Text("No neighbors discovered yet.", color = TerminalTextSecondary)
        }
        uiState.neighbors.forEach { neighbor -> NeighborRow(neighbor, uiState) }
    }
}

@Composable
private fun NeighborRow(neighbor: MeshNeighbor, uiState: DashboardUiState) {
    val hostname = neighbor.neighbor.substringBefore(".")
    val hops = uiState.hopsByHostname[hostname]
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(neighbor.neighbor, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${neighbor.hardwareAddress}  ·  hops=${hops ?: "?"}  ·  " +
                "${formatBitsPerSecond(neighbor.throughputBps.toLong())}  ·  " +
                "rssi=${neighbor.signalStrength}dBm  ·  signal=${neighbor.signal}",
            style = MaterialTheme.typography.bodySmall,
            color = TerminalTextSecondary,
        )
    }
}

@Composable
private fun GpsStatusCard(fix: GpsFix?) {
    TerminalCard(title = "GPS / GNSS") {
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
        StatRow("Latitude", "%.5f".format(fix.lat))
        StatRow("Longitude", "%.5f".format(fix.lon))
        fix.altitudeM?.let { StatRow("Altitude", "%.1f m".format(it)) }
    }
}

@Composable
private fun TestSessionCard(
    activeSessionId: String?,
    pingTargets: List<PingTarget>,
    onOpenPing: () -> Unit,
    onOpenGps: () -> Unit,
    onOpenIperf: () -> Unit,
    onStop: () -> Unit,
) {
    TerminalCard(title = "Test Session") {
        if (activeSessionId == null) {
            Text("Starting session against ${pingTargets.size.coerceAtLeast(1)} target(s)…", color = TerminalTextSecondary)
        } else {
            val targetList = pingTargets.joinToString { "${it.label} (${it.host})" }
            Text("Pinging ${pingTargets.size} target(s): $targetList", color = TerminalTextSecondary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Button(onClick = onOpenPing) { Text("Ping") }
                Button(onClick = onOpenGps) { Text("GPS") }
                Button(onClick = onOpenIperf) { Text("iperf3") }
                Button(onClick = onStop) { Text("Stop") }
            }
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
