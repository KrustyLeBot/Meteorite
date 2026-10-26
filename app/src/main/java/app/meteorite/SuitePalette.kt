// Generated: the same recipe in every app of the suite (Synchrotron, Griffonote, Ecoutify, Météorite).
package app.meteorite

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Fixed brand colors (never the wallpaper's), from the app accent #2563EB: Material's tonal-spot
 * scheme for surfaces and containers, the accent at full strength for `primary`.
 */
internal val SuiteLightColors = lightColorScheme(
    primary = Color(0xFF004AC6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB3C2F7),
    onPrimaryContainer = Color(0xFF2D3C68),
    inversePrimary = Color(0xFFB4C5FF),
    secondary = Color(0xFF595E72),
    onSecondary = Color(0xFFFAF8FF),
    secondaryContainer = Color(0xFFDDE1F9),
    onSecondaryContainer = Color(0xFF4C5164),
    tertiary = Color(0xFF6D567F),
    onTertiary = Color(0xFFFFF6FE),
    tertiaryContainer = Color(0xFFE6CAFA),
    onTertiaryContainer = Color(0xFF564067),
    background = Color(0xFFFAF8FE),
    onBackground = Color(0xFF30323B),
    surface = Color(0xFFFAF8FE),
    onSurface = Color(0xFF30323B),
    surfaceVariant = Color(0xFFE1E2ED),
    onSurfaceVariant = Color(0xFF5D5F68),
    surfaceTint = Color(0xFF0053DB),
    inverseSurface = Color(0xFF0D0E12),
    inverseOnSurface = Color(0xFF9D9CA2),
    error = Color(0xFFA83836),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFA746F),
    onErrorContainer = Color(0xFF6E0A12),
    outline = Color(0xFF797A84),
    outlineVariant = Color(0xFFB0B1BC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FE),
    surfaceContainer = Color(0xFFEEEDF6),
    surfaceContainerHigh = Color(0xFFE8E7F1),
    surfaceContainerHighest = Color(0xFFE1E2ED),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD9D9E4),
)

internal val SuiteDarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF465174),
    onPrimaryContainer = Color(0xFFDCE2FF),
    inversePrimary = Color(0xFF0053DB),
    secondary = Color(0xFFC1C5DD),
    onSecondary = Color(0xFF3A4052),
    secondaryContainer = Color(0xFF363B4D),
    onSecondaryContainer = Color(0xFFBABED5),
    tertiary = Color(0xFFF1DAFF),
    onTertiary = Color(0xFF5F4971),
    tertiaryContainer = Color(0xFFE6CAFA),
    onTertiaryContainer = Color(0xFF564067),
    background = Color(0xFF0D0E12),
    onBackground = Color(0xFFE4E4F0),
    surface = Color(0xFF0D0E12),
    onSurface = Color(0xFFE4E4F0),
    surfaceVariant = Color(0xFF23252E),
    onSurfaceVariant = Color(0xFFAAAAB5),
    surfaceTint = Color(0xFFB4C5FF),
    inverseSurface = Color(0xFFFAF8FE),
    inverseOnSurface = Color(0xFF54555A),
    error = Color(0xFFFA746F),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF871F21),
    onErrorContainer = Color(0xFFFF9993),
    outline = Color(0xFF74757F),
    outlineVariant = Color(0xFF464750),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2C34),
    surfaceContainer = Color(0xFF181920),
    surfaceContainerHigh = Color(0xFF1E1F26),
    surfaceContainerHighest = Color(0xFF23252E),
    surfaceContainerLow = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceDim = Color(0xFF0D0E12),
)

/** Rounded shapes shared by the whole suite. */
internal val SuiteShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
