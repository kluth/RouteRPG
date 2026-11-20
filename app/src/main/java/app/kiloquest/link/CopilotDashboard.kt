package app.kiloquest.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.R
import app.kiloquest.nav.TravelDiary
import app.kiloquest.stringByKey
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
private fun distance(meters: Double): String =
    if (meters >= 1000) stringResource(R.string.unit_km, meters / 1000.0) else stringResource(R.string.unit_m, meters.toInt())

@Composable
private fun duration(seconds: Double): String {
    val minutes = (seconds / 60).toInt().coerceAtLeast(0)
    return if (minutes >= 60) stringResource(R.string.unit_h_min, minutes / 60, minutes % 60) else stringResource(R.string.unit_min, minutes)
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/**
 * Fahrtstatus fuer den Beifahrer: Ziel, Rest, Ankunftszeit, Fortschritt, aktuelles Tempo mit Limit, Land und naechste Anweisung.
 * Ohne Fahrt ([drive] == null) steht ein kurzer Hinweis da.
 */
@Composable
fun DriveCard(drive: DriveDto?) {
    Panel {
        if (drive == null) {
            Text(stringResource(R.string.copilot_no_trip), style = MaterialTheme.typography.bodyLarge)
            return@Panel
        }
        if (drive.destination.isNotBlank()) Text(drive.destination, style = MaterialTheme.typography.titleLarge)
        val eta = remember(drive) { LocalTime.now().plusSeconds(drive.remainingSeconds.toLong()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }
        Text(
            distance(drive.remainingMeters) + " · " + duration(drive.remainingSeconds),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(stringResource(R.string.copilot_arrival, eta), style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(progress = { drive.progress }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            drive.speedKmh?.let { Text(stringResource(R.string.copilot_speed, it), style = MaterialTheme.typography.titleMedium) }
            drive.limitKmh?.takeIf { it in 1..200 }?.let { Text(stringResource(R.string.copilot_limit, it), style = MaterialTheme.typography.bodyMedium) }
            drive.country?.let { c ->
                Text(TravelDiary.flag(c) + " " + Locale("", c).getDisplayCountry(Locale.getDefault()), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val instruction = drive.nextInstruction
        if (instruction != null) {
            Text(
                stringResource(R.string.copilot_next, instruction, drive.nextMeters?.let { distance(it) } ?: ""),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Was die Kinder gerade machen: Punkte, Sticker, Fortschritt und die aktuelle Aufgabe. */
@Composable
fun KidsStatusCard(points: Int, stickers: Int, questsDone: Int, questsTotal: Int, questText: String?) {
    Panel {
        Text(stringResource(R.string.copilot_kids_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.copilot_kids_stats, points, stickers) +
                if (questsTotal > 0) " · " + stringResource(R.string.kids_progress, questsDone.coerceAtMost(questsTotal), questsTotal) else "",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!questText.isNullOrBlank()) Text(stringResource(R.string.copilot_kids_now, questText), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Text der aktuellen Aufgabe in der Geraetesprache (leer, wenn keine). */
@Composable
fun questLine(dto: QuestDto?): String? {
    if (dto == null) return null
    val ctx = LocalContext.current
    return remember(dto) { ctx.stringByKey(dto.textKey, dto.args).take(160) }
}
