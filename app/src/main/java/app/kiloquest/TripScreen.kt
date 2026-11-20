package app.kiloquest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.nav.TripApi

@Composable
private fun distanceText(meters: Double): String =
    if (meters >= 1000) stringResource(R.string.unit_km, meters / 1000.0) else stringResource(R.string.unit_m, meters.toInt())

@Composable
private fun durationText(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes >= 60) stringResource(R.string.unit_h_min, minutes / 60, minutes % 60) else stringResource(R.string.unit_min, minutes)
}

/**
 * Mehrtagesreise: Etappenziele setzen, Laenge/Dauer je Etappe sehen, Etappe starten.
 * Jede Etappe ist eine eigene Route mit eigenem Abenteuer (Punkte pro Tag).
 */
@Composable
fun TripScreen(state: UiState, nav: NavController) {
    var query by remember { mutableStateOf("") }
    val trip = state.trip
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.trip_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.trip_hint), style = MaterialTheme.typography.bodyMedium)

            if (trip != null && trip.legs.isNotEmpty()) {
                val total = TripApi.totals(trip.legs)
                Text(
                    stringResource(R.string.trip_total, trip.stops.size, distanceText(total.lengthMeters), durationText(total.durationSeconds)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            trip?.stops?.forEachIndexed { i, stop ->
                val done = i < trip.currentStage
                // legs beginnen bei der aktuellen Etappe
                val leg = trip.legs.getOrNull(i - trip.currentStage)
                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.trip_stage, i + 1, stop.name), style = MaterialTheme.typography.titleMedium)
                        when {
                            done -> Text(stringResource(R.string.trip_stage_done_label, trip.stagePoints.getOrElse(i) { 0 }))
                            leg != null -> {
                                Text(stringResource(R.string.trip_stage_info, distanceText(leg.lengthMeters), durationText(leg.durationSeconds)))
                                if (TripApi.isLongStage(leg)) {
                                    Text(stringResource(R.string.trip_long_day), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (!done) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (i == trip.currentStage) {
                                    Button(onClick = { nav.startStage(i) }) { Text(stringResource(R.string.trip_start_stage, i + 1)) }
                                }
                                OutlinedButton(onClick = { nav.removeTripStop(i) }) { Text(stringResource(R.string.trip_remove)) }
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.trip_add_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { nav.search(query) }, modifier = Modifier.padding(start = 8.dp), enabled = !state.busy) {
                    Text(stringResource(R.string.search_action))
                }
            }
            state.results.forEach { place ->
                Text(
                    place.name,
                    modifier = Modifier.fillMaxWidth().clickable { nav.addTripStop(place); query = "" }.padding(vertical = 10.dp),
                )
            }
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

            if (trip != null) {
                OutlinedButton(onClick = nav::clearTrip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.trip_clear)) }
            }
            OutlinedButton(onClick = nav::clearOfflineMaps, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pkg_clear)) }
            OutlinedButton(onClick = { nav.showTrip(false); nav.clearResults() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.kids_back))
            }
        }
    }
}
