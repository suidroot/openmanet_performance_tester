package net.openmanet.perfapp.ui.ping

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.openmanet.perfapp.data.entities.PingResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PingScreen(viewModel: PingViewModel = hiltViewModel()) {
    val results by viewModel.results.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ping (${results.size})") }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(results.asReversed(), key = { it.id }) { result ->
                PingResultRow(result)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PingResultRow(result: PingResult) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    ListItem(
        headlineContent = { Text(result.targetHost) },
        supportingContent = { Text(timeFormat.format(Date(result.timestampMs))) },
        trailingContent = {
            Text(
                text = if (result.success) "${result.rttMs} ms" else "timeout",
                color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
