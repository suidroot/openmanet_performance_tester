package net.openmanet.perfapp.ui.iperf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.openmanet.perfapp.data.entities.IperfResult
import net.openmanet.perfapp.iperf.IperfConfig
import net.openmanet.perfapp.iperf.IperfEngine
import net.openmanet.perfapp.iperf.IperfProtocol
import net.openmanet.perfapp.ui.theme.Sparkline

@Composable
fun IperfScreen(viewModel: IperfViewModel = hiltViewModel()) {
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val initialConfig by viewModel.initialConfig.collectAsStateWithLifecycle()

    var engine by remember { mutableStateOf(IperfEngine.V3) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(engine.defaultPort.toString()) }
    var durationSeconds by remember { mutableStateOf("10") }
    var protocol by remember { mutableStateOf(IperfProtocol.TCP) }
    var reverse by remember { mutableStateOf(false) }

    LaunchedEffect(initialConfig) {
        initialConfig?.let { config ->
            engine = config.engine
            host = config.host
            port = config.port.toString()
            durationSeconds = config.durationSeconds.toString()
            protocol = config.protocol
            reverse = config.reverse
        }
    }

    val intervalSamples = samples.filter { !it.isSummary }
    val summaryRows = samples.filter { it.isSummary }

    Scaffold(
        topBar = { TopAppBar(title = { Text("IPERF") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Target host") },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // OpenManet nodes run iperf2 by default, not iperf3 - the two speak
                // incompatible wire protocols, so this picks which vendored binary/parser runs.
                FilterChip(
                    selected = engine == IperfEngine.V3,
                    onClick = {
                        if (port == IperfEngine.V2.defaultPort.toString()) port = IperfEngine.V3.defaultPort.toString()
                        engine = IperfEngine.V3
                    },
                    label = { Text("iperf3") },
                    enabled = !isRunning,
                )
                FilterChip(
                    selected = engine == IperfEngine.V2,
                    onClick = {
                        if (port == IperfEngine.V3.defaultPort.toString()) port = IperfEngine.V2.defaultPort.toString()
                        engine = IperfEngine.V2
                    },
                    label = { Text("iperf2") },
                    enabled = !isRunning,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = durationSeconds,
                    onValueChange = { durationSeconds = it },
                    label = { Text("Duration (s)") },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = protocol == IperfProtocol.TCP,
                    onClick = { protocol = IperfProtocol.TCP },
                    label = { Text("TCP") },
                    enabled = !isRunning,
                )
                FilterChip(
                    selected = protocol == IperfProtocol.UDP,
                    onClick = { protocol = IperfProtocol.UDP },
                    label = { Text("UDP") },
                    enabled = !isRunning,
                )
                FilterChip(
                    selected = reverse,
                    onClick = { reverse = !reverse },
                    label = { Text("Reverse (download)") },
                    enabled = !isRunning,
                )
            }

            Button(
                onClick = {
                    viewModel.start(
                        IperfConfig(
                            host = host,
                            port = port.toIntOrNull() ?: engine.defaultPort,
                            protocol = protocol,
                            durationSeconds = durationSeconds.toIntOrNull() ?: 10,
                            reverse = reverse,
                            engine = engine,
                        ),
                    )
                },
                enabled = !isRunning && host.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isRunning) "Running…" else "Start test")
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (intervalSamples.isNotEmpty()) {
                val current = intervalSamples.last()
                Text(
                    "%.1f Mbit/s".format(current.bitsPerSecond!! / 1_000_000.0),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Sparkline(
                    values = intervalSamples.map { it.bitsPerSecond ?: 0.0 },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                )
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(summaryRows, key = { it.id }) { result -> SummaryRow(result) }
            }
        }
    }
}

@Composable
private fun SummaryRow(result: IperfResult) {
    ListItem(
        headlineContent = { Text("%.1f Mbit/s".format(result.bitsPerSecond!! / 1_000_000.0)) },
        supportingContent = {
            val extra = when {
                result.retransmits != null -> "retransmits: ${result.retransmits}"
                result.jitterMs != null -> "jitter: %.3f ms, lost: %d".format(result.jitterMs, result.lostPackets ?: 0)
                else -> ""
            }
            Text("summary — $extra")
        },
    )
    HorizontalDivider()
}
