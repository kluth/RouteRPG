package app.kiloquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiloquest.KidsActions
import app.kiloquest.R
import app.kiloquest.UiState
import app.kiloquest.game.QuestKind
import app.kiloquest.game.StickerBook
import app.kiloquest.questSpeech
import app.kiloquest.stringByKey

private val MiniFont get() = NunitoFamily

private fun miniStyle(size: Int, line: Int = size + 6) = TextStyle(fontFamily = NunitoFamily, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = size.sp, lineHeight = line.sp)

/**
 * Ansicht fuer die Kleinsten (4 bis 6): ein Gedanke pro Bildschirm, riesige Knoepfe, kein Lesen noetig.
 * "Ja!" heisst geschafft/gesehen, "Noch nicht" ueberspringt ohne Folgen. Antwortknoepfe bei Quizfragen sind gross und bunt.
 */
/** Sprachausgabe dieses Geraets (fuer "Vorlesen" auf Kinder-Geraeten); wird beim Verlassen freigegeben. */
@Composable
private fun rememberSpeaker(): (String) -> Unit {
    val ctx = LocalContext.current
    val holder = remember { arrayOfNulls<android.speech.tts.TextToSpeech>(1) }
    val ready = remember { booleanArrayOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        holder[0] = android.speech.tts.TextToSpeech(ctx) { status -> ready[0] = status == android.speech.tts.TextToSpeech.SUCCESS }
        onDispose { holder[0]?.stop(); holder[0]?.shutdown(); holder[0] = null; ready[0] = false }
    }
    return { text -> if (ready[0]) holder[0]?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "kq-mini-read") }
}

@Composable
fun MiniKids(state: UiState, nav: KidsActions) {
    val ctx = LocalContext.current
    val speak = rememberSpeaker()
    val c = KiloTheme.colors
    val q = state.quest
    val fb = state.feedback
    var asked by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = c.kidsBackgroundMini) {
        Column(Modifier.screenColumn().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Kopf: Zurueck und Fortschritt als Punkte (5 Stationen)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(onClick = { nav.showKids(false) }, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, modifier = Modifier.size(64.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.kids_back), modifier = Modifier.size(32.dp))
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.mini_on_the_way), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val total = state.questsTotal.coerceAtLeast(1)
                    val filled = (state.questsDone.coerceAtMost(total) * 5 / total).coerceIn(0, 5)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 0 until 5) {
                            Box(
                                Modifier.size(22.dp).then(
                                    if (i < filled) Modifier.background(c.ageMini, CircleShape)
                                    else Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).border(3.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                ),
                            )
                        }
                    }
                }
                Box(Modifier.size(64.dp))
            }

            when {
                q != null -> {
                    KiloSays(stringResource(R.string.kilo_intro))
                    Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.size(132.dp).background(c.hintContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Text(questEmoji(q), style = TextStyle(fontSize = 72.sp))
                            }
                            Text(ctx.stringByKey(q.textKey, q.args), style = miniStyle(30), textAlign = TextAlign.Center)
                            // Kilo liest vor: Aufgabe und bei Quizfragen die Antworten mit Form und Farbe
                            Surface(
                                onClick = { speak(ctx.questSpeech(q, withOptions = true)) },
                                shape = CircleShape, color = c.hintContainer, contentColor = c.onHintContainer, shadowElevation = 3.dp,
                                modifier = Modifier.size(72.dp).semantics { contentDescription = ctx.getString(R.string.mini_read_aloud) },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.ic_volume_up), contentDescription = null, modifier = Modifier.size(40.dp))
                                }
                            }
                            if (q.kind == QuestKind.CHOICE) {
                                // Kein Lesen noetig: jede Antwort hat Form und Farbe (Kreis, Quadrat, Dreieck)
                                val shapes = listOf(R.drawable.ic_shape_circle, R.drawable.ic_shape_square, R.drawable.ic_shape_triangle)
                                val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
                                q.optionKeys.forEachIndexed { i, key ->
                                    Button(
                                        onClick = { nav.answerQuest(i) },
                                        modifier = Modifier.fillMaxWidth().height(84.dp),
                                        shape = RoundedCornerShape(28.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors[i % 3], contentColor = Color.White),
                                    ) {
                                        Box(Modifier.size(52.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(painterResource(shapes[i % 3]), contentDescription = null, tint = colors[i % 3], modifier = Modifier.size(30.dp))
                                        }
                                        Text(ctx.stringByKey(key), Modifier.padding(start = 14.dp).weight(1f), style = miniStyle(26), textAlign = TextAlign.Start)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { nav.answerQuest() },
                                    modifier = Modifier.fillMaxWidth().height(96.dp),
                                    shape = RoundedCornerShape(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                                ) {
                                    Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, modifier = Modifier.size(48.dp))
                                    Text(stringResource(R.string.mini_yes), Modifier.padding(start = 12.dp), style = miniStyle(40))
                                }
                            }
                            OutlinedButton(
                                onClick = nav::skipQuest,
                                modifier = Modifier.fillMaxWidth().height(72.dp),
                                shape = RoundedCornerShape(28.dp),
                                border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Icon(painterResource(R.drawable.ic_skip_next), contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.mini_not_yet), Modifier.padding(start = 10.dp), style = miniStyle(22), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                fb != null -> {
                    val praise = when {
                        fb.correctOptionKey == null || fb.correct -> stringResource(R.string.mini_great)
                        else -> stringResource(R.string.kids_wrong, ctx.stringByKey(fb.correctOptionKey))
                    }
                    KiloSays(praise)
                    Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            val sticker = state.newSticker
                            if (sticker != null) {
                                StickerImage(sticker, collected = true, size = 140.dp)
                                Text(stringResource(R.string.kids_new_sticker), style = miniStyle(28), textAlign = TextAlign.Center)
                            } else {
                                Text(stringResource(R.string.kids_done, fb.points), style = miniStyle(28), textAlign = TextAlign.Center)
                            }
                            Button(
                                onClick = nav::dismissFeedback,
                                modifier = Modifier.fillMaxWidth().height(96.dp),
                                shape = RoundedCornerShape(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                            ) {
                                Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(48.dp))
                                Text(stringResource(R.string.kids_next), Modifier.padding(start = 12.dp), style = miniStyle(34))
                            }
                        }
                    }
                }
                else -> {
                    KiloSays(stringResource(if (state.route == null && !state.remote) R.string.kids_need_route else R.string.kids_waiting))
                }
            }

            // Unsere Tiere (gemeinsames Album) und "Ich muss mal"
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.mini_animals), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.semantics { contentDescription = "${state.album.size}" },
                    ) {
                        for (s in StickerBook.ALL.take(4)) StickerImage(s, s.id in state.album, size = 44.dp)
                    }
                }
            }
            val alarm = state.toilet?.phase.let { it != null && it != app.kiloquest.ToiletPhase.NONE }
            if (alarm) {
                ToiletKidButton({}, Modifier.fillMaxWidth(), active = true)
            } else if (asked) {
                Text(stringResource(R.string.toilet_kid_sent), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else {
                ToiletKidButton({ asked = true; nav.requestToilet() }, Modifier.fillMaxWidth())
            }
        }
    }
}
