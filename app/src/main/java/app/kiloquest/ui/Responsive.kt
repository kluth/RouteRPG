package app.kiloquest.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Breitenklassen wie bei Material (Fenstergroesse, nicht Geraet: gilt auch fuer Faltgeraete und Mehrfensterbetrieb). */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

@Immutable
data class WindowInfo(val widthDp: Int, val heightDp: Int) {
    val width: WidthClass get() = when { widthDp < 600 -> WidthClass.COMPACT; widthDp < 840 -> WidthClass.MEDIUM; else -> WidthClass.EXPANDED }
    val landscape: Boolean get() = widthDp > heightDp
    /** Querformat auf dem Handy: wenig Hoehe, dann Elemente nebeneinander statt uebereinander. */
    val shortHeight: Boolean get() = heightDp < 480
    /** Genug Breite fuer Seitenleisten und zwei Spalten. */
    val wide: Boolean get() = width != WidthClass.COMPACT
    val twoPane: Boolean get() = widthDp >= 720
}

@Composable
fun rememberWindowInfo(): WindowInfo {
    val c = LocalConfiguration.current
    return WindowInfo(c.screenWidthDp, c.screenHeightDp)
}

@Composable
fun isCompactWidth(): Boolean = rememberWindowInfo().width == WidthClass.COMPACT

/** Vollbild-Spalte, die auf Tablets/Querformat nicht ueber die ganze Breite laeuft, sondern zentriert bleibt und scrollt. */
@Composable
fun Modifier.screenColumn(maxWidth: Dp = 640.dp): Modifier =
    this.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth).verticalScroll(rememberScrollState())
