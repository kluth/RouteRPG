package app.kiloquest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.game.AgeBand
import app.kiloquest.game.QuestKind
import app.kiloquest.game.StickerBook

/**
 * Kinderansicht (Handy/Tablet, nicht im Auto-Display): aktuelle Aufgabe, Punkte, Sticker.
 * Grosse Schrift und grosse Buttons; der Fahrer bekommt nur die Sprachansage.
 */
@Composable
fun KidsScreen(state: UiState, nav: NavController) {
    val ctx = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.kids_title), style = MaterialTheme.typography.headlineLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(R.string.kids_points, state.points), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.kids_stickers, state.stickers), style = MaterialTheme.typography.titleLarge)
            }
            if (state.questsTotal > 0) {
                Text(
                    stringResource(R.string.kids_progress, state.questsDone.coerceAtMost(state.questsTotal), state.questsTotal),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            if (state.players > 1) Scoreboard(state)

            if (!state.navigating) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(checked = state.sicknessMode, onCheckedChange = nav::setSicknessMode)
                    Text(stringResource(R.string.kids_sickness_mode), style = MaterialTheme.typography.titleMedium)
                }
                AgePicker(state.ageBand, nav::setAgeBand)
                PlayerPicker(state.players, nav::setPlayers)
            }

            val q = state.quest
            val fb = state.feedback
            when {
                // Reiseuebelkeit-Modus: auf kurvigen Abschnitten kein Bildschirm, stattdessen Blick nach draussen
                state.navigating && state.curvy -> {
                    Text(
                        stringResource(R.string.sickness_look_out),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                q != null -> {
                    if (state.players > 1) {
                        Text(
                            stringResource(R.string.kids_turn, stringResource(R.string.kids_player, state.currentPlayer + 1)),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(ctx.stringByKey(q.textKey, q.args), style = MaterialTheme.typography.headlineMedium)
                    if (q.type == app.kiloquest.game.QuestType.SKY && q.args.isNotEmpty() && q.textKey != app.kiloquest.game.PlaneSpotting.MOON_KEY) {
                        Text(stringResource(R.string.sky_source), style = MaterialTheme.typography.labelMedium)
                    }
                    if (q.type == app.kiloquest.game.QuestType.NATURE && q.args.isNotEmpty()) {
                        Text(stringResource(R.string.nature_source), style = MaterialTheme.typography.labelMedium)
                    }
                    if (q.textKey == app.kiloquest.game.WikiStories.TEXT_KEY) {
                        // Lizenzhinweis: Wikipedia-Texte stehen unter CC BY-SA
                        Text(stringResource(R.string.story_source), style = MaterialTheme.typography.labelMedium)
                    }
                    if (q.kind == QuestKind.CHOICE) {
                        q.optionKeys.forEachIndexed { i, key ->
                            Button(onClick = { nav.answerQuest(i) }, modifier = Modifier.fillMaxWidth()) {
                                Text(ctx.stringByKey(key), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    } else {
                        Button(onClick = { nav.answerQuest() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.kids_found), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    OutlinedButton(onClick = nav::skipQuest, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.kids_skip))
                    }
                }
                fb != null -> {
                    val msg = when {
                        fb.correctOptionKey == null -> stringResource(R.string.kids_done, fb.points)
                        fb.correct -> stringResource(R.string.kids_correct, fb.points)
                        else -> stringResource(R.string.kids_wrong, ctx.stringByKey(fb.correctOptionKey))
                    }
                    Text(msg, style = MaterialTheme.typography.headlineMedium)
                    state.newSticker?.let {
                        Text(
                            stringResource(R.string.kids_new_sticker) + " " + it.emoji,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Button(onClick = nav::dismissFeedback, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.kids_next))
                    }
                }
                state.route == null -> Text(stringResource(R.string.kids_need_route), style = MaterialTheme.typography.titleMedium)
                else -> Text(stringResource(R.string.kids_waiting), style = MaterialTheme.typography.titleMedium)
            }

            Album(state.album)
            Text(stringResource(R.string.kids_total, state.totalPoints), style = MaterialTheme.typography.bodyMedium)

            if (!state.navigating) {
                OutlinedButton(onClick = { nav.showKids(false); nav.showRiddles(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.riddle_open))
                }
            }
            OutlinedButton(onClick = { nav.showKids(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.kids_back))
            }
        }
    }
}

@Composable
private fun AgePicker(selected: AgeBand, onSelect: (AgeBand) -> Unit) {
    Text(stringResource(R.string.kids_age), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (band in AgeBand.entries) {
            FilterChip(
                selected = band == selected,
                onClick = { onSelect(band) },
                label = {
                    Text(
                        stringResource(
                            when (band) {
                                AgeBand.MINI -> R.string.age_mini
                                AgeBand.EXPLORER -> R.string.age_explorer
                                AgeBand.PRO -> R.string.age_pro
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun PlayerPicker(selected: Int, onSelect: (Int) -> Unit) {
    Text(stringResource(R.string.kids_players), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (n in 1..4) {
            FilterChip(selected = n == selected, onClick = { onSelect(n) }, label = { Text(n.toString()) })
        }
    }
}

@Composable
private fun Scoreboard(state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.scores.forEachIndexed { i, pts ->
            val active = state.quest != null && i == state.currentPlayer
            Text(
                stringResource(R.string.kids_player, i + 1) + ": " + stringResource(R.string.kids_points, pts),
                style = if (active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Album(collected: Set<String>) {
    Text(
        stringResource(R.string.kids_album, collected.size, StickerBook.ALL.size),
        style = MaterialTheme.typography.titleMedium,
    )
    // Gesammelte Sticker als Emoji, fehlende als graue Punkte
    Text(
        StickerBook.ALL.joinToString(" ") { if (it.id in collected) it.emoji else "·" },
        style = MaterialTheme.typography.headlineSmall,
    )
}
