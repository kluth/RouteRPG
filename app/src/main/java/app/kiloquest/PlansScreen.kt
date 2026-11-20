package app.kiloquest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.nav.IssueKind
import app.kiloquest.nav.PlannedTrip
import app.kiloquest.ui.screenColumn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun dateText(day: Int): String =
    LocalDate.ofEpochDay(day.toLong()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private fun daysUntil(day: Int): Int = (day - LocalDate.now().toEpochDay()).toInt()

@Composable
private fun kmText(meters: Double) = stringResource(R.string.unit_km, meters / 1000.0)

@Composable
private fun hoursText(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes >= 60) stringResource(R.string.unit_h_min, minutes / 60, minutes % 60) else stringResource(R.string.unit_min, minutes)
}

/** Datumsfeld: zeigt das Datum und oeffnet bei Tipp eine Kalenderauswahl. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, day: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) { Text("$label: ${dateText(day)}", maxLines = 1) }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = day * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { onPick((it / 86_400_000L).toInt()) }
                    open = false
                }) { Text(stringResource(R.string.dlg_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.dlg_cancel)) } },
        ) { DatePicker(state = st) }
    }
}

/** Fest geplante Reisen: Liste und Editor mit Buchungen, Terminplanung und Uebernachtungsvorschlaegen. */
@Composable
fun PlansScreen(state: UiState, nav: NavController) {
    val open = state.plans.firstOrNull { it.id == state.planOpenId }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.screenColumn().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (open == null) PlanList(state, nav) else PlanEditor(state, nav, open)
        }
    }
    state.lodging?.takeIf { it.planDay != null }?.let { ui ->
        AlertDialog(
            onDismissRequest = nav::closeLodging,
            title = { Text(stringResource(R.string.lodging_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        ui.loading -> Text(stringResource(R.string.lodging_loading))
                        ui.failed -> Text(stringResource(R.string.lodging_failed))
                        ui.options.isEmpty() -> Text(stringResource(R.string.lodging_none))
                    }
                    ui.options.take(6).forEach { o ->
                        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(o.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { nav.chooseLodging(o) }) { Text(stringResource(R.string.plan_book)) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = nav::closeLodging) { Text(stringResource(R.string.dlg_close_short)) } },
        )
    }
}

@Composable
private fun PlanList(state: UiState, nav: NavController) {
    Text(stringResource(R.string.plans_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.plans_hint), style = MaterialTheme.typography.bodyMedium)
    if (state.plans.isEmpty()) Text(stringResource(R.string.plans_empty))
    state.plans.sortedBy { it.startDay }.forEach { p ->
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.name.ifBlank { p.destination?.name ?: stringResource(R.string.plan_unnamed) }, style = MaterialTheme.typography.titleMedium)
                Text(dateText(p.startDay) + " · " + (p.origin?.name ?: "?") + " → " + (p.destination?.name ?: "?"))
                val d = daysUntil(p.startDay)
                if (d >= 0) Text(pluralStringResource(R.plurals.plan_in_days, d, d), style = MaterialTheme.typography.bodySmall)
                if (p.stays.isNotEmpty()) Text(stringResource(R.string.plan_stays_count, p.stays.size), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { nav.openPlan(p.id) }) { Text(stringResource(R.string.plan_open)) }
            }
        }
    }
    Button(onClick = nav::newPlan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_new)) }
    OutlinedButton(onClick = { nav.showPlans(false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.kids_back)) }
}

