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
import androidx.compose.runtime.getValue
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
@Composable
fun KidMap(state: UiState, mode: MapMode, modifier: Modifier = Modifier) {
    val route = state.route
    val pos = state.position
    val along = state.guidance?.alongMeters ?: 0.0
    val colors = KiloTheme.colors
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val routeColor = colors.route
    val casing = MaterialTheme.colorScheme.surface
    val avatar = state.avatar
    val flag = "🏁"
    val desc = stringResource(R.string.map_live_desc)

    Canvas(modifier.background(bg).semantics { contentDescription = desc }) {
        val w = size.width
        val h = size.height
        // Mittelpunkt und Massstab (Meter pro Pixel) je Modus
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
        // Nord ist oben; die Figur zeigt die Fahrtrichtung (siehe Avatars.pose)
        val theta = 0.0

        fun project(p: LatLon): Offset {
            val x = (p.lon - center.lon) * cosLat0 * 111_320.0
            val y = -(p.lat - center.lat) * M_PER_DEG_LAT
            val rx = x * cos(theta) - y * sin(theta)
            val ry = x * sin(theta) + y * cos(theta)
            return Offset((w / 2 + rx / metersPerPx).toFloat(), (h / 2 + ry / metersPerPx).toFloat())
        }

        // Raster als Orientierung (alle 500 m im Nahmodus), dreht mit
        if (mode == MapMode.FOLLOW) {
            val step = (500.0 / metersPerPx).toFloat()
            if (step > 24f) {
                val ox = (w / 2) % step
                val oy = (h / 2) % step
                var x = ox
                while (x < w) { drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f); x += step }
                var y = oy
                while (y < h) { drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f); y += step }
            }
        }

        if (route != null) {
            val line = route.line
            val lenM = line.lengthMeters
            // Nur den sichtbaren Ausschnitt zeichnen (Nahmodus: Umgebung der Position)
            val from = if (mode == MapMode.FOLLOW) (along - FOLLOW_WINDOW_M * 1.2).coerceAtLeast(0.0) else 0.0
            val to = if (mode == MapMode.FOLLOW) (along + FOLLOW_WINDOW_M * 1.2).coerceAtMost(lenM) else lenM
            val pts = line.slice(from, to, 500)
            if (pts.size >= 2) {
                val done = Path(); val todo = Path()
                var doneStarted = false; var todoStarted = false
                var travelled = from
                val total = max(to - from, 1.0)
                pts.forEachIndexed { i, p ->
                    val o = project(p)
                    val frac = i.toDouble() / (pts.size - 1)
                    val atM = from + total * frac
                    if (atM <= along) { if (!doneStarted) { done.moveTo(o.x, o.y); doneStarted = true } else done.lineTo(o.x, o.y) }
                    if (atM >= along - total / pts.size) { if (!todoStarted) { todo.moveTo(o.x, o.y); todoStarted = true } else todo.lineTo(o.x, o.y) }
                    travelled = atM
                }
                val widthPx = if (mode == MapMode.FOLLOW) 14.dp.toPx() else 8.dp.toPx()
                val st = { c: Color, wpx: Float -> Stroke(width = wpx, cap = StrokeCap.Round, join = StrokeJoin.Round) }
                drawPath(done, routeColor.copy(alpha = 0.28f), style = st(routeColor, widthPx))
                drawPath(todo, casing, style = st(casing, widthPx + 6.dp.toPx()))
                drawPath(todo, routeColor, style = st(routeColor, widthPx))
            }
            // Start-Punkt und Ziel-Flagge
            val first = line.points.first()
            drawCircle(routeColor.copy(alpha = 0.5f), 6.dp.toPx(), project(first))
            val dest = state.destination?.position ?: line.points.last()
            val d = project(dest)
            drawEmoji(flag, d.x, d.y, 28.dp.toPx())
        }

        // Eigene Figur
        val pp = pos?.let { project(it) } ?: Offset(w / 2, h / 2)
        drawCircle(Color.White.copy(alpha = 0.85f), 26.dp.toPx(), pp)
        drawCircle(routeColor.copy(alpha = 0.35f), 26.dp.toPx(), pp, style = Stroke(width = 3.dp.toPx()))
        val pose = app.kiloquest.game.Avatars.pose(avatar, state.bearing)
        drawEmoji(avatar, pp.x, pp.y, 34.dp.toPx(), pose.rotationDegrees, pose.mirrored)

        // Kompass: kleiner Pfeil "Norden" oben links (dreht mit, wenn die Karte nach Fahrtrichtung zeigt)
        val cc = Offset(26.dp.toPx(), 26.dp.toPx())
        drawCircle(Color.White.copy(alpha = 0.8f), 18.dp.toPx(), cc)
        rotate(degrees = Math.toDegrees(theta).toFloat(), pivot = cc) {
            val tri = Path().apply {
                moveTo(cc.x, cc.y - 12.dp.toPx()); lineTo(cc.x - 6.dp.toPx(), cc.y + 6.dp.toPx()); lineTo(cc.x + 6.dp.toPx(), cc.y + 6.dp.toPx()); close()
            }
            drawPath(tri, Color(0xFFB3261E))
        }
    }
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
    Surface(onClick = onClick, modifier = modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Box(contentAlignment = Alignment.Center) { Text(avatar, style = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp))) }
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
