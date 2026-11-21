package app.kiloquest.ui.kids

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiloquest.R
import app.kiloquest.game.Sticker
import app.kiloquest.ui.NunitoFamily
import app.kiloquest.ui.StickerImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Kinder tippen oft doppelt: Klicks innerhalb von [gapMs] nach dem letzten (oder nach dem Zustandswechsel) zaehlen nicht. */
class Debouncer(private var last: Long = System.currentTimeMillis()) {
    fun run(gapMs: Long = 400, block: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - last >= gapMs) { last = now; block() }
    }
}

@Composable
fun rememberDebouncer(key: Any?): Debouncer = remember(key) { Debouncer() }

/** Text- oder Symbolfarbe mit dem besseren Kontrast auf [bg]. */
fun onColorFor(bg: Color): Color = if (bg.luminance() > 0.35f) Color(0xFF1B1F24) else Color.White

fun kidsText(sp: Int, weight: FontWeight = FontWeight.ExtraBold) = TextStyle(fontFamily = NunitoFamily, fontWeight = weight, fontSize = sp.sp, lineHeight = (sp * 1.25f).sp)

enum class ButtonKind { PRIMARY, SECONDARY, SOFT }

/**
 * Grosser Knopf fuer Kinder: Mindesthoehe je Altersstufe, runde Ecken, sanftes Eindruecken (nicht bei ruhiger Ansicht).
 * [leading]: eigenes Symbol (z. B. Form beim Quiz). Klicks sind entprellt ([debouncer]).
 */
@Composable
fun BigActionButton(
    label: String,
    onClick: () -> Unit,
    style: KidsStyle,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    kind: ButtonKind = ButtonKind.PRIMARY,
    container: Color? = null,
    labelSp: Int = if (kind == ButtonKind.SOFT) 22 else (style.questSp - 2).coerceAtLeast(18),
    leading: (@Composable () -> Unit)? = null,
    debouncer: Debouncer? = null,
) {
    val bg = container ?: when (kind) {
        ButtonKind.PRIMARY -> style.accent
        ButtonKind.SECONDARY -> style.container
        ButtonKind.SOFT -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        container != null -> onColorFor(container)
        kind == ButtonKind.PRIMARY -> style.onAccent
        kind == ButtonKind.SECONDARY -> style.text
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val reduced = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reduced) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "press")
    val height = if (kind == ButtonKind.SOFT) style.target else style.buttonHeight
    Surface(
        onClick = { debouncer?.run(block = onClick) ?: onClick() },
        interactionSource = interaction,
        shape = RoundedCornerShape(if (kind == ButtonKind.SOFT) 28.dp else 32.dp),
        color = bg, contentColor = fg,
        shadowElevation = if (kind == ButtonKind.SOFT) 0.dp else 4.dp,
        border = if (kind == ButtonKind.SOFT) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = modifier.heightIn(min = height).graphicsLayer { scaleX = scale; scaleY = scale }.semantics { role = Role.Button },
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            leading?.invoke()
            if (leading == null && icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size((height.value * 0.45f).dp))
            Text(label, Modifier.padding(start = if (leading != null || icon != null) 12.dp else 0.dp), style = kidsText(labelSp), textAlign = TextAlign.Center)
        }
    }
}

enum class KiloMood { NEUTRAL, HAPPY, THINKING, SLEEPY, CHEER }

