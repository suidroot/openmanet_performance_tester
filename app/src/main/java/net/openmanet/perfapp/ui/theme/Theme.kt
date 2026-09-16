package net.openmanet.perfapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF0B3D91)
private val BlueLight = Color(0xFF4B6FB8)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
)

private val LightColors = lightColorScheme(
    primary = Blue,
)

@Composable
fun ManetPerfAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