@Composable
private fun PlanEditor(state: UiState, nav: NavController, plan: PlannedTrip) {
    var query by remember { mutableStateOf("") }
    Text(stringResource(R.string.plan_edit_title), style = MaterialTheme.typography.headlineMedium)
    OutlinedTextField(
        value = plan.name, onValueChange = { v -> nav.updatePlan { it.copy(name = v) } },
        label = { Text(stringResource(R.string.plan_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    DateField(stringResource(R.string.plan_start_date), plan.startDay, { d -> nav.updatePlan { it.copy(startDay = d) } }, Modifier.fillMaxWidth())

    Text(stringResource(R.string.plan_from) + ": " + (plan.origin?.name ?: stringResource(R.string.plan_not_set)))
    Text(stringResource(R.string.plan_to) + ": " + (plan.destination?.name ?: stringResource(R.string.plan_not_set)))
    if (state.position != null) {
        OutlinedButton(onClick = nav::setPlanOriginHere) { Text(stringResource(R.string.plan_use_position)) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.plan_search_hint)) }, singleLine = true, modifier = Modifier.weight(1f),
        )
        Button(onClick = { nav.search(query) }, modifier = Modifier.padding(start = 8.dp), enabled = !state.busy) { Text(stringResource(R.string.search_action)) }
    }
    state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    (state.results + state.favorites.filter { query.isBlank() }).distinctBy { it.name to it.position }.forEach { place ->
        Column(Modifier.fillMaxWidth()) {
            Text(place.name, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { nav.setPlanPlace(place, true) }) { Text(stringResource(R.string.plan_from)) }
                TextButton(onClick = { nav.setPlanPlace(place, false) }) { Text(stringResource(R.string.plan_to)) }
                TextButton(onClick = { nav.addPlanStay(place); nav.clearResults() }) { Text(stringResource(R.string.booked_add)) }
                if (state.favorites.none { it.name == place.name }) TextButton(onClick = { nav.addFavorite(place) }) { Text(stringResource(R.string.fav_save)) }
            }
        }
    }

    Text(stringResource(R.string.booked_title), style = MaterialTheme.typography.titleMedium)
    if (plan.stays.isEmpty()) Text(stringResource(R.string.booked_empty))
    plan.stays.forEachIndexed { i, s ->
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall)
                DateField(stringResource(R.string.plan_check_in), s.checkInDay, { d -> nav.changePlanStay(i) { it.copy(checkInDay = d) } }, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.changePlanStay(i) { it.copy(nights = (it.nights - 1).coerceAtLeast(1)) } }) { Text("−") }
                    Text(pluralStringResource(R.plurals.plan_nights, s.nights, s.nights))
                    OutlinedButton(onClick = { nav.changePlanStay(i) { it.copy(nights = (it.nights + 1).coerceAtMost(60)) } }) { Text("+") }
                    TextButton(onClick = { nav.clearPlanStay(i) }) { Text(stringResource(R.string.trip_remove)) }
                }
            }
        }
    }

    Button(onClick = nav::calculatePlan, modifier = Modifier.fillMaxWidth(), enabled = state.planResult?.loading != true) { Text(stringResource(R.string.plan_calculate)) }
    state.planResult?.let { PlanResultView(it, nav) }

    OutlinedButton(onClick = { nav.deletePlan(plan.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_delete)) }
    OutlinedButton(onClick = { nav.openPlan(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.kids_back)) }
}

@Composable
private fun PlanResultView(res: PlanResult, nav: NavController) {
    if (res.loading) { Text(stringResource(R.string.plan_loading)); return }
    res.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error); return }
    val it = res.itinerary ?: return
    Text(stringResource(R.string.plan_result_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.plan_arrival, dateText(it.arrivalDay)), style = MaterialTheme.typography.bodyMedium)
    res.extraMinutes?.let { m ->
        Text(
            stringResource(if (m <= 10) R.string.plan_detour_none else R.string.plan_detour, m),
            color = if (m <= 10) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
    res.offRouteKm.filter { (_, km) -> km >= 15 }.forEach { (name, km) -> Text(stringResource(R.string.trip_note_far, name, km), color = MaterialTheme.colorScheme.error) }
    it.issues.forEach { issue ->
        val leg = it.legs[issue.legIndex]
        Text(
            when (issue.kind) {
                IssueKind.OVERLAP -> stringResource(R.string.plan_issue_overlap, leg.toName)
                IssueKind.TOO_FEW_DAYS -> pluralStringResource(R.plurals.plan_issue_days, issue.missingDays, leg.fromName, leg.toName, issue.missingDays)
            },
            color = MaterialTheme.colorScheme.error,
        )
    }
    it.legs.forEach { l ->
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(l.fromName + " → " + l.toName, style = MaterialTheme.typography.titleSmall)
                Text(kmText(l.lengthMeters) + " · " + hoursText(l.durationSeconds) + " · " + pluralStringResource(R.plurals.plan_drive_days, l.driveDays, l.driveDays))
                Text(dateText(l.departDay) + " – " + dateText(l.arriveDay), style = MaterialTheme.typography.bodySmall)
                if (l.spareDays > 0) Text(pluralStringResource(R.plurals.plan_spare_days, l.spareDays, l.spareDays), style = MaterialTheme.typography.bodySmall)
                res.nights.filter { n -> n.legIndex == l.index }.forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.plan_night_open, dateText(n.day), n.name.ifBlank { stringResource(R.string.plan_on_route) }),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { nav.findPlanLodging(n) }) { Text(stringResource(R.string.lodging_find)) }
                    }
                }
            }
        }
    }
    if (it.ok) Button(onClick = nav::usePlanAsTrip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_use_as_trip)) }
}
