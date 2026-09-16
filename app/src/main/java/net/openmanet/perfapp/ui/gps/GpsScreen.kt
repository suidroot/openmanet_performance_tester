package net.openmanet.perfapp.ui.gps

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.openmanet.perfapp.data.entities.GpsFix
import net.openmanet.perfapp.data.entities.GpsSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GpsScreen(viewModel: GpsViewModel = hiltViewModel()) {
    val fixes by viewModel.fixes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("GPS fixes (${fixes.size})") }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(fixes.asReversed(), key = { it.id }) { fix ->
                GpsFixRow(fix)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun GpsFixRow(fix: GpsFix) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val sourceLabel = when (fix.source) {
        GpsSource.DEVICE -> "device"
        GpsSource.COT -> "CoT — ${fix.sourceId}"
        GpsSource.NODE_GNSS -> "node GNSS"
    }
    ListItem(
        headlineContent = { Text("%.5f, %.5f".format(fix.lat, fix.lon)) },
        supportingContent = { Text("$sourceLabel — ${timeFormat.format(Date(fix.timestampMs))}") },
        modifier = Modifier.fillMaxWidth(),
    )
}
