package app.kiloquest.ui.kids

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kiloquest.game.AgeBand
import kotlin.math.max
import kotlin.math.min

/**
 * Farben und Masse der Kinderansicht je Altersstufe. Marke: Orange (Pin), Gruen (Natur), Violett (Daemmerung); der Stern ist ueberall gleich.
 * Der Akzent ist eine **Flaeche** mit dunklem bzw. hellem Text darauf; als Text/Linie auf hellem Grund dient [text] (hoeherer Kontrast).
 */
@Immutable
data class KidsStyle(
    val band: AgeBand,
    val accent: Color,
    val onAccent: Color,
    /** Text und Linien in Akzentfarbe auf [bg]/Karten (mindestens 4,5:1). */
    val text: Color,
    val container: Color,
    val bg: Color,
    val star: Color,
    val onStar: Color,
    /** Mindesthoehe der Hauptknoepfe. */
    val buttonHeight: Dp,
    /** Mindestkante aller Tipp-Ziele. */
    val target: Dp,
    /** Aufgabentext in sp. */
    val questSp: Int,
)

object KidsPalette {
    private val STAR = Color(0xFFFFD54A)
    private val ON_STAR = Color(0xFF0A2A5C)

    fun style(band: AgeBand, dark: Boolean): KidsStyle = when (band) {
        AgeBand.MINI -> if (!dark) KidsStyle(band, Color(0xFFFF9F43), Color(0xFF1B1F24), Color(0xFFB8420A), Color(0xFFFFE3C7), Color(0xFFFFF1E6), STAR, ON_STAR, 96.dp, 72.dp, 30)
        else KidsStyle(band, Color(0xFFFFB68A), Color(0xFF2A1D14), Color(0xFFFFB68A), Color(0xFF3A281C), Color(0xFF2A1D14), STAR, ON_STAR, 96.dp, 72.dp, 30)
        AgeBand.EXPLORER -> if (!dark) KidsStyle(band, Color(0xFF5FCB8C), Color(0xFF0B2A1A), Color(0xFF1F7A48), Color(0xFFD7F2E1), Color(0xFFEEF6F0), STAR, ON_STAR, 64.dp, 56.dp, 24)
        else KidsStyle(band, Color(0xFF7FD6A4), Color(0xFF0B2A1A), Color(0xFF7FD6A4), Color(0xFF223329), Color(0xFF16201A), STAR, ON_STAR, 64.dp, 56.dp, 24)
        AgeBand.PRO -> if (!dark) KidsStyle(band, Color(0xFF5B3FB0), Color(0xFFFFFFFF), Color(0xFF5B3FB0), Color(0xFFE6DEFA), Color(0xFFF4F1FB), STAR, ON_STAR, 56.dp, 48.dp, 20)
        else KidsStyle(band, Color(0xFFC9BCFF), Color(0xFF1B1030), Color(0xFFC9BCFF), Color(0xFF2A2440), Color(0xFF14171A), STAR, ON_STAR, 56.dp, 48.dp, 20)
    }

    /** Kontrastverhaeltnis nach WCAG 2.x (1..21). */
    fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble() + 0.05
        val lb = b.luminance().toDouble() + 0.05
        return max(la, lb) / min(la, lb)
    }
}

@Composable
fun rememberKidsStyle(band: AgeBand): KidsStyle {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return KidsPalette.style(band, dark)
}
