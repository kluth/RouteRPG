package app.kiloquest

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.nav.DiaryEntry
import app.kiloquest.nav.DiaryKind
import app.kiloquest.nav.TravelDiary
import app.kiloquest.ui.ConfirmDialog
import app.kiloquest.ui.screenColumn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private fun dayText(day: Int): String =
    LocalDate.ofEpochDay(day.toLong()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

/** Eine Zeile des Tagebuchs als Text (fuer Anzeige und Teilen). */
internal fun diaryLine(ctx: Context, e: DiaryEntry): String = when (e.kindOrNull) {
    DiaryKind.TRIP_START -> ctx.getString(R.string.diary_line_trip, e.title)
    DiaryKind.ARRIVAL -> ctx.getString(R.string.diary_line_arrival, e.title, ctx.getString(R.string.unit_km, e.meters / 1000.0))
    DiaryKind.COUNTRY -> ctx.getString(R.string.diary_line_country, TravelDiary.flag(e.country), e.title)
    DiaryKind.STORY -> ctx.getString(R.string.diary_line_story, e.title)
    DiaryKind.QUEST -> ctx.getString(R.string.diary_line_quest, e.title, e.points)
    DiaryKind.STICKER -> ctx.getString(R.string.diary_line_sticker, e.title)
    DiaryKind.PHOTO -> if (e.title.isBlank()) ctx.getString(R.string.diary_line_photo) else ctx.getString(R.string.diary_line_photo_place, e.title)
    null -> e.title
}

/** Das ganze Tagebuch als Text zum Teilen, Tag fuer Tag. */
internal fun diaryShareText(ctx: Context, entries: List<DiaryEntry>): String {
    val st = TravelDiary.stats(entries)
    return buildString {
        appendLine(ctx.getString(R.string.diary_title))
        appendLine(ctx.getString(R.string.diary_stats_trip, st.days, ctx.getString(R.string.unit_km, st.meters / 1000.0), st.countries.size))
        for ((day, list) in TravelDiary.byDay(entries)) {
            appendLine()
            appendLine(dayText(day))
            list.forEach { appendLine("• " + diaryLine(ctx, it)) }
        }
    }.trim()
}

/** Reisetagebuch: Zahlen der Reise und alle Erlebnisse Tag fuer Tag; Geschichten lassen sich noch einmal anhoeren. */
@Composable
fun DiaryScreen(state: UiState, nav: NavController) {
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    BackHandler { nav.showDiary(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.screenColumn().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.diary_title), style = MaterialTheme.typography.headlineMedium)
            if (state.diary.isEmpty()) {
                Text(stringResource(R.string.diary_empty), style = MaterialTheme.typography.bodyLarge)
            } else {
                val st = TravelDiary.stats(state.diary)
                Surface(tonalElevation = 3.dp, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.diary_stats_trip, st.days, stringResource(R.string.unit_km, st.meters / 1000.0), st.countries.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (st.countries.isNotEmpty()) Text(st.countries.joinToString(" ") { TravelDiary.flag(it) }, style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.diary_stats_play, st.quests, st.points, st.stickers, st.stories), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TravelDiary.byDay(state.diary).reversed().forEach { (day, list) ->
                    Text(dayText(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    if (list.any { it.kindOrNull == DiaryKind.PHOTO }) {
                        TextButton(onClick = { shareCollage(ctx, day, list) }) { Text(stringResource(R.string.photo_collage_share)) }
                    }
                    list.forEach { e ->
                        if (e.kindOrNull == DiaryKind.PHOTO) {
                            Text(diaryLine(ctx, e), style = MaterialTheme.typography.bodyLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                e.photoFiles().forEach { n ->
                                    val bmp = remember(n) { PhotoStore.thumbnail(ctx, n, 360) }
                                    if (bmp != null) androidx.compose.foundation.Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.weight(1f).height(140.dp))
                                }
                            }
                            TextButton(onClick = { deleteEntry = e }) { Text(stringResource(R.string.diary_delete_one), color = MaterialTheme.colorScheme.error) }
                            return@forEach
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(diaryLine(ctx, e), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (e.kindOrNull == DiaryKind.STORY && e.text.isNotBlank()) {
                                TextButton(onClick = { nav.speakDiaryEntry(e) }) { Text(stringResource(R.string.diary_listen)) }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.story_source), style = MaterialTheme.typography.labelMedium)
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, diaryShareText(ctx, state.diary))
                        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.diary_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.diary_share)) }
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.diary_clear)) }
            }
            OutlinedButton(onClick = { nav.showDiary(false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.kids_back)) }
        }
    }
    deleteEntry?.let { entry ->
        app.kiloquest.ui.ConfirmDialog(
            stringResource(R.string.diary_delete_one), stringResource(R.string.diary_delete_one_msg), stringResource(R.string.dlg_delete), stringResource(R.string.dlg_cancel),
            onConfirm = { nav.deleteDiaryEntry(entry) }, onDismiss = { deleteEntry = null },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            stringResource(R.string.diary_clear), stringResource(R.string.diary_clear_msg), stringResource(R.string.dlg_delete), stringResource(R.string.dlg_cancel),
            onConfirm = nav::clearDiary, onDismiss = { confirmClear = false },
        )
    }
}

/** Karte mit der Hoergeschichte (Kinderansicht): Suche, Text mit Lizenzhinweis oder "nichts gefunden". */
@Composable
fun StoryCardView(card: StoryCard?, nav: KidsActions) {
    if (card == null) return
    Surface(tonalElevation = 3.dp, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                card.loading -> Text(stringResource(R.string.story_loading), style = MaterialTheme.typography.bodyLarge)
                card.failed -> Text(stringResource(R.string.story_none), style = MaterialTheme.typography.bodyLarge)
                else -> {
                    Text(card.title, style = MaterialTheme.typography.titleMedium)
                    Text(card.text, style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.story_source), style = MaterialTheme.typography.labelMedium)
                }
            }
            OutlinedButton(onClick = nav::dismissStory, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.story_close)) }
        }
    }
}

/** Collage(n) des Tages bauen und ueber das Teilen-Menue anbieten (bei mehreren Seiten alle zusammen). */
private fun shareCollage(ctx: Context, day: Int, entries: List<DiaryEntry>) {
    val files = CollageBuilder.build(ctx, day, entries)
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) })
    val send = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.photo_collage_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
