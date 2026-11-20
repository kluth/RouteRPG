package app.kiloquest.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiloquest.R
import app.kiloquest.UiState
import app.kiloquest.nav.LatLon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class MapMode { FOLLOW, OVERVIEW }

private const val M_PER_DEG_LAT = 110_540.0
private const val FOLLOW_WINDOW_M = 3_000.0

/**
 * Schematische Live-Karte fuer Kinder: Route als dicke Linie (zurueckgelegt blass), Ziel-Flagge und die eigene
 * Figur an der aktuellen Position. Braucht weder Internet noch Kartenkacheln, laeuft also auch auf Geraeten, die nur per
 * Bluetooth verbunden sind. [FOLLOW] zeigt die Umgebung (Fahrtrichtung oben), [OVERVIEW] die ganze Route (Norden oben).
 */
/** Sanft zur neuen GPS-Position gleiten (statt zu springen); Verschiebung in Metern gegenueber dem ersten Fix. */
@Composable
private fun rememberSmoothPosition(target: LatLon?): LatLon? {
    var anchor by remember { mutableStateOf<LatLon?>(null) }
    val east = remember { androidx.compose.animation.core.Animatable(0f) }
    val north = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(target) {
        if (target == null) { anchor = null; return@LaunchedEffect }
        val a = anchor
        if (a == null || app.kiloquest.nav.haversine(a, target) > 20_000.0) {
            anchor = target; east.snapTo(0f); north.snapTo(0f); return@LaunchedEffect
        }
        val e = ((target.lon - a.lon) * cos(a.lat * PI / 180) * 111_320.0).toFloat()
        val n = ((target.lat - a.lat) * M_PER_DEG_LAT).toFloat()
        val spec = androidx.compose.animation.core.tween<Float>(900, easing = androidx.compose.animation.core.LinearEasing)
        kotlinx.coroutines.coroutineScope {
            launch { east.animateTo(e, spec) }
            launch { north.animateTo(n, spec) }
        }
    }
    val a = anchor ?: return target
    return LatLon(a.lat + north.value / M_PER_DEG_LAT, a.lon + east.value / (111_320.0 * cos(a.lat * PI / 180)))
}

/** Faehrt das Auto gerade? (Position hat sich in den letzten 3 s um mehr als 3 m geaendert) */
@Composable
private fun rememberMoving(pos: LatLon?): Boolean {
    var last by remember { mutableStateOf<LatLon?>(null) }
    var moving by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(pos) {
        val p = pos ?: return@LaunchedEffect
        val l = last
        if (l == null || app.kiloquest.nav.haversine(l, p) > 3.0) { moving = l != null; last = p }
        kotlinx.coroutines.delay(3000)
        moving = false
    }
    return moving
}

/** Richtung weich drehen (immer den kuerzeren Weg, auch ueber Norden). */
@Composable
private fun rememberSmoothBearing(target: Float?): Float? {
    val angle = remember { androidx.compose.animation.core.Animatable(0f) }
    var inited by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        if (!inited) { angle.snapTo(target); inited = true; return@LaunchedEffect }
        val cur = angle.value
        val d = ((target - cur) % 360f + 540f) % 360f - 180f
        angle.animateTo(cur + d, androidx.compose.animation.core.tween(600))
    }
    return if (target == null) null else ((angle.value % 360f) + 360f) % 360f
}

/**
 * Schematische Live-Karte fuer Kinder in 3D-Optik: die Kartenebene ist nach hinten gekippt (Perspektive, Nebel am Horizont),
 * Route als dicke Linie (zurueckgelegt blass), Ziel-Flagge und die eigene Figur. Die Figur schwebt leicht auf und ab, wirft
 * einen Schatten auf die Karte, pulsiert mit einem Ring, zeigt mit einem Lichtkegel in Fahrtrichtung und gleitet weich von
 * Position zu Position. Braucht weder Internet noch Kartenkacheln. [FOLLOW] zeigt die Umgebung, [OVERVIEW] die ganze Route
 * flach von oben. Norden ist immer oben.
 */
