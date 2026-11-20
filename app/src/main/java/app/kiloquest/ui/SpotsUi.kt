package app.kiloquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.NavController
import app.kiloquest.R
import app.kiloquest.StopoverPhase
import app.kiloquest.UiState
import app.kiloquest.nav.OpenState
import app.kiloquest.nav.SpotKind

private fun kindRes(k: SpotKind) = when (k) {
    SpotKind.PLAYGROUND -> R.string.spot_playground
    SpotKind.ICE_CREAM -> R.string.spot_ice_cream
    SpotKind.SWIMMING -> R.string.spot_swimming
    SpotKind.ZOO -> R.string.spot_zoo
    SpotKind.CASTLE -> R.string.spot_castle
}

/** Laufender Zwischenstopp (Fahrt dorthin oder Pause) mit "Weiter zum Ziel". */
@Composable
fun StopoverCard(state: UiState, nav: NavController) {
    val so = state.stopover ?: return
    KiloCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stopover_heading, so.label), style = MaterialTheme.typography.titleMedium)
            if (so.phase == StopoverPhase.ARRIVED) Text(stringResource(R.string.stopover_arrived, so.label), style = MaterialTheme.typography.bodyMedium)
            so.story?.let {
                Text(it.title, style = MaterialTheme.typography.titleSmall)
                Text(it.text, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.story_source), style = MaterialTheme.typography.labelMedium)
            }
            Button(onClick = nav::resumeAfterStopover, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.stopover_continue)) }
        }
    }
}

/** Zwischenstopps fuer Kinder voraus (nur fuer den Beifahrer; der Fahrer bekommt nur Audio). */
@Composable
fun SpotsCard(state: UiState, nav: NavController) {
    if (state.route == null || state.stopover != null) return
    val along = state.guidance?.alongMeters ?: 0.0
    KiloCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.spots_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.spots_hint), style = MaterialTheme.typography.bodySmall)
            when {
                state.spotsLoading -> Text(stringResource(R.string.spots_loading))
                state.spots.isNotEmpty() -> state.spots.forEach { s ->
                    Text(
                        stringResource(kindRes(s.kind)) + (s.name?.let { ": $it" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val line = stringResource(R.string.spots_line, stringResource(kindRes(s.kind)), ((s.alongMeters - along) / 1000).toInt().coerceAtLeast(1), s.detourMinutes)
                    Text(line + if (s.open == OpenState.UNKNOWN) " · " + stringResource(R.string.spots_unsure) else "", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { nav.goToSpot(s) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spots_go)) }
                }
                state.spotsSearched -> Text(stringResource(R.string.spots_none), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = { nav.findSpots() }, enabled = !state.spotsLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spots_find)) }
        }
    }
}

/** Naechste Pause und Wetter am Ziel: kleine Ergaenzung fuer das Beifahrer-Dashboard. */
@Composable
fun DashExtras(state: UiState, nav: NavController) {
    val lines = buildList {
        if (state.powerSaving) add(stringResource(R.string.dash_power_saving))
        state.nextBreakMin?.takeIf { state.navigating }?.let { add(stringResource(R.string.dash_next_break, it)) }
        state.weather?.destination?.let { d ->
            val t = d.tempC?.let { Math.round(it).toInt().toString() + " °C, " }.orEmpty()
            add(stringResource(R.string.dash_dest_weather, t + nav.conditionName(d.condition)))
        }
    }
    if (lines.isEmpty()) return
    KiloCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
