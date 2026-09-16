package net.openmanet.perfapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val refreshIntervalMs by viewModel.refreshIntervalMs.collectAsStateWithLifecycle()
    var refreshIntervalSecondsText by remember { mutableStateOf("") }

    LaunchedEffect(refreshIntervalMs) {
        refreshIntervalSecondsText = (refreshIntervalMs / 1000.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
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
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}
