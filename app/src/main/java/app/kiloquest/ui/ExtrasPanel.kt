package app.kiloquest.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.kiloquest.R
import app.kiloquest.game.AudioGameType
import app.kiloquest.game.KiloSeries
import app.kiloquest.game.TeamGoal
import app.kiloquest.link.ExtrasDto
import app.kiloquest.nav.OpenState
import app.kiloquest.nav.SpotKind
import app.kiloquest.nav.TollGuide
import app.kiloquest.stringByKey
import app.kiloquest.weatherConditionRes

/** Aktionen des Beifahrer-Bereichs: auf dem Hauptgeraet direkt, auf einem gekoppelten Beifahrer-Geraet als Bluetooth-Befehl. */
interface ExtrasActions {
    fun findSpots()
    fun goToSpot(index: Int)
    fun resumeStopover()
    fun startGame(type: AudioGameType)
    fun stopGame()
    fun series()
    fun emergency()
    fun tolls()
}

private fun kindRes(k: String) = when (runCatching { SpotKind.valueOf(k) }.getOrDefault(SpotKind.PLAYGROUND)) {
    SpotKind.PLAYGROUND -> R.string.spot_playground
    SpotKind.ICE_CREAM -> R.string.spot_ice_cream
    SpotKind.SWIMMING -> R.string.spot_swimming
    SpotKind.ZOO -> R.string.spot_zoo
    SpotKind.CASTLE -> R.string.spot_castle
}

/**
 * Alles rund um die Reise fuer den Beifahrer: Notfall, Zwischenstopp, naechste Pause und Zielwetter, Stopps fuer Kinder, Maut und lange Strecken
 * ohne Orte, Teamziel, Hoerspiele und Hoerserie. Gleiche Ansicht auf dem Hauptgeraet und auf dem gekoppelten Beifahrer-Geraet.
 * [micOnThisDevice]: Spiele hoert das Hauptgeraet zu; nur dort wird die Mikrofon-Erlaubnis abgefragt.
 */
@Composable
fun ExtrasPanel(x: ExtrasDto, actions: ExtrasActions, navigating: Boolean, micOnThisDevice: Boolean) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = actions::emergency, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.emergency_open)) }

        // Zwischenstopp laeuft
        val label = x.stopoverLabel
        if (label != null) {
            KiloCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.stopover_heading, label), style = MaterialTheme.typography.titleMedium)
                    if (x.stopoverArrived) Text(stringResource(R.string.stopover_arrived, label), style = MaterialTheme.typography.bodyMedium)
                    x.stopoverStory?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.story_source), style = MaterialTheme.typography.labelMedium)
                    }
                    Button(onClick = actions::resumeStopover, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.stopover_continue)) }
                }
            }
        }

        // naechste Pause, Zielwetter, Sparmodus
        val lines = buildList {
            if (x.powerSaving) add(stringResource(R.string.dash_power_saving))
            x.nextBreakMin?.let { add(stringResource(R.string.dash_next_break, it)) }
            x.destCondition?.let { c ->
                val cond = runCatching { app.kiloquest.nav.WeatherCondition.valueOf(c) }.getOrNull()
                if (cond != null) add(stringResource(R.string.dash_dest_weather, (x.destTempC?.let { "$it °C, " } ?: "") + stringResource(weatherConditionRes(cond))))
            }
        }
        if (lines.isNotEmpty()) KiloCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) } } }

        // Stopps fuer Kinder
        if (navigating && x.stopoverLabel == null) {
            KiloCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.spots_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.spots_hint), style = MaterialTheme.typography.bodySmall)
                    when {
                        x.spotsLoading -> Text(stringResource(R.string.spots_loading))
                        x.spots.isNotEmpty() -> x.spots.forEachIndexed { i, s ->
                            val kind = stringResource(kindRes(s.kind))
                            Text(kind + (s.name?.let { ": $it" } ?: ""), style = MaterialTheme.typography.titleSmall)
                            val line = stringResource(R.string.spots_line, kind, (s.aheadMeters / 1000).toInt().coerceAtLeast(1), s.detourMinutes)
                            Text(line + if (s.open == OpenState.UNKNOWN.name) " · " + stringResource(R.string.spots_unsure) else "", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { actions.goToSpot(i) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spots_go)) }
                        }
                        x.spotsSearched -> Text(stringResource(R.string.spots_none), style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(onClick = actions::findSpots, enabled = !x.spotsLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spots_find)) }
                }
            }
        }

        // Maut und lange Strecken ohne Orte
        if (x.tolls.isNotEmpty() || x.remote.isNotEmpty()) {
            KiloCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (x.tolls.isNotEmpty()) {
                        Text(stringResource(R.string.toll_title), style = MaterialTheme.typography.titleMedium)
                        x.tolls.forEach { cc ->
                            Text(ctx.stringByKey(TollGuide.textKey(cc)), style = MaterialTheme.typography.bodyMedium)
                            TollGuide.rule(cc)?.url?.let { url ->
                                TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.toll_open) + " (" + cc + ")")
                                }
                            }
                        }
                        Text(stringResource(R.string.toll_asof, TollGuide.AS_OF), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = actions::tolls, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.toll_speak)) }
                    }
                    if (x.remote.isNotEmpty()) {
                        Text(stringResource(R.string.remote_title), style = MaterialTheme.typography.titleMedium)
                        x.remote.forEach { Text(stringResource(R.string.remote_item, it.fromKm, it.toKm, it.toKm - it.fromKm), style = MaterialTheme.typography.bodyMedium) }
                        Text(stringResource(R.string.remote_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        GamesBlock(x, actions, micOnThisDevice)
    }
}

@Composable
private fun GamesBlock(x: ExtrasDto, actions: ExtrasActions, micOnThisDevice: Boolean) {
    val ctx = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val p = pending; pending = null
        if (ok && p != null) actions.startGame(AudioGameType.valueOf(p))
    }
    fun start(t: AudioGameType) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (t == AudioGameType.STORY_RELAY || !micOnThisDevice || granted) actions.startGame(t) else { pending = t.name; mic.launch(Manifest.permission.RECORD_AUDIO) }
    }
    KiloCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (x.goalTarget > 0) TeamGoalBar(TeamGoal(x.goalTarget, x.goalCollected))
            Text(stringResource(R.string.games_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.games_hint), style = MaterialTheme.typography.bodySmall)
            if (x.game != null) {
                Text(stringResource(R.string.game_running), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = actions::stopGame, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.game_stop)) }
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
            Text(stringResource(R.string.series_title) + " (" + x.seriesHeard + "/" + KiloSeries.EPISODES + ")", style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.series_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = actions::series, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.series_play) + if (x.seriesNext != null) " (" + x.seriesNext + ")" else "")
            }
        }
    }
}
