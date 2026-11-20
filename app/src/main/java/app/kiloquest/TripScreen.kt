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
import app.kiloquest.ui.screenColumn
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.nav.TripApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.annotation.StringRes
import app.kiloquest.nav.LodgingKind

@StringRes
private fun lodgingKindLabel(k: LodgingKind): Int = when (k) {
    LodgingKind.HOTEL -> R.string.lodging_hotel
    LodgingKind.MOTEL -> R.string.lodging_motel
    LodgingKind.GUEST_HOUSE -> R.string.lodging_guest_house
    LodgingKind.APARTMENT -> R.string.lodging_apartment
    LodgingKind.HOSTEL -> R.string.lodging_hostel
    LodgingKind.CAMPING -> R.string.lodging_camping
}

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
            Modifier.screenColumn().padding(16.dp),
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
                                if (i < trip.stops.lastIndex) {
                                    OutlinedButton(onClick = { nav.findLodging(i) }) { Text(stringResource(R.string.lodging_find)) }
                                }
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
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(place.name, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { nav.addTripStop(place); query = "" }) { Text(stringResource(R.string.stage_add)) }
                        TextButton(onClick = { nav.addBooked(place); query = "" }) { Text(stringResource(R.string.booked_add)) }
                        TextButton(onClick = { nav.addFavorite(place) }) { Text(stringResource(R.string.fav_save)) }
                    }
                }
            }
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

            Text(stringResource(R.string.booked_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.booked_hint), style = MaterialTheme.typography.bodySmall)
            if (state.booked.isEmpty()) Text(stringResource(R.string.booked_empty), style = MaterialTheme.typography.bodyMedium)
            state.booked.forEach { p ->
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, Modifier.weight(1f))
                        TextButton(onClick = { nav.removeBooked(p) }) { Text(stringResource(R.string.trip_remove)) }
                    }
                }
            }
            Text(stringResource(R.string.fav_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.fav_hint), style = MaterialTheme.typography.bodySmall)
            if (state.favorites.isEmpty()) Text(stringResource(R.string.fav_empty), style = MaterialTheme.typography.bodyMedium)
            state.favorites.forEach { p ->
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { nav.addTripStop(p) }) { Text(stringResource(R.string.stage_add)) }
                            TextButton(onClick = { nav.addBooked(p) }) { Text(stringResource(R.string.booked_add)) }
                            TextButton(onClick = { nav.removeFavorite(p) }) { Text(stringResource(R.string.fav_remove)) }
                        }
                    }
                }
            }
            state.tripNotes.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.route != null && state.destination != null && (trip == null || trip.stops.isEmpty())) {
                Text(stringResource(R.string.trip_split_hint), style = MaterialTheme.typography.bodyMedium)
                if (state.booked.isNotEmpty()) Text(stringResource(R.string.trip_split_booked_info, state.booked.size), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = nav::splitRouteIntoDays, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) {
                    Text(stringResource(R.string.trip_split))
                }
            }
            if (trip != null) {
                OutlinedButton(onClick = nav::clearTrip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.trip_clear)) }
            }
            OutlinedButton(onClick = nav::clearOfflineMaps, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pkg_clear)) }
            OutlinedButton(onClick = { nav.showTrip(false); nav.clearResults() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.kids_back))
            }
        }
    }
    state.lodging?.let { ui ->
        AlertDialog(
            onDismissRequest = nav::closeLodging,
            title = { Text(stringResource(R.string.lodging_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        ui.loading -> Text(stringResource(R.string.lodging_loading))
                        ui.failed -> Text(stringResource(R.string.lodging_failed))
                        ui.options.isEmpty() -> Text(stringResource(R.string.lodging_none))
                    }
                    ui.options.forEach { o ->
                        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(o.name, style = MaterialTheme.typography.titleSmall)
                                val kind = stringResource(lodgingKindLabel(o.kind))
                                Text(
                                    if (o.stars != null) kind + " · " + stringResource(R.string.lodging_stars, o.stars!!) else kind,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { nav.chooseLodging(o) }) { Text(stringResource(R.string.lodging_take)) }
                                    TextButton(onClick = { nav.addFavorite(app.kiloquest.Place(o.name, o.position)) }) { Text(stringResource(R.string.fav_save)) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = nav::closeLodging) { Text(stringResource(R.string.dlg_close_short)) } },
        )
    }
}
