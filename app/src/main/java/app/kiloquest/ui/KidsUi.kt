package app.kiloquest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiloquest.R
import app.kiloquest.game.AgeBand
import app.kiloquest.game.Sticker

/** Bild fuer einen Sticker; nicht alle haben schon ein Bild, dann bleibt das Emoji. */
private fun stickerRes(id: String): Int? = when (id) {
    "fox" -> R.drawable.kilo_fox
    "owl" -> R.drawable.sticker_owl
    "moose" -> R.drawable.sticker_moose
    "reindeer" -> R.drawable.sticker_deer
    "bear" -> R.drawable.sticker_bear
    "seal" -> R.drawable.sticker_seal
    "whale" -> R.drawable.sticker_whale
    "hedgehog" -> R.drawable.sticker_hedgehog
    "swan" -> R.drawable.sticker_swan
    else -> null
}

private val Grey = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun StickerImage(sticker: Sticker, collected: Boolean, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val res = stickerRes(sticker.id)
    Box(modifier.size(size).alpha(if (collected) 1f else 0.28f), contentAlignment = Alignment.Center) {
        if (res != null) {
            Image(painterResource(res), contentDescription = null, modifier = Modifier.size(size), colorFilter = if (collected) null else Grey)
        } else {
            Text(if (collected) sticker.emoji else "·", style = TextStyle(fontSize = (size.value * 0.7f).sp))
        }
    }
}

/** Gemeinsames Sticker-Album: gesammelt in Farbe, fehlend grau. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StickerGrid(all: List<Sticker>, collected: Set<String>, size: Dp = 44.dp) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in all) StickerImage(s, s.id in collected, size)
    }
}

/** Hintergrund der Kinderseite je Altersstufe (Mini warm, Entdecker gruen). */
@Composable
fun kidsBackground(band: AgeBand): Color = when (band) {
    AgeBand.MINI -> KiloTheme.colors.kidsBackgroundMini
    AgeBand.EXPLORER -> KiloTheme.colors.kidsBackgroundExplorer
    AgeBand.PRO -> MaterialTheme.colorScheme.surface
}

/** Aufgabentext je Altersstufe: Mini 28-32 sp, Entdecker 22-24 sp, Profi 18-20 sp. */
@Composable
fun questTextStyle(band: AgeBand): TextStyle = when (band) {
    AgeBand.MINI -> MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 36.sp)
    AgeBand.EXPLORER -> MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp, lineHeight = 30.sp)
    AgeBand.PRO -> MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp)
}

/** Kilo mit Sprechblase. */
@Composable
fun KiloSays(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.kilo_fox), contentDescription = null, modifier = Modifier.size(88.dp))
        Surface(
            shape = RoundedCornerShape(24.dp, 24.dp, 24.dp, 6.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 20.sp, lineHeight = 26.sp))
        }
    }
}
