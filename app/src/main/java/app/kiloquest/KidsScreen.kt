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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.game.AgeBand
import app.kiloquest.game.QuestKind

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

            if (!state.navigating) AgePicker(state.ageBand, nav::setAgeBand)

            val q = state.quest
            val fb = state.feedback
            when {
                q != null -> {
                    Text(ctx.stringByKey(q.textKey), style = MaterialTheme.typography.headlineMedium)
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
                    Button(onClick = nav::dismissFeedback, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.kids_next))
                    }
                }
                state.route == null -> Text(stringResource(R.string.kids_need_route), style = MaterialTheme.typography.titleMedium)
                else -> Text(stringResource(R.string.kids_waiting), style = MaterialTheme.typography.titleMedium)
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
