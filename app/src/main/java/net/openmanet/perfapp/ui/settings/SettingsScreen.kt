package net.openmanet.perfapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import net.openmanet.perfapp.data.entities.GpsSource

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val refreshIntervalMs by viewModel.refreshIntervalMs.collectAsStateWithLifecycle()
    val preferredGpsSource by viewModel.preferredGpsSource.collectAsStateWithLifecycle()
    val isClearingHistory by viewModel.isClearingHistory.collectAsStateWithLifecycle()
    val historyCleared by viewModel.historyCleared.collectAsStateWithLifecycle()
    var refreshIntervalSecondsText by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(refreshIntervalMs) {
        refreshIntervalSecondsText = (refreshIntervalMs / 1000.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear session history?") },
            text = { Text("Deletes every recorded session - ping, GPS, neighbor, and iperf3 data. This can't be undone. Saved node addresses and iperf profiles are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearSessionHistory()
                    },
                ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SETTINGS") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Dashboard refresh interval (seconds)")
            OutlinedTextField(
                value = refreshIntervalSecondsText,
                onValueChange = { text ->
                    refreshIntervalSecondsText = text
                    text.toDoubleOrNull()?.let { seconds ->
                        viewModel.setRefreshIntervalMs((seconds * 1000).toLong())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Preferred GPS source", modifier = Modifier.padding(top = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = preferredGpsSource == GpsSource.DEVICE,
                    onClick = { viewModel.setPreferredGpsSource(GpsSource.DEVICE) },
                    label = { Text("Device") },
                )
                FilterChip(
                    selected = preferredGpsSource == GpsSource.COT,
                    onClick = { viewModel.setPreferredGpsSource(GpsSource.COT) },
                    label = { Text("Multicast (CoT)") },
                )
            }

            Text("Session history", modifier = Modifier.padding(top = 16.dp))
            Button(
                onClick = { showClearConfirm = true },
                enabled = !isClearingHistory,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isClearingHistory) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(if (isClearingHistory) "Clearing…" else "Clear session history")
            }
            if (historyCleared) {
                Text(
                    "Session history cleared.",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text("Back")
            }
        }
    }
}