/** Kilo mit Stimmung: Huepfer einmalig bei Freude (nicht bei ruhiger Ansicht), sonst Neigung und Symbol daneben. */
@Composable
fun KiloAvatar(mood: KiloMood, size: Dp, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val hop = remember { Animatable(0f) }
    LaunchedEffect(mood, reduced) {
        if (!reduced && (mood == KiloMood.HAPPY || mood == KiloMood.CHEER)) {
            hop.snapTo(0f); hop.animateTo(1f, tween(280)); hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val tilt = when (mood) { KiloMood.THINKING -> 4f; KiloMood.SLEEPY -> 8f; else -> 0f }
    val overlay = when (mood) { KiloMood.HAPPY -> "✨"; KiloMood.CHEER -> "🎉"; KiloMood.THINKING -> "💭"; KiloMood.SLEEPY -> "💤"; KiloMood.NEUTRAL -> "" }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.kilo_fox), contentDescription = null,
            modifier = Modifier.size(size).graphicsLayer { translationY = -hop.value * size.toPx() * 0.12f; scaleX = 1f + hop.value * 0.06f; scaleY = 1f + hop.value * 0.06f; rotationZ = tilt }
                .alpha(if (mood == KiloMood.SLEEPY) 0.85f else 1f),
        )
        if (overlay.isNotEmpty()) Text(overlay, Modifier.align(Alignment.TopEnd), style = TextStyle(fontSize = (size.value * 0.28f).sp))
    }
}

/** Kilo mit Sprechblase (und auf Wunsch "Nochmal vorlesen"). */
@Composable
fun KiloBubble(text: String, style: KidsStyle, mood: KiloMood = KiloMood.NEUTRAL, size: Dp = 88.dp, modifier: Modifier = Modifier, onReadAloud: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KiloAvatar(mood, size)
        Surface(
            shape = RoundedCornerShape(28.dp, 28.dp, 28.dp, 6.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp,
            modifier = Modifier.weight(1f, fill = false).padding(bottom = 10.dp),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, Modifier.weight(1f, fill = false), style = kidsText((style.questSp - 6).coerceAtLeast(18), FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                if (onReadAloud != null) {
                    Surface(
                        onClick = onReadAloud, shape = CircleShape, color = style.container, contentColor = style.text,
                        modifier = Modifier.padding(start = 8.dp).size(style.target).semantics { contentDescription = "" },
                    ) { Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_volume_up), contentDescription = stringResource(R.string.mini_read_aloud), modifier = Modifier.size(28.dp)) } }
                }
            }
        }
    }
}

/** Fortschritt als Punkte (Kinder ohne Zahlen): gefuellt = erledigt; TalkBack liest "Aufgabe x von y". */
@Composable
fun ProgressTrail(done: Int, total: Int, style: KidsStyle, modifier: Modifier = Modifier, steps: Int = 5) {
    val t = total.coerceAtLeast(1)
    val filled = (done.coerceAtMost(t) * steps / t).coerceIn(0, steps)
    val desc = stringResource(R.string.kids_progress, done.coerceAtMost(t), t)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = desc }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until steps) {
            Box(
                Modifier.size(if (style.band == app.kiloquest.game.AgeBand.MINI) 22.dp else 16.dp).then(
                    if (i < filled) Modifier.background(style.accent, CircleShape)
                    else Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).border(3.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                ),
            )
        }
    }
}

/** Letzte gesammelte Sticker als Regal plus "n von m". */
@Composable
fun StickerShelf(all: List<Sticker>, collected: Set<String>, style: KidsStyle, modifier: Modifier = Modifier, showCount: Boolean = true) {
    val have = all.filter { it.id in collected }
    val desc = stringResource(R.string.kids_album, collected.size, all.size)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = desc }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        have.takeLast(3).forEach { StickerImage(it, true, size = 36.dp) }
        if (have.isEmpty()) StickerImage(all.first(), false, size = 36.dp)
        if (showCount) Text("${collected.size}/${all.size}", style = kidsText(16), color = style.text)
    }
}

/** Sterne-Punktestand als Pille. */
@Composable
fun StarPill(points: Int, style: KidsStyle, modifier: Modifier = Modifier) {
    val desc = stringResource(R.string.kids_points, points)
    Surface(shape = RoundedCornerShape(50), color = style.star, contentColor = style.onStar, modifier = modifier.semantics(mergeDescendants = true) { contentDescription = desc }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("★", style = kidsText(22))
            Text(points.toString(), Modifier.padding(start = 6.dp), style = kidsText(22))
        }
    }
}