@Composable
fun KidMap(state: UiState, mode: MapMode, modifier: Modifier = Modifier) {
    val route = state.route
    val pos = rememberSmoothPosition(state.position)
    val bearing = rememberSmoothBearing(state.bearing)
    val moving = rememberMoving(state.position)
    val context = androidx.compose.ui.platform.LocalContext.current
    // 3D-Fahrzeug: Drehbilder (siehe Figures3D); sonst Emoji
    val sprites = remember(state.avatar) { Figures3D.frames(context, state.avatar)?.map { it.asImageBitmap() } }
    val along = state.guidance?.alongMeters ?: 0.0
    val colors = KiloTheme.colors
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val routeColor = colors.route
    val casing = MaterialTheme.colorScheme.surface
    // zurueckgelegt: mit dem Hintergrund gemischt statt durchsichtig (sonst sieht man bei runden Enden Punkte)
    val doneColor = androidx.compose.ui.graphics.lerp(bg, routeColor, 0.3f)
    val avatar = state.avatar
    val flag = "🏁"
    val desc = stringResource(R.string.map_live_desc)

    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "kidmap")
    val pulse by infinite.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)),
        label = "pulse",
    )
    val shake by infinite.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.LinearEasing)),
        label = "shake",
    )
    val bob by infinite.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "bob",
    )

    Canvas(modifier.background(bg).semantics { contentDescription = desc }) {
        val w = size.width
        val h = size.height
        val dp = density
        // Mittelpunkt und Massstab (Meter pro Pixel auf Bodenhoehe) je Modus
        val center: LatLon
        val metersPerPx: Double
        if (mode == MapMode.FOLLOW || route == null) {
            center = pos ?: route?.line?.points?.firstOrNull() ?: return@Canvas
            metersPerPx = FOLLOW_WINDOW_M / min(w, h)
        } else {
            val pts = route.line.points
            val minLat = pts.minOf { it.lat }; val maxLat = pts.maxOf { it.lat }
            val minLon = pts.minOf { it.lon }; val maxLon = pts.maxOf { it.lon }
            center = LatLon((minLat + maxLat) / 2, (minLon + maxLon) / 2)
            val cosLat = cos(center.lat * PI / 180)
            val spanX = max((maxLon - minLon) * cosLat * 111_320.0, 500.0)
            val spanY = max((maxLat - minLat) * M_PER_DEG_LAT, 500.0)
            metersPerPx = max(spanX / (w * 0.8), spanY / (h * 0.8))
        }
        val cosLat0 = cos(center.lat * PI / 180)

        // 3D: Kartenebene nach hinten gekippt (Perspektive); in der Gesamtansicht flach von oben
        val tilt = if (mode == MapMode.FOLLOW) 52.0 * PI / 180 else 0.0
        val sinT = sin(tilt).toFloat(); val cosT = cos(tilt).toFloat()
        val camD = h * 1.1f
        val cy0 = if (tilt > 0) h * 0.66f else h / 2f

        class P(val o: Offset, val s: Float)
        // Bodenpunkt in Pixeln relativ zur Mitte (x nach Osten, y nach Sueden) -> Bildschirm
        fun proj(gx: Float, gy: Float): P {
            val z = -gy * sinT
            val scale = camD / (camD + z).coerceAtLeast(camD * 0.3f)
            return P(Offset(w / 2 + gx * scale, cy0 + gy * cosT * scale), scale)
        }
        fun ground(p: LatLon): Pair<Float, Float> {
            val x = (p.lon - center.lon) * cosLat0 * 111_320.0
            val y = -(p.lat - center.lat) * M_PER_DEG_LAT
            return (x / metersPerPx).toFloat() to (y / metersPerPx).toFloat()
        }
        fun proj(p: LatLon): P { val (gx, gy) = ground(p); return proj(gx, gy) }

        // Raster am Boden (alle 500 m) als Orientierung; es scrollt mit der Fahrt
        if (mode == MapMode.FOLLOW) {
            val stepPx = (500.0 / metersPerPx).toFloat()
            if (stepPx > 12f) {
                val centerE = center.lon * cosLat0 * 111_320.0
                val centerN = center.lat * M_PER_DEG_LAT
                val offX = (-(centerE % 500.0) / metersPerPx).toFloat()
                val offY = ((centerN % 500.0) / metersPerPx).toFloat()
                val reach = w * 1.6f
                var k = -(reach / stepPx).toInt() - 1
                while (k <= (reach / stepPx).toInt() + 1) {
                    val x = offX + k * stepPx
                    drawLine(grid, proj(x, -reach).o, proj(x, reach).o, strokeWidth = 1f)
                    val y = offY + k * stepPx
                    drawLine(grid, proj(-reach, y).o, proj(reach, y).o, strokeWidth = 1f)
                    k++
                }
            }
        }

        if (route != null) {
            val line = route.line
            val lenM = line.lengthMeters
            val from = if (mode == MapMode.FOLLOW) (along - FOLLOW_WINDOW_M * 1.6).coerceAtLeast(0.0) else 0.0
            val to = if (mode == MapMode.FOLLOW) (along + FOLLOW_WINDOW_M * 1.6).coerceAtMost(lenM) else lenM
            val pts = line.slice(from, to, 500)
            if (pts.size >= 2) {
                val total = max(to - from, 1.0)
                val projected = pts.map { proj(it) }
                val baseW = if (mode == MapMode.FOLLOW) 14.dp.toPx() else 8.dp.toPx()
                for (pass in 0..1) { // erst weisse Kontur, dann Linie
                    for (i in 0 until projected.size - 1) {
                        val a0 = projected[i]; val b0 = projected[i + 1]
                        val atM = from + total * (i.toDouble() / (pts.size - 1))
                        val done = atM < along
                        val sw = baseW * (a0.s + b0.s) / 2
                        if (pass == 0) {
                            if (!done) drawLine(casing, a0.o, b0.o, strokeWidth = sw + 6.dp.toPx() * a0.s, cap = StrokeCap.Round)
                        } else {
                            drawLine(if (done) doneColor else routeColor, a0.o, b0.o, strokeWidth = sw, cap = StrokeCap.Round)
                        }
                    }
                }
            }
            val first = proj(line.points.first())
            drawOval(routeColor.copy(alpha = 0.5f), Offset(first.o.x - 6.dp.toPx() * first.s, first.o.y - 6.dp.toPx() * first.s * cosT), androidx.compose.ui.geometry.Size(12.dp.toPx() * first.s, 12.dp.toPx() * first.s * cosT))
        }

        // Nebel am Horizont: ferne Teile der Karte blenden aus
        if (tilt > 0) {
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)), startY = 0f, endY = h * 0.34f), size = androidx.compose.ui.geometry.Size(w, h * 0.34f))
        }

        // Ziel-Flagge steht aufrecht auf der Karte (mit kleinem Schatten)
        if (route != null) {
            val dest = state.destination?.position ?: route.line.points.last()
            val d = proj(dest)
            if (d.o.x in -40f..w + 40f && d.o.y in -40f..h + 40f) {
                drawOval(Color.Black.copy(alpha = 0.2f), Offset(d.o.x - 10.dp.toPx() * d.s, d.o.y - 4.dp.toPx() * d.s), androidx.compose.ui.geometry.Size(20.dp.toPx() * d.s, 8.dp.toPx() * d.s))
                drawEmoji(flag, d.o.x, d.o.y - 14.dp.toPx() * d.s, 30.dp.toPx() * d.s.coerceIn(0.5f, 1.4f))
            }
        }

        // Eigene Figur: Schatten am Boden, pulsierender Ring, Lichtkegel in Fahrtrichtung, schwebende Figur
        val me = if (pos != null) proj(pos) else P(Offset(w / 2, cy0), 1f)
        val hover = (10f + 5f * bob) * dp                       // Schwebehoehe in px
        val r = 24.dp.toPx() * me.s
        // Ring (breitet sich am Boden aus und blendet aus)
        val ringR = r * (0.9f + 1.6f * pulse)
        drawOval(
            routeColor.copy(alpha = 0.55f * (1f - pulse)),
            Offset(me.o.x - ringR, me.o.y - ringR * cosT.coerceAtLeast(0.55f)),
            androidx.compose.ui.geometry.Size(ringR * 2, ringR * 2 * cosT.coerceAtLeast(0.55f)),
            style = Stroke(width = 3.dp.toPx()),
        )
        // Lichtkegel (am Boden) in Fahrtrichtung
        if (bearing != null) {
            val rad = bearing * PI.toFloat() / 180f
            val dx = sin(rad); val dy = -cos(rad)
            val len = 70.dp.toPx(); val half = 22.dp.toPx()
            val gx0 = if (pos != null) ground(pos).first else 0f
            val gy0 = if (pos != null) ground(pos).second else 0f
            val tip = proj(gx0 + dx * len, gy0 + dy * len)
            val l = proj(gx0 - dy * half, gy0 + dx * half)
            val rr = proj(gx0 + dy * half, gy0 - dx * half)
            val cone = Path().apply { moveTo(l.o.x, l.o.y); lineTo(tip.o.x, tip.o.y); lineTo(rr.o.x, rr.o.y); close() }
            drawPath(cone, androidx.compose.ui.graphics.Brush.linearGradient(listOf(routeColor.copy(alpha = 0.40f), routeColor.copy(alpha = 0f)), start = me.o, end = tip.o))
        }
        // Schatten: je hoeher die Figur schwebt, desto kleiner und blasser
        val shrink = 1f - 0.18f * bob
        drawOval(Color.Black.copy(alpha = 0.16f), Offset(me.o.x - r * 1.15f * shrink, me.o.y - r * 0.5f * cosT * shrink), androidx.compose.ui.geometry.Size(r * 2.3f * shrink, r * 1.0f * cosT * shrink))
        drawOval(Color.Black.copy(alpha = 0.22f), Offset(me.o.x - r * 0.8f * shrink, me.o.y - r * 0.35f * cosT * shrink), androidx.compose.ui.geometry.Size(r * 1.6f * shrink, r * 0.7f * cosT * shrink))
        // Spur hinter der Figur (nur bei Fahrt): Staub, Schaum, Rauch oder Flamme je nach Fahrzeug
        if (moving && bearing != null) {
            val rad = bearing * PI.toFloat() / 180f
            val dx = sin(rad); val dy = -cos(rad)
            val gx0 = if (pos != null) ground(pos).first else 0f
            val gy0 = if (pos != null) ground(pos).second else 0f
            val c = puffColorFor(avatar)
            for (i in 0 until 6) {
                val f = ((pulse * 2f + i / 6f) % 1f)
                val back = f * 80.dp.toPx()
                val q = proj(gx0 - dx * back, gy0 - dy * back)
                val rr = (4.dp.toPx() + 11.dp.toPx() * f) * q.s
                drawOval(c.copy(alpha = 0.45f * (1f - f) * (1f - f)), Offset(q.o.x - rr, q.o.y - rr * cosT.coerceAtLeast(0.55f)), androidx.compose.ui.geometry.Size(rr * 2, rr * 2 * cosT.coerceAtLeast(0.55f)))
            }
        }

        // Figur schwebt bzw. federt, ohne Untergrund: 3D-Fahrzeuge als Drehbild, sonst Emoji
        val isFlier = sprites == null || avatar == "🚀" || avatar == "🛸"
        val springAmp = if (moving) 1.6f else 0.4f
        val lift = if (isFlier) hover else 2.dp.toPx() + springAmp * dp * sin(shake * 2f * PI.toFloat())
        // Kurvenlage: zu Beginn einer Drehung neigt sich die Figur, dann richtet sie sich auf
        val turn = if (state.bearing != null && bearing != null) ((state.bearing - bearing + 540f) % 360f - 180f) else 0f
        val roll = if (moving) (turn * 0.25f).coerceIn(-12f, 12f) else 0f
        if (sprites != null) {
            val img = sprites[app.kiloquest.game.Avatars.frameFor(bearing)]
            val sz = (96.dp.toPx() * me.s.coerceIn(0.8f, 1.25f)).toInt()
            val topLeft = androidx.compose.ui.unit.IntOffset((me.o.x - sz / 2f).toInt(), (me.o.y - sz * 0.88f - lift).toInt())
            rotate(roll, pivot = Offset(me.o.x, me.o.y - sz * 0.35f - lift)) {
                drawImage(img, dstOffset = topLeft, dstSize = androidx.compose.ui.unit.IntSize(sz, sz), filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
            }
        } else {
            val pose = app.kiloquest.game.Avatars.pose(avatar, bearing)
            drawEmoji(avatar, me.o.x, me.o.y - r - lift, r * 2.1f, pose.rotationDegrees, pose.mirrored)
        }

        // Kompass: Norden ist immer oben
        val cc = Offset(26.dp.toPx(), 26.dp.toPx())
        drawCircle(Color.White.copy(alpha = 0.8f), 18.dp.toPx(), cc)
        val tri = Path().apply {
            moveTo(cc.x, cc.y - 12.dp.toPx()); lineTo(cc.x - 6.dp.toPx(), cc.y + 6.dp.toPx()); lineTo(cc.x + 6.dp.toPx(), cc.y + 6.dp.toPx()); close()
        }
        drawPath(tri, Color(0xFFB3261E))
    }
}

