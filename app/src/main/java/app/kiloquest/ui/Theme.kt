package app.kiloquest.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiloquest.R

/** Fahrer-nahe Schrift: Atkinson Hyperlegible (OFL). */
val AtkinsonFamily = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
/** Kinder-Überschriften: Nunito (OFL, variable Schrift, API 26+). */
val NunitoFamily = FontFamily(
    Font(R.font.nunito_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

/** Zusatzfarben, die Material 3 nicht kennt (Verkehr, Wetter, Ziel, Alters-Akzente). */
@Immutable
data class KiloColors(
    val route: Color,
    val destination: Color,
    val breakStop: Color,
    val trafficLight: Color,
    val trafficHeavy: Color,
    val closure: Color,
    val weather: Color,
    val speedRing: Color,
    val navBanner: Color,
    val onNavBanner: Color,
    val hintContainer: Color,
    val onHintContainer: Color,
    val ageMini: Color,
    val ageExplorer: Color,
    val agePro: Color,
    val kidsBackgroundMini: Color,
    val kidsBackgroundExplorer: Color,
)

val LightKiloColors = KiloColors(
    route = Color(0xFF0B57D0), destination = Color(0xFFC2410C), breakStop = Color(0xFF0F766E),
    trafficLight = Color(0xFFB45309), trafficHeavy = Color(0xFFB3261E), closure = Color(0xFF7A1020),
    weather = Color(0xFF6A1B9A), speedRing = Color(0xFFB3261E),
    navBanner = Color(0xFF0A2A5C), onNavBanner = Color(0xFFFFFFFF),
    hintContainer = Color(0xFFFFE3BD), onHintContainer = Color(0xFF5C2E00),
    ageMini = Color(0xFFE8590C), ageExplorer = Color(0xFF2E9B5E), agePro = Color(0xFF6A4CC2),
    kidsBackgroundMini = Color(0xFFFFF1E6), kidsBackgroundExplorer = Color(0xFFF2F6F1),
)

val DarkKiloColors = KiloColors(
    route = Color(0xFF7FB2FF), destination = Color(0xFFFF9A4D), breakStop = Color(0xFF4DD6C4),
    trafficLight = Color(0xFFFFC24D), trafficHeavy = Color(0xFFFF6B5E), closure = Color(0xFFFF8A9A),
    weather = Color(0xFFD8A8FF), speedRing = Color(0xFFFF8A80),
    navBanner = Color(0xFF12233F), onNavBanner = Color(0xFFE6E8EC),
    hintContainer = Color(0xFF4A3500), onHintContainer = Color(0xFFFFC24D),
    ageMini = Color(0xFFFFB68A), ageExplorer = Color(0xFF7FD6A4), agePro = Color(0xFFC9BCFF),
    kidsBackgroundMini = Color(0xFF2A1D14), kidsBackgroundExplorer = Color(0xFF16201A),
)

val LocalKiloColors = staticCompositionLocalOf { LightKiloColors }

/** Zugriff: `KiloTheme.colors.trafficHeavy`. */
object KiloTheme {
    val colors: KiloColors
        @Composable get() = LocalKiloColors.current
}

internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B57D0), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF0A2A5C),
    secondary = Color(0xFF0F766E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFEFE9), onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFFC2410C), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE3BD), onTertiaryContainer = Color(0xFF5C2E00),
    background = Color(0xFFF7F5F0), onBackground = Color(0xFF1B1F24),
    surface = Color(0xFFFBF9F4), onSurface = Color(0xFF1B1F24),
    surfaceVariant = Color(0xFFE6E2DA), onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outlineVariant = Color(0xFFD5D1C7),
    surfaceTint = Color.Transparent,
)

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF9CCAFF), onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF12233F), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF4DD6C4), onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0F4F49), onSecondaryContainer = Color(0xFFCFEFE9),
    tertiary = Color(0xFFFFB68A), onTertiary = Color(0xFF4A1A00),
    tertiaryContainer = Color(0xFF4A3500), onTertiaryContainer = Color(0xFFFFE3BD),
    background = Color(0xFF14171A), onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF1B1F24), onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF2B3036), onSurfaceVariant = Color(0xFFC4C7CF),
    outline = Color(0xFF8D9199),
    error = Color(0xFFFFB4AB), onError = Color(0xFF601410),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outlineVariant = Color(0xFF3A4048),
    surfaceTint = Color.Transparent,
)

private fun style(size: Int, line: Int, bold: Boolean = false, family: FontFamily = AtkinsonFamily) = TextStyle(
    fontFamily = family,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = line.sp,
)

/** Größen aus dem Designentwurf (alles in sp, skaliert mit Systemschrift). */
val KiloTypography = Typography(
    displayLarge = style(40, 44, bold = true),      // Abbiege-Distanz
    displayMedium = style(36, 40, bold = true),
    headlineLarge = style(30, 36, bold = true, family = NunitoFamily),   // Kinder-Aufgabe
    headlineMedium = style(24, 30, bold = true),    // Straße
    headlineSmall = style(22, 28, bold = true),
    titleLarge = style(22, 28, bold = true),
    titleMedium = style(19, 24, bold = true),       // Hinweis-Banner
    titleSmall = style(17, 22, bold = true),
    bodyLarge = style(16, 22),
    bodyMedium = style(15, 21),
    bodySmall = style(13, 18),
    labelLarge = style(17, 22, bold = true),
    labelMedium = style(15, 20, bold = true),
    labelSmall = style(13, 16, bold = true),
)

/** Einheitliche, weiche Ecken (Karten 20-24, Knoepfe 16-18). */
val KiloShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun KiloQuestTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalKiloColors provides if (dark) DarkKiloColors else LightKiloColors,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = KiloTypography,
            shapes = KiloShapes,
            content = content,
        )
    }
}
