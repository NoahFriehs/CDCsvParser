package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * M3 color scheme mirroring `res/values/colors.xml` / `values-night`
 * (identity: deep violet primary, teal secondary, amber tertiary,
 * violet-tinted neutrals). Static palette - no dynamic color, matching
 * the decision for the XML theme.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B21B6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9E2FB),
    onPrimaryContainer = Color(0xFF27116B),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFF3EC),
    onSecondaryContainer = Color(0xFF0A4A41),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFDE8CB),
    onTertiaryContainer = Color(0xFF3F2602),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1C1726),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1C1726),
    surfaceVariant = Color(0xFFE6E0EE),
    onSurfaceVariant = Color(0xFF4B4557),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCBBDFB),
    onPrimary = Color(0xFF34126B),
    primaryContainer = Color(0xFF433085),
    onPrimaryContainer = Color(0xFFE9E0FF),
    secondary = Color(0xFF7FDCCE),
    onSecondary = Color(0xFF05443B),
    secondaryContainer = Color(0xFF11554B),
    onSecondaryContainer = Color(0xFFC8F5EC),
    tertiary = Color(0xFFF2BA6C),
    onTertiary = Color(0xFF3F2602),
    tertiaryContainer = Color(0xFF66460F),
    onTertiaryContainer = Color(0xFFFFE4B5),
    background = Color(0xFF121017),
    onBackground = Color(0xFFE6E1EF),
    surface = Color(0xFF121017),
    onSurface = Color(0xFFE6E1EF),
    surfaceVariant = Color(0xFF464053),
    onSurfaceVariant = Color(0xFFC8C2D6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/**
 * App theme for Compose screens. Follows the system dark setting exactly
 * like the XML `Theme.Material3.DayNight` base.
 */
@Composable
fun CdcsvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
