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
    val expectedThroughputByHost by viewModel.expectedThroughputBpsByHost.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("PING (${results.size})") }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(results.asReversed(), key = { it.id }) { result ->
                PingResultRow(result, expectedThroughputByHost[result.targetHost])
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PingResultRow(result: PingResult, expectedThroughputBps: Int?) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val headline = result.targetLabel?.let { "$it (${result.targetHost})" } ?: result.targetHost
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = {
            val expected = expectedThroughputBps?.let { " — expected %.1f Mbit/s".format(it / 1_000_000.0) }.orEmpty()
            Text(timeFormat.format(Date(result.timestampMs)) + expected)
        },
        trailingContent = {
            Text(
                text = if (result.success) "${result.rttMs} ms" else "timeout",
                color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
    )
}
