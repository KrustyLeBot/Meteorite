package app.meteorite

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0B2A66),
    secondary = Color(0xFF475A7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EAF6),
    onSecondaryContainer = Color(0xFF1A2740),
    background = Color(0xFFF3F7FF),
    onBackground = Color(0xFF0F1B2D),
    surface = Color.White,
    onSurface = Color(0xFF0F1B2D),
    surfaceVariant = Color(0xFFE3EAF6),
    onSurfaceVariant = Color(0xFF5B6B82),
    outline = Color(0xFFB3BFD3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF062A66),
    primaryContainer = Color(0xFF1D3A6E),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFFB4C5E4),
    onSecondary = Color(0xFF1A2740),
    secondaryContainer = Color(0xFF2A3A55),
    onSecondaryContainer = Color(0xFFE3EAF6),
    background = Color(0xFF0E1621),
    onBackground = Color(0xFFEAF1FB),
    surface = Color(0xFF182433),
    onSurface = Color(0xFFEAF1FB),
    surfaceVariant = Color(0xFF2A3A55),
    onSurfaceVariant = Color(0xFF9DB0C8),
    outline = Color(0xFF5B6B82),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun MeteoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