private fun puffColorFor(emoji: String): Color = when (app.kiloquest.game.Avatars.modelOf(emoji)) {
    "sailboat", "speedboat", "liner", "rowboat", "cargoship", "galleon" -> Color.White
    "rocket", "ufo" -> Color(0xFFFFC247)
    "train_bullet", "train_city", "train_city_b" -> Color(0xFFE8E8E8)
    else -> if (app.kiloquest.game.Avatars.orientOf(emoji) == app.kiloquest.game.Orient.POINTER) Color.White else Color(0xFFCFC6B4)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmoji(
    text: String, cx: Float, cy: Float, sizePx: Float, rotationDegrees: Float = 0f, mirrored: Boolean = false,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sizePx; textAlign = Paint.Align.CENTER }
    val fm = paint.fontMetrics
    val c = drawContext.canvas.nativeCanvas
    c.save()
    c.rotate(rotationDegrees, cx, cy)
    if (mirrored) c.scale(-1f, 1f, cx, cy)
    c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, paint)
    c.restore()
}

/**
 * Karte fuer Kinder: echte Kacheln, wenn gewuenscht und Position bekannt, sonst (oder ohne Netz) die schematische Karte.
 * Die Namen und Urheberrechte der Kartendaten stehen sichtbar dabei.
 */
