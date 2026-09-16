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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.openmanet.perfapp.connectivity.ConnectionState
import net.openmanet.perfapp.ui.nav.ConnectionViewModel
import net.openmanet.perfapp.ui.session.SessionViewModel

@Composable
fun DashboardScreen(
    connectionViewModel: ConnectionViewModel,
    onOpenPing: (sessionId: String) -> Unit,
    onOpenGps: (sessionId: String) -> Unit,
    onOpenIperf: (sessionId: String) -> Unit,
    onOpenSessions: () -> Unit,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()
    val connected = connectionState as? ConnectionState.Connected ?: return
    val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val activeSessionId by sessionViewModel.activeSessionId.collectAsStateWithLifecycle()
    val pingTargets by sessionViewModel.pingTargets.collectAsStateWithLifecycle()

    LaunchedEffect(connected.node.ip) {
        dashboardViewModel.refresh(connected.node.ip)
        sessionViewModel.seedTargetIfEmpty(connected.node.ip)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { sessionViewModel.start(connected.node.ip) }

    var newTarget by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text(connected.node.displayName) }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Node IP: ${connected.node.ip}")

            if (uiState.isLoading) {
                CircularProgressIndicator()
            }
            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            uiState.status?.let { status ->
                Text(
                    "Status: ${if (status.isConnected) "connected" else "disconnected"} — " +
                        "${status.connectedNeighbors} neighbors, gateway=${status.isMeshGateway}",
                )
            }

            Text("Neighbors (${uiState.neighbors.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(uiState.neighbors, key = { it.neighbor }) { neighbor ->
                    ListItem(
                        headlineContent = { Text(neighbor.neighbor) },
                        supportingContent = {
                            Text("signal=${neighbor.signal} rssi=${neighbor.signalStrength}dBm via ${neighbor.interfaceName}")
                        },
                    )
                    HorizontalDivider()
                }
            }

            Row {
                Button(onClick = { dashboardViewModel.refresh(connected.node.ip) }) { Text("Refresh") }
                Button(onClick = { onOpenSessions() }) { Text("Sessions") }
                Button(onClick = { connectionViewModel.disconnect() }) { Text("Disconnect") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("Test session", style = MaterialTheme.typography.titleMedium)

            if (activeSessionId == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTarget,
                        onValueChange = { newTarget = it },
                        label = { Text("Ping target IP") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        sessionViewModel.addTarget(newTarget)
                        newTarget = ""
                    }) { Text("Add") }
                }
                Column {
                    pingTargets.forEach { target ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(target)
                            Button(onClick = { sessionViewModel.removeTarget(target) }) { Text("Remove") }
                        }
                    }
                }
                Button(
                    onClick = { permissionLauncher.launch(sessionPermissions()) },
                    enabled = pingTargets.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start session")
                }
            } else {
                Text("Session running: $activeSessionId")
                Row {
                    Button(onClick = { onOpenPing(activeSessionId!!) }) { Text("Ping") }
                    Button(onClick = { onOpenGps(activeSessionId!!) }) { Text("GPS") }
                    Button(onClick = { onOpenIperf(activeSessionId!!) }) { Text("iperf3") }
                    Button(onClick = { sessionViewModel.stop() }) { Text("Stop session") }
                }
            }
        }
    }
}

private fun sessionPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
