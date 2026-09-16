package net.openmanet.perfapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Placeholder for Phase 0. Later phases add the cloud-export endpoint URL field here. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Settings placeholder.")
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}