@Composable
fun KidMapView(state: UiState, mode: MapMode, modifier: Modifier = Modifier, interactive: Boolean = false) {
    var failed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    // Neuer Versuch, wenn der Nutzer "Echte Karte" erneut waehlt
    androidx.compose.runtime.LaunchedEffect(state.realMap) { if (state.realMap) failed = false }
    if (state.realMap && state.position != null && !failed) {
        Box(modifier) {
            KidTileMap(state, mode, Modifier.fillMaxSize(), interactive) { failed = true }
            Text(
                stringResource(R.string.map_attribution),
                Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = Color(0xFF14171A),
            )
        }
    } else {
        KidMap(state, mode, modifier)
    }
}

/** Kleine Karte mit Vollbild-Knopf und Figur-Wahl; sonst ruhig, nur auf Wunsch gross. */
@Composable
fun KidMapCard(state: UiState, onOpen: () -> Unit, onPickAvatar: () -> Unit, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 160.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp))) {
        KidMapView(state, MapMode.FOLLOW, Modifier.fillMaxSize())
        // Tippen oeffnet die grosse Karte (die Kartenansicht selbst nimmt keine Gesten an)
        Box(Modifier.fillMaxSize().clickable(onClick = onOpen))
        if (state.route == null && state.position == null) {
            Text(stringResource(R.string.map_waiting), Modifier.align(Alignment.Center).padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        MapRoundButton(R.drawable.ic_open_in_full, stringResource(R.string.map_fullscreen), onOpen, Modifier.align(Alignment.TopEnd).padding(8.dp))
        AvatarBadge(state.avatar, onPickAvatar, Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

@Composable
private fun MapRoundButton(icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(24.dp))
        }
    }
}

/** Rundes Feld mit der aktuellen Figur; Tippen oeffnet die Auswahl. */
@Composable
fun AvatarBadge(avatar: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.size(56.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Box(contentAlignment = Alignment.Center) { AvatarImage(avatar, 40.dp) }
    }
}

/** Vollbild-Karte auf Wunsch: Umgebung oder ganze Route, Figur waehlen, schliessen. */
@Composable
fun KidMapFull(state: UiState, onClose: () -> Unit, onPickAvatar: () -> Unit, onRealMap: (Boolean) -> Unit = {}) {
    var mode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(MapMode.FOLLOW) }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            KidMapView(state, mode, Modifier.fillMaxSize(), interactive = true)
            Row(Modifier.align(Alignment.TopCenter).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == MapMode.FOLLOW, onClick = { mode = MapMode.FOLLOW }, label = { Text(stringResource(R.string.map_mode_near)) })
                FilterChip(selected = mode == MapMode.OVERVIEW, onClick = { mode = MapMode.OVERVIEW }, label = { Text(stringResource(R.string.map_mode_route)) })
            }
            // Echte Karte oder schematische Skizze (ohne Internet)
            Row(Modifier.align(Alignment.TopCenter).padding(top = 64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.realMap, onClick = { onRealMap(true) }, label = { Text(stringResource(R.string.map_style_real)) })
                FilterChip(selected = !state.realMap, onClick = { onRealMap(false) }, label = { Text(stringResource(R.string.map_style_sketch)) })
            }
            MapRoundButton(R.drawable.ic_close, stringResource(R.string.map_close), onClose, Modifier.align(Alignment.TopEnd).padding(12.dp))
            AvatarBadge(state.avatar, onPickAvatar, Modifier.align(Alignment.CenterEnd).padding(16.dp))
            val remaining = state.guidance?.remainingMeters
            if (remaining != null && remaining > 0) {
                Surface(Modifier.align(Alignment.BottomStart).padding(16.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                    Text(
                        stringResource(R.string.remaining_summary, if (remaining >= 1000) stringResource(R.string.unit_km, remaining / 1000.0) else stringResource(R.string.unit_m, remaining.toInt())),
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}


/** Figur als Bild: 3D-Vorschau (Fahrt nach Osten), sonst Emoji. */
@Composable
fun AvatarImage(emoji: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bmp = remember(emoji) { Figures3D.preview(ctx, emoji)?.asImageBitmap() }
    if (bmp != null) {
        androidx.compose.foundation.Image(bmp, contentDescription = null, modifier = modifier.size(size * 1.25f))
    } else {
        Text(emoji, modifier, style = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(size.value * 0.72f, androidx.compose.ui.unit.TextUnitType.Sp)))
    }
}
