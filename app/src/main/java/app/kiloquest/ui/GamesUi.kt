package app.kiloquest.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.kiloquest.NavController
import app.kiloquest.R
import app.kiloquest.UiState
import app.kiloquest.game.AudioGameType
import app.kiloquest.game.KiloSeries
import app.kiloquest.game.TeamGoal

/** Gemeinsames Teamziel der Familie: ein Fortschrittsbalken statt Rangliste. */
@Composable
fun TeamGoalBar(goal: TeamGoal, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.team_goal_title) + ": " + stringResource(R.string.team_goal_progress, goal.collected.coerceAtMost(goal.target), goal.target)
    Column(modifier.semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { goal.progress.toFloat() }, modifier = Modifier.fillMaxWidth().heightIn(min = 10.dp))
    }
}

/** Audio-Spiele (ohne Bildschirm), Hoerserie und Teamziel fuer das Beifahrer-Geraet. */
@Composable
fun GamesCard(state: UiState, nav: NavController) {
    val ctx = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val p = pending; pending = null
        if (ok && p != null) nav.startAudioGame(AudioGameType.valueOf(p))
    }
    fun start(t: AudioGameType) {
        if (t == AudioGameType.STORY_RELAY || ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) nav.startAudioGame(t)
        else { pending = t.name; mic.launch(Manifest.permission.RECORD_AUDIO) }
    }
    KiloCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.teamGoal?.let { TeamGoalBar(it) }
            Text(stringResource(R.string.games_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.games_hint), style = MaterialTheme.typography.bodySmall)
            if (state.audioGame != null) {
                Text(stringResource(R.string.game_running), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = nav::stopAudioGame, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.game_stop)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ start(AudioGameType.WORD_CHAIN) }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.game_word_chain)) }
                    OutlinedButton({ start(AudioGameType.LETTER_CATEGORY) }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.game_letters)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ start(AudioGameType.RHYME) }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.game_rhyme)) }
                    OutlinedButton({ start(AudioGameType.STORY_RELAY) }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.game_story)) }
                }
            }
            Text(stringResource(R.string.series_title) + " (" + state.seriesHeard + "/" + KiloSeries.EPISODES + ")", style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.series_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = nav::tellSeries, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.series_play) + if (state.seriesNext != null) " (" + state.seriesNext + ")" else "")
            }
        }
    }
}
