package net.openmanet.perfapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A bordered panel matching the reference dashboard's card style: dark surface, thin outline,
 * an uppercase title (optionally with a trailing meta string like "5M" or a live sample count). */
@Composable
fun TerminalCard(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(TerminalSurface)
            .border(1.dp, TerminalOutline, RoundedCornerShape(4.dp))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, color = TerminalTextSecondary)
            if (meta != null) {
                Text(
                    "  ·  ${meta.uppercase()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TerminalTextSecondary,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = TerminalOutline)
        content()
    }
}

/** A "LABEL .......... value" row, matching the reference design's key/value rows. */
@Composable
fun StatRow(label: String, value: String, valueColor: Color = TerminalTextPrimary) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.bodyMedium, color = TerminalTextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** A thin horizontal progress bar with a value label, for CPU/MEM/OVERLAY-style rows. */
@Composable
fun ProgressStatRow(label: String, fraction: Float, valueText: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label.uppercase(), style = MaterialTheme.typography.bodyMedium, color = TerminalTextSecondary)
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = TerminalTextPrimary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            color = TerminalCyan,
            trackColor = TerminalOutline,
        )
    }
}

/** A small colored status dot, e.g. for interface/connection state. */
@Composable
fun StatusDot(active: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (active) TerminalGreen else TerminalTextSecondary),
    )
}
