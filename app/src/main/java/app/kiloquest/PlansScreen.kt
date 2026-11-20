package app.kiloquest

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import app.kiloquest.game.AgeBand
import app.kiloquest.nav.IcsExport
import app.kiloquest.nav.PackCategory
import app.kiloquest.nav.PackContext
import app.kiloquest.nav.PackItem
import app.kiloquest.nav.PackingList
import app.kiloquest.nav.TripReminders
import java.io.File
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
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Button(
        onClick = {
            nav.newPlan()
            // Erinnerungen brauchen unter Android 13+ die Benachrichtigungs-Erlaubnis
            if (android.os.Build.VERSION.SDK_INT >= 33 && state.settings.tripReminders &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.plan_new)) }
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
                OutlinedTextField(
                    value = s.note, onValueChange = { v -> nav.changePlanStay(i) { it.copy(note = v.take(300)) } },
                    label = { Text(stringResource(R.string.plan_note)) }, modifier = Modifier.fillMaxWidth(),
                )
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

    PackingSection(state, nav, plan)
    val ctx = LocalContext.current
    OutlinedButton(onClick = { shareCalendar(ctx, plan, state.planResult?.itinerary?.arrivalDay, ctx.getString(R.string.plan_export_title)) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.plan_export))
    }
    val returnPrefix = stringResource(R.string.plan_return_prefix)
    val copySuffix = stringResource(R.string.plan_copy_suffix)
    OutlinedButton(onClick = { nav.createReturnTrip(returnPrefix) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_return)) }
    OutlinedButton(onClick = { nav.duplicatePlan(copySuffix) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_duplicate)) }
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

@StringRes
private fun packLabel(i: PackItem): Int = when (i) {
    PackItem.ID_PAPERS -> R.string.pack_id_papers
    PackItem.BOOKINGS -> R.string.pack_bookings
    PackItem.INSURANCE_CARD -> R.string.pack_insurance_card
    PackItem.CASH_CARDS -> R.string.pack_cash_cards
    PackItem.RAIN_JACKET -> R.string.pack_rain_jacket
    PackItem.SPARE_CLOTHES -> R.string.pack_spare_clothes
    PackItem.WARM_LAYERS -> R.string.pack_warm_layers
    PackItem.WINTER_GEAR -> R.string.pack_winter_gear
    PackItem.SUN_PROTECTION -> R.string.pack_sun_protection
    PackItem.SWIMWEAR -> R.string.pack_swimwear
    PackItem.FIRST_AID -> R.string.pack_first_aid
    PackItem.MEDICINE -> R.string.pack_medicine
    PackItem.TRAVEL_SICKNESS -> R.string.pack_travel_sickness
    PackItem.MOSQUITO -> R.string.pack_mosquito
    PackItem.CHARGERS -> R.string.pack_chargers
    PackItem.SNACKS_WATER -> R.string.pack_snacks_water
    PackItem.WARNING_TRIANGLE -> R.string.pack_warning_triangle
    PackItem.TRASH_BAGS -> R.string.pack_trash_bags
    PackItem.PILLOWS_BLANKETS -> R.string.pack_pillows_blankets
    PackItem.CUDDLY_TOY -> R.string.pack_cuddly_toy
    PackItem.AUDIO_BOOKS -> R.string.pack_audio_books
    PackItem.DIAPERS -> R.string.pack_diapers
    PackItem.GAMES_BOOKS -> R.string.pack_games_books
    PackItem.TENT_GEAR -> R.string.pack_tent_gear
    PackItem.SLEEPING_BAGS -> R.string.pack_sleeping_bags
    PackItem.HEADLAMP -> R.string.pack_headlamp
}

@StringRes
private fun packCategoryLabel(c: PackCategory): Int = when (c) {
    PackCategory.DOCUMENTS -> R.string.packcat_documents
    PackCategory.CLOTHES -> R.string.packcat_clothes
    PackCategory.HEALTH -> R.string.packcat_health
    PackCategory.CAR -> R.string.packcat_car
    PackCategory.KIDS -> R.string.packcat_kids
    PackCategory.CAMPING -> R.string.packcat_camping
}

/** Packliste passend zur Reise (Jahreszeit, Ziel, Dauer, Kinderalter, Camping) mit Haken, die gespeichert bleiben. */
@Composable
private fun PackingSection(state: UiState, nav: NavController, plan: PlannedTrip) {
    val itin = state.planResult?.itinerary
    val ctx = PackContext(
        destinationLat = plan.destination?.lat,
        month = LocalDate.ofEpochDay(plan.startDay.toLong()).monthValue,
        nights = plan.stays.sumOf { it.nights } + (state.planResult?.nights?.size ?: 0),
        drivingDays = itin?.legs?.sumOf { it.driveDays } ?: 0,
        smallKids = state.ageBand == AgeBand.MINI,
        camping = plan.camping,
    )
    val items = PackingList.build(ctx)
    val packedCount = items.count { it.name in plan.packed }
    Text(stringResource(R.string.plan_pack_title), style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = plan.camping, onCheckedChange = nav::setPlanCamping)
        Text(stringResource(R.string.plan_camping), Modifier.padding(start = 8.dp))
    }
    Text(stringResource(R.string.plan_pack_progress, packedCount, items.size), style = MaterialTheme.typography.bodySmall)
    PackCategory.entries.forEach { cat ->
        val inCat = items.filter { it.category == cat }
        if (inCat.isNotEmpty()) {
            Text(stringResource(packCategoryLabel(cat)), style = MaterialTheme.typography.titleSmall)
            inCat.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.name in plan.packed, onCheckedChange = { nav.togglePacked(item) })
                    Text(stringResource(packLabel(item)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Kalenderdatei (.ics) erzeugen und ueber das Teilen-Menue anbieten (Kalender-App, Mail, Messenger). */
private fun shareCalendar(ctx: Context, trip: PlannedTrip, arrivalDay: Int?, title: String) {
    val stamp = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "trip.ics")
    file.writeText(IcsExport.build(trip, arrivalDay, stamp), Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType("text/calendar").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Hinweiskarte auf dem Startbildschirm: naechste Reise in hoechstens 30 Tagen, am Abreisetag hervorgehoben. */
@Composable
fun UpcomingTripCard(state: UiState, nav: NavController, modifier: Modifier = Modifier) {
    val today = LocalDate.now().toEpochDay().toInt()
    val t = TripReminders.next(state.plans, today) ?: return
    val left = t.startDay - today
    if (left > 30) return
    val name = t.name.ifBlank { t.destination?.name ?: stringResource(R.string.plan_unnamed) }
    Surface(tonalElevation = 4.dp, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (left == 0) stringResource(R.string.plan_upcoming_today, name) else pluralStringResource(R.plurals.plan_upcoming, left, left, name),
                Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = { nav.showPlans(true); nav.openPlan(t.id) }) { Text(stringResource(R.string.plan_open)) }
        }
    }
}

/** Countdown fuer die Kinder, nur wenn eine Reise ansteht (hoechstens 60 Tage). */
@Composable
fun KidsCountdown(plans: List<PlannedTrip>, modifier: Modifier = Modifier) {
    val today = LocalDate.now().toEpochDay().toInt()
    val t = TripReminders.next(plans, today) ?: return
    val left = t.startDay - today
    if (left > 60) return
    val dest = t.destination?.name ?: t.name
    Text(
        if (left == 0) stringResource(R.string.kids_countdown_today, dest) else pluralStringResource(R.plurals.kids_countdown, left, left, dest),
        style = MaterialTheme.typography.titleMedium, modifier = modifier,
    )
}
