package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
 * Expressive corner scale (2026 look), hand-rolled on the stable 1.4
 * surface where the expressive tokens are internal-only. It carries the two
 * raised tiers of the expressive scale - large 20 dp (was the 16 dp M3
 * default) and extraLarge 32 dp (was 28 dp) - on the standard slots, with
 * the small tiers unchanged. The top expressive tier (xxlarge 48 dp) has no
 * 1.4 API slot; it is [HeroCardCorners], applied to the overview's metric
 * cards - the app's single dominant surface.
 */
private val ExpressiveShapes = Shapes(
    // The small tiers keep the 1.4 baseline (4/8/12 dp); only the two
    // raised expressive tiers move.
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** The xxlarge (48 dp) tier: the overview metric cards only. */
val HeroCardCorners = RoundedCornerShape(48.dp)

/**
 * Hand-rolled emphasized type layer (2026 look): the expressive type scale's
 * emphasized styles are 1.5-line internal symbols, so on the stable 1.4
 * surface they are plain [TextStyle] values. Applied ONLY to the hero
 * numbers (the overview metric cards); body and list text stay on the
 * baseline [MaterialTheme.typography]. Weight-based emphasis per the locked
 * type ruling (Roboto, no bundled variable font).
 */
val HeroValueStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.3).sp,
)

val CardValueStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 21.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.2).sp,
)

/**
 * App theme for Compose screens. Follows the system dark setting exactly
 * like the XML `Theme.Material3.DayNight` base. Colors are the locked brand
 * identity ([LightColors]/[DarkColors]); [shapes] is the expressive scale.
 */
@Composable
fun CdcsvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = ExpressiveShapes,
        content = content,
    )
}

/**
 * P/L line color for the parse chart (a deliberate green outside the
 * identity palette); the dark variant is brightened so the line stays
 * readable on the dark surface.
 */
@Composable
fun chartPlColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF16A34A)
