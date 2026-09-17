package net.openmanet.perfapp.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ExportScreen(viewModel: ExportViewModel = hiltViewModel()) {
    val file by viewModel.file.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val endpointUrl by viewModel.endpointUrl.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("EXPORT") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Exports this session's ping/GPS/iperf log as one combined CSV.")

            Button(
                onClick = { viewModel.exportAndShare() },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isBusy) "Exporting…" else "Export…")
            }

            file?.let { Text("Last export: ${it.name}") }
            if (file == null && !isBusy) {
                Text("This session has no recorded data yet.", color = MaterialTheme.colorScheme.error)
            }

            OutlinedTextField(
                value = endpointUrl,
                onValueChange = viewModel::onEndpointUrlChanged,
                label = { Text("Upload URL (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.upload() },
                enabled = !isBusy && uploadState != UploadUiState.Uploading && endpointUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Upload")
            }

            when (val state = uploadState) {
                UploadUiState.Idle -> {}
                UploadUiState.Uploading -> CircularProgressIndicator()
                UploadUiState.Success -> Text("Upload complete", color = MaterialTheme.colorScheme.primary)
                is UploadUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
