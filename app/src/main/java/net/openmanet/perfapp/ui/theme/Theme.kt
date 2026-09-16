package net.openmanet.perfapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TerminalColors = darkColorScheme(
    background = TerminalBackground,
    surface = TerminalSurface,
    surfaceVariant = TerminalSurfaceVariant,
    primary = TerminalCyan,
    onPrimary = TerminalBackground,
    secondary = TerminalCyanDim,
    onBackground = TerminalTextPrimary,
    onSurface = TerminalTextPrimary,
    onSurfaceVariant = TerminalTextSecondary,
    outline = TerminalOutline,
    error = TerminalRed,
    onError = TerminalTextPrimary,
)

/** Always dark - this is a field-ops "terminal" aesthetic, not something with a light variant. */
@Composable
fun ManetPerfAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColors,
        typography = TerminalTypography,
        content = content,
    )
}
