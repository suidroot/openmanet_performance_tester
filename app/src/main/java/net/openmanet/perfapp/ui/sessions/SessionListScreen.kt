package net.openmanet.perfapp.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionListScreen(
    onOpenSession: (sessionId: String) -> Unit,
    viewModel: SessionListViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("SESSIONS") }) },
    ) { padding ->
        if (sessions.isEmpty()) {
            Text(
                "No test sessions yet. Sessions are recorded while connected to a node.",
                modifier = Modifier.padding(padding).padding(24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(sessions, key = { it.session.sessionId }) { summary ->
                    SessionRow(summary, onClick = { onOpenSession(summary.session.sessionId) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SessionRow(summary: SessionSummary, onClick: () -> Unit) {
    val session = summary.session
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    ListItem(
        headlineContent = { Text(dateFormat.format(Date(session.startedAtMs))) },
        supportingContent = {
            val status = if (session.endedAtMs == null) "running" else "ended ${dateFormat.format(Date(session.endedAtMs))}"
            Text(
                "$status\n${summary.pingCount} ping · ${summary.gpsCount} GPS · ${summary.iperfCount} iperf" +
                    if (summary.isEmpty) " (no data)" else "",
            )
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