/** Einmaliges Konfetti bei einem neuen Sticker (ca. 1,2 s, deterministisch); bei ruhiger Ansicht nichts. */
@Composable
fun ConfettiBurst(trigger: Any?, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    var p by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(trigger, reduced) {
        if (trigger == null || reduced) { p = 1f; return@LaunchedEffect }
        val a = Animatable(0f)
        a.animateTo(1f, tween(1200)) { p = value }
    }
    if (p >= 1f) return
    val colors = listOf(Color(0xFFFFD54A), Color(0xFFFF9F43), Color(0xFF5FCB8C), Color(0xFF7FB2FF), Color(0xFFFF8A9A))
    Canvas(modifier.fillMaxWidth().heightIn(min = 160.dp)) {
        val rnd = Random(trigger.hashCode())
        repeat(26) {
            val x0 = rnd.nextFloat() * size.width
            val v = 0.5f + rnd.nextFloat()
            val y = -20f + (size.height + 40f) * p * v
            val sway = (rnd.nextFloat() - 0.5f) * 80f * p
            drawCircle(colors[it % colors.size].copy(alpha = (1f - p).coerceIn(0f, 1f)), radius = 6f + rnd.nextFloat() * 6f, center = Offset(x0 + sway, y))
        }
    }
}

/** Feste untere Leiste: wenige grosse Knoepfe mit Symbol und kurzem Wort. */
@Composable
fun BarButton(label: String, onClick: () -> Unit, style: KidsStyle, modifier: Modifier = Modifier, icon: Painter? = null, emoji: String? = null, container: Color = style.container, content: Color = style.text, debouncer: Debouncer? = null) {
    Surface(
        onClick = { debouncer?.run(block = onClick) ?: onClick() },
        shape = RoundedCornerShape(24.dp), color = container, contentColor = content, shadowElevation = 2.dp,
        modifier = modifier.heightIn(min = style.target + 16.dp).semantics { role = Role.Button },
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp)) else Text(emoji.orEmpty(), style = TextStyle(fontSize = 30.sp))
            Text(label, style = kidsText(15), maxLines = 1)
        }
    }
}

/** "Halten zum Verlassen": Schutz vor versehentlichem Verlassen der Kinderansicht (fuer Erwachsene). */
@Composable
fun HoldToLeave(label: String, hint: String, style: KidsStyle, onDone: () -> Unit, modifier: Modifier = Modifier, holdMs: Long = 900) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                val job = scope.launch { progress.snapTo(0f); progress.animateTo(1f, tween(holdMs.toInt(), easing = androidx.compose.animation.core.LinearEasing)); onDone() }
                tryAwaitRelease()
                job.cancel()
                scope.launch { progress.snapTo(0f) }
            })
        }.semantics { role = Role.Button; contentDescription = "$label. $hint" }) {
        Box {
            Box(Modifier.matchParentSizeFraction(progress.value).background(style.container))
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = kidsText(18), color = MaterialTheme.colorScheme.onSurface)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Modifier.matchParentSizeFraction(f: Float): Modifier = this.then(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).heightIn(min = 64.dp))

/** Ueberschrift fuer TalkBack (Reihenfolge/Navigation). */
fun Modifier.asHeading(): Modifier = this.semantics { heading() }

/** Aenderungen (Lob, Fehlermeldungen) hoerbar machen. */
fun Modifier.politeLive(): Modifier = this.semantics { liveRegion = LiveRegionMode.Polite }

/** Sprachausgabe dieses Geraets fuer "Vorlesen" auf Kinder-Geraeten. */
@Composable
fun rememberSpeaker(): (String) -> Unit {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val holder = remember { arrayOfNulls<android.speech.tts.TextToSpeech>(1) }
    val ready = remember { booleanArrayOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        holder[0] = android.speech.tts.TextToSpeech(ctx) { status -> ready[0] = status == android.speech.tts.TextToSpeech.SUCCESS }
        onDispose { holder[0]?.stop(); holder[0]?.shutdown(); holder[0] = null; ready[0] = false }
    }
    return { text -> if (ready[0]) holder[0]?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "kq-kids-read") }
}
