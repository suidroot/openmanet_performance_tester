package net.openmanet.perfapp.ui.iperf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.openmanet.perfapp.data.entities.IperfProfile
import net.openmanet.perfapp.iperf.IperfEngine
import net.openmanet.perfapp.iperf.mbpsTextToBitsPerSecond

@Composable
fun IperfProfilesScreen(
    onRunProfile: (profileId: Long) -> Unit,
    onRunAdHoc: () -> Unit,
    viewModel: IperfProfilesViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf(IperfEngine.V3) }
    var port by remember { mutableStateOf(engine.defaultPort.toString()) }
    var durationSeconds by remember { mutableStateOf("10") }
    var protocol by remember { mutableStateOf("TCP") }
    var reverse by remember { mutableStateOf(false) }
    var maxBandwidthMbps by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("IPERF PROFILES") }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (profiles.isEmpty()) {
                Text("No saved profiles yet.", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileRow(
                            profile = profile,
                            onRun = { onRunProfile(profile.id) },
                            onDelete = { viewModel.delete(profile) },
                        )
                        HorizontalDivider()
                    }
                }
            }

            Button(onClick = onRunAdHoc, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Run without saving…")
            }

            HorizontalDivider()

            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("New profile", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Server address") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // OpenManet nodes run iperf2 by default (`iperf`, not `iperf3`) - the two
                    // speak incompatible wire protocols, so this picks which vendored binary and
                    // output parser to use, not just a cosmetic label.
                    FilterChip(
                        selected = engine == IperfEngine.V3,
                        onClick = {
                            if (port == IperfEngine.V2.defaultPort.toString()) port = IperfEngine.V3.defaultPort.toString()
                            engine = IperfEngine.V3
                        },
                        label = { Text("iperf3") },
                    )
                    FilterChip(
                        selected = engine == IperfEngine.V2,
                        onClick = {
                            if (port == IperfEngine.V3.defaultPort.toString()) port = IperfEngine.V2.defaultPort.toString()
                            engine = IperfEngine.V2
                        },
                        label = { Text("iperf2") },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = durationSeconds,
                        onValueChange = { durationSeconds = it },
                        label = { Text("Duration (s)") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = protocol == "TCP", onClick = { protocol = "TCP" }, label = { Text("TCP") })
                    FilterChip(selected = protocol == "UDP", onClick = { protocol = "UDP" }, label = { Text("UDP") })
                    FilterChip(selected = reverse, onClick = { reverse = !reverse }, label = { Text("Reverse") })
                }
                OutlinedTextField(
                    value = maxBandwidthMbps,
                    onValueChange = { maxBandwidthMbps = it },
                    label = { Text("Max bandwidth (Mbit/s, optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        viewModel.save(
                            IperfProfile(
                                name = name,
                                host = host,
                                port = port.toIntOrNull() ?: engine.defaultPort,
                                protocol = protocol,
                                durationSeconds = durationSeconds.toIntOrNull() ?: 10,
                                reverse = reverse,
                                engine = engine.name,
                                maxBitsPerSecond = maxBandwidthMbps.mbpsTextToBitsPerSecond(),
                            ),
                        )
                        name = ""
                        host = ""
                        maxBandwidthMbps = ""
                    },
                    enabled = name.isNotBlank() && host.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save profile")
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: IperfProfile, onRun: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(profile.name) },
        supportingContent = {
            val engineLabel = if (profile.engine == IperfEngine.V2.name) "iperf2" else "iperf3"
            Text(
                "${profile.host}:${profile.port} • $engineLabel • ${profile.protocol} • " +
                    "${profile.durationSeconds}s${if (profile.reverse) " • reverse" else ""}",
            )
        },
        trailingContent = {
            Row {
                Button(onClick = onRun) { Text("Run") }
                Button(onClick = onDelete) { Text("Delete") }
            }
        },
    )
}
