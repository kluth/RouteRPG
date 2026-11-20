package app.kiloquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kiloquest.R
import app.kiloquest.car.maneuverIconRes
import app.kiloquest.nav.LaneGuidance
import app.kiloquest.nav.Maneuver
import app.kiloquest.nav.ManeuverKind
import app.kiloquest.nav.SpeedProfile
import java.text.DateFormat
import java.util.Date

/** Schmale Bildschirme (Hochformat): kleinere Schrift und feste Einzeiler statt unkontrollierter Umbrueche. */
@Composable
fun isCompactWidth(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 420

@Composable
private fun distanceLabel(meters: Double): String =
    if (meters >= 1000) stringResource(R.string.unit_km, meters / 1000.0)
    else stringResource(R.string.unit_m, (meters.coerceAtLeast(0.0) / 10).toInt() * 10)

/** Abbiege-Karte: grosses Symbol, Distanz (fett) und Strasse; immer einzeilig, Strasse wird gekuerzt. */
@Composable
fun ManeuverCard(maneuver: Maneuver?, metersToGo: Double, modifier: Modifier = Modifier) {
    val c = KiloTheme.colors
    val compact = isCompactWidth()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = c.navBanner,
        contentColor = c.onNavBanner,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(maneuverIconRes(maneuver?.kind ?: ManeuverKind.STRAIGHT)), contentDescription = null, modifier = Modifier.size(if (compact) 44.dp else 60.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    distanceLabel(metersToGo),
                    style = MaterialTheme.typography.displayMedium.let { if (compact) it.copy(fontSize = 30.sp, lineHeight = 34.sp) else it },
                    maxLines = 1, softWrap = false,
                )
                maneuver?.streetNames?.firstOrNull()?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleMedium.let { st -> if (compact) st.copy(fontSize = 16.sp, lineHeight = 20.sp) else st },
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Tempolimit-Schild; bei Ueberschreitung zusaetzlich die eigene Geschwindigkeit (Zahl nur dann). */
@Composable
fun SpeedSign(limit: Int?, speedKmh: Int?, overspeed: Boolean, modifier: Modifier = Modifier) {
    val ring = KiloTheme.colors.speedRing
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (limit != null) {
            val unlimited = limit >= SpeedProfile.UNLIMITED
            val label = if (unlimited) stringResource(R.string.speed_unlimited) else stringResource(R.string.speed_limit_desc, limit)
            Box(
                Modifier
                    .size(76.dp)
                    .semantics { contentDescription = label }
                    .background(Color(0xFFFFFFFF), CircleShape)
                    .border(9.dp, if (unlimited) Color.Gray else ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (unlimited) "∞" else limit.toString(),
                    color = Color(0xFF14171A),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
        if (overspeed && speedKmh != null) {
            Surface(shape = RoundedCornerShape(12.dp), color = ring, contentColor = Color.White, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.speed_kmh, speedKmh), Modifier.padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Spuren vor der Kreuzung; gueltige Spuren blau hinterlegt, ungueltige gedaempft. */
@Composable
fun LaneStrip(g: LaneGuidance, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (lane in g.lanes) {
            Box(
                Modifier
                    .size(width = 46.dp, height = 52.dp)
                    .background(if (lane.valid) scheme.primary else scheme.surface.copy(alpha = 0.92f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    lane.symbol,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (lane.valid) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Untere Leiste waehrend der Fahrt: Ankunft, Rest-Strecke, Rest-Zeit und Stopp (einzeilig, im Hochformat kleiner). */
@Composable
fun EtaBar(remainingMeters: Double, remainingSeconds: Double, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val compact = isCompactWidth()
    val arrival = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + (remainingSeconds * 1000).toLong()))
    val minutes = (remainingSeconds / 60).toInt()
    val duration = if (minutes >= 60) stringResource(R.string.unit_h_min, minutes / 60, minutes % 60) else stringResource(R.string.unit_min, minutes)
    val km = if (remainingMeters >= 1000) stringResource(R.string.unit_km, remainingMeters / 1000.0) else stringResource(R.string.unit_m, remainingMeters.toInt())
    val style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = if (compact) 14.dp else 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 20.dp)) {
            Text(arrival, style = style, maxLines = 1, softWrap = false)
            Text(km, style = style, maxLines = 1, softWrap = false)
            Text(duration, style = style, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onStop, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(stringResource(R.string.stop_navigation), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Quests-Knopf mit Kilo (Fuchs); nur fuer Beifahrer gedacht, gross genug zum Treffen. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun QuestsPill(onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val c = KiloTheme.colors
    Surface(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = RoundedCornerShape(28.dp), color = c.hintContainer, contentColor = c.onHintContainer, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.Image(painterResource(R.drawable.kilo_fox), contentDescription = null, modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.kids_open), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        }
    }
}

/** Routenvorschau als untere Karte: Dauer gross, Strecke, Offline-Zeile, "Los" und Quests. */
@Composable
fun RoutePreviewSheet(
    distanceMeters: Double,
    durationSeconds: Double,
    onStart: () -> Unit,
    onQuests: () -> Unit,
    onQuestsLong: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    val minutes = (durationSeconds / 60).toInt()
    val duration = if (minutes >= 60) stringResource(R.string.unit_h_min, minutes / 60, minutes % 60) else stringResource(R.string.unit_min, minutes)
    val km = if (distanceMeters >= 1000) stringResource(R.string.unit_km, distanceMeters / 1000.0) else stringResource(R.string.unit_m, distanceMeters.toInt())
    val arrival = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + (durationSeconds * 1000).toLong()))
    Surface(modifier, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val compact = isCompactWidth()
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    duration,
                    style = MaterialTheme.typography.displayMedium.let { if (compact) it.copy(fontSize = 30.sp, lineHeight = 34.sp) else it },
                    maxLines = 1, softWrap = false,
                )
                Column(Modifier.weight(1f)) {
                    val sub = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
                    Text(km, style = sub, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Text(stringResource(R.string.route_arrival, arrival), style = sub, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                }
            }
            extra()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text(stringResource(R.string.start_navigation), style = MaterialTheme.typography.titleMedium) }
                QuestsPill(onQuests, onLongClick = onQuestsLong)
            }
        }
    }
}
