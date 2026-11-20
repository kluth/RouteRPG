package app.kiloquest.link

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import app.kiloquest.ui.screenColumn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.KidsActions
import app.kiloquest.KiloQuestApp
import app.kiloquest.NavController
import app.kiloquest.R
import app.kiloquest.game.AgeBand

/** Aktionen der Kinderansicht auf einem gekoppelten Geraet: alles geht ueber Bluetooth zum Hauptgeraet. */
class RemoteActions(private val client: BtClient, private val nav: NavController) : KidsActions {
    override fun answerQuest(choice: Int?) = client.send(ClientMsg.Answer(choice))
    override fun skipQuest() = client.send(ClientMsg.Skip)
    override fun dismissFeedback() = client.send(ClientMsg.Next)
    override fun requestToilet() = client.send(ClientMsg.ToiletRequest)
    /** Die Figur ist eine Einstellung dieses Geraets: bleibt lokal, geht nicht zum Hauptgeraet. */
    override fun setAvatar(emoji: String) = nav.setAvatar(emoji)
    override fun setRealMap(on: Boolean) = nav.setRealMap(on)
    /** "Zurueck" fuehrt ins Beifahrer-Menue dieses Geraets (dort Trennen/Alter). */
    override fun showKids(show: Boolean) { if (!show) nav.showParent(true) }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** Hauptgeraet: Geraete koppeln, erlauben, entfernen. */
@Composable
fun LinkHostCard() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as KiloQuestApp
    val ui by app.link.state.collectAsState()
    var error by remember { mutableStateOf(false) }

    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        error = !app.link.startHosting()
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        // Benachrichtigungen sind optional; nur Bluetooth muss erlaubt sein
        if (BtHost.hasPermissions(ctx)) {
            discoverable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300))
        } else error = true
    }

    SectionCard {
        Text(stringResource(R.string.link_host_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.link_host_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error) Text(stringResource(R.string.link_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

        for (req in ui.pending) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.link_request, req.name), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { app.link.approve(req.address, Role.KID) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.link_as_kid)) }
                        Button(onClick = { app.link.approve(req.address, Role.COPILOT) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.link_as_copilot)) }
                    }
                    TextButton(onClick = { app.link.deny(req.address) }) { Text(stringResource(R.string.link_deny)) }
                }
            }
        }
        for (d in ui.devices) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.device.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (d.device.role == Role.KID) R.string.link_role_kid else R.string.link_role_copilot) + " · " +
                            stringResource(if (d.connected) R.string.link_connected else R.string.link_offline),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { app.link.remove(d.device.address) }) { Text(stringResource(R.string.link_remove), color = MaterialTheme.colorScheme.error) }
            }
        }
        if (!ui.hosting) {
            Button(
                onClick = {
                    error = false
                    val toAsk = BtHost.permissionsToRequest(ctx)
                    if (toAsk.isEmpty()) discoverable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300))
                    else permissions.launch(toAsk.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.link_start)) }
        } else {
            Text(stringResource(R.string.link_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { app.link.stopHosting() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.link_stop)) }
        }
    }
}

/** Kinder-/Beifahrer-Geraet: Hauptgeraet suchen und verbinden, Alter einstellen. */
@Composable
fun LinkClientCard(ageBand: AgeBand) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as KiloQuestApp
    val client = app.linkClient
    val cs by client.state.collectAsState()
    val nearby by client.nearby.collectAsState()
    var error by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        if (BtHost.hasPermissions(ctx)) client.scan() else error = true
    }
    val deviceName = remember { android.os.Build.MODEL ?: "KiloQuest" }

    SectionCard {
        Text(stringResource(R.string.link_client_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.link_client_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error) Text(stringResource(R.string.link_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(
                when (cs.status) {
                    ClientStatus.IDLE -> R.string.link_status_idle
                    ClientStatus.CONNECTING -> R.string.link_status_connecting
                    ClientStatus.WAITING -> R.string.link_status_waiting
                    ClientStatus.CONNECTED -> R.string.link_status_connected
                    ClientStatus.REFUSED -> R.string.link_status_refused
                    ClientStatus.FULL -> R.string.link_status_full
                    ClientStatus.VERSION -> R.string.link_status_version
                    ClientStatus.LOST -> R.string.link_status_lost
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (cs.status == ClientStatus.IDLE || cs.status == ClientStatus.REFUSED || cs.status == ClientStatus.FULL || cs.status == ClientStatus.VERSION) {
            OutlinedButton(
                onClick = {
                    error = false
                    val toAsk = BtHost.permissionsToRequest(ctx)
                    if (toAsk.isEmpty()) client.scan() else permissions.launch(toAsk.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.link_search)) }
            for (d in nearby) {
                TextButton(onClick = { client.connect(d.address, deviceName, ageBand.name) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(d.name + if (d.bonded) "" else "  (" + stringResource(R.string.link_new) + ")")
                }
            }
        } else {
            OutlinedButton(onClick = { client.disconnect() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.link_disconnect)) }
        }
    }
}

/** Beifahrer-Geraet: Umfahrung und Pause entscheiden, sonst nur Status. */
@Composable
fun CopilotRemoteScreen(snapshot: Snapshot?, drive: DriveDto?, extras: ExtrasDto?, photoRequest: HostMsg.PhotoRequest?, pos: PosDto?, mapState: app.kiloquest.UiState?, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    val client = (ctx.applicationContext as KiloQuestApp).linkClient
    val gallery = (ctx.applicationContext as KiloQuestApp).nav.state.collectAsState().value.settings.photosToGallery
    // Foto-Moment: laeuft als Vollbild ueber dem Beifahrer-Bildschirm; [id] gesetzt = Antwort auf eine Bitte des Hauptgeraets
    var shooting by remember { mutableStateOf<Pair<String?, String?>?>(null) }
    shooting?.let { (id, place) ->
        app.kiloquest.PhotoMomentScreen(place, pos?.let { app.kiloquest.nav.LatLon(it.lat, it.lon) }, gallery) { taken ->
            if (id != null) client.answerPhoto(id, taken)
            shooting = null
        }
        return
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.screenColumn().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.parent_title), style = MaterialTheme.typography.headlineMedium)
            DriveCard(if (snapshot?.navigating == true) drive else null)
            // Karte mit Route und Position (Kartenansicht der Kinder, hier fuer den Beifahrer)
            if (snapshot?.navigating == true && mapState?.route != null) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 260.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))) {
                    app.kiloquest.ui.KidMapView(mapState, app.kiloquest.ui.MapMode.FOLLOW, Modifier.fillMaxSize())
                }
            }
            if (photoRequest != null) {
                app.kiloquest.PhotoRequestCard(
                    reasonScenic = photoRequest.reason == "SCENIC", place = photoRequest.place,
                    onTake = { shooting = photoRequest.id to photoRequest.place },
                    onLater = { client.answerPhoto(photoRequest.id, false) },
                )
            }
            // Klo-Alarm: Wunsch eines Kindes, laufender Alarm oder der Knopf selbst
            app.kiloquest.ui.ToiletRequestCard(snapshot?.toiletRequest, { client.send(ClientMsg.ToiletAlarm) }, { client.send(ClientMsg.ToiletDismiss) })
            val td = snapshot?.toilet
            if (td != null) {
                app.kiloquest.ui.ToiletStatusCard(
                    app.kiloquest.ToiletState(runCatching { app.kiloquest.ToiletPhase.valueOf(td.phase) }.getOrDefault(app.kiloquest.ToiletPhase.HEADING), td.label, td.seconds, td.meters),
                    { client.send(ClientMsg.ToiletResume) },
                )
            } else {
                Button(
                    onClick = { client.send(ClientMsg.ToiletAlarm) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) {
                    androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_wc), contentDescription = null)
                    Text(stringResource(R.string.toilet_alarm), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            val rr = snapshot?.reroute
            val bp = snapshot?.breakProposal
            if (rr != null) {
                SectionCard {
                    Text(
                        stringResource(if (rr.blocked) R.string.reroute_blocked else R.string.reroute_title) +
                            if (rr.savedMinutes > 0) " · " + stringResource(R.string.reroute_saves, rr.savedMinutes) else "" + " · " + rr.road,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { client.send(ClientMsg.Reroute(true)) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.reroute_accept)) }
                        OutlinedButton(onClick = { client.send(ClientMsg.Reroute(false)) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.reroute_dismiss)) }
                    }
                }
            }
            if (bp != null) {
                SectionCard {
                    Text(stringResource(R.string.break_banner, if (bp.aheadMeters >= 1000) stringResource(R.string.unit_km, bp.aheadMeters / 1000.0) else stringResource(R.string.unit_m, bp.aheadMeters.toInt()), bp.stopText), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { client.send(ClientMsg.Break(true)) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.break_accept)) }
                        OutlinedButton(onClick = { client.send(ClientMsg.Break(false)) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.break_later)) }
                    }
                }
            }
            if (snapshot?.navigating == true && photoRequest == null) {
                OutlinedButton(onClick = { shooting = null to null }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.photo_now)) }
            }
            if (snapshot?.navigating == true && snapshot.breakProposal == null && snapshot.toilet == null) {
                OutlinedButton(onClick = { client.send(ClientMsg.BreakRequest) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.copilot_break_now)) }
            }
            extras?.let {
                app.kiloquest.ui.ExtrasPanel(it, CopilotExtrasActions(client), snapshot?.navigating == true, micOnThisDevice = false)
                it.emergency?.let { e -> app.kiloquest.ui.EmergencyView(e) { client.send(ClientMsg.Cmd(Cmds.EMERGENCY_CLOSE)) } }
            }
            snapshot?.let { KidsStatusCard(it.points, it.stickers, it.questsDone, it.questsTotal, questLine(it.quest)) }
            if (rr == null && bp == null && td == null) Text(stringResource(R.string.link_copilot_idle), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.link_menu)) }
        }
    }
}

/** Aktionen auf dem Hauptgeraet: direkt an die Navigation. */
class HostExtrasActions(private val nav: app.kiloquest.NavController) : app.kiloquest.ui.ExtrasActions {
    override fun findSpots() = nav.findSpots()
    override fun goToSpot(index: Int) { nav.state.value.spots.getOrNull(index)?.let(nav::goToSpot) }
    override fun resumeStopover() = nav.resumeAfterStopover()
    override fun startGame(type: app.kiloquest.game.AudioGameType) = nav.startAudioGame(type)
    override fun stopGame() = nav.stopAudioGame()
    override fun series() = nav.tellSeries()
    override fun emergency() = nav.openEmergency()
    override fun tolls() = nav.sayTolls()
}

/** Aktionen auf einem gekoppelten Beifahrer-Geraet: Bluetooth-Befehle an das Hauptgeraet (dort sprechen und hoeren die Spiele). */
class CopilotExtrasActions(private val client: BtClient) : app.kiloquest.ui.ExtrasActions {
    override fun findSpots() = client.send(ClientMsg.Cmd(Cmds.FIND_SPOTS))
    override fun goToSpot(index: Int) = client.send(ClientMsg.Cmd(Cmds.GO_SPOT, index))
    override fun resumeStopover() = client.send(ClientMsg.Cmd(Cmds.RESUME_STOPOVER))
    override fun startGame(type: app.kiloquest.game.AudioGameType) = client.send(ClientMsg.Cmd(Cmds.GAME, arg = type.name))
    override fun stopGame() = client.send(ClientMsg.Cmd(Cmds.STOP_GAME))
    override fun series() = client.send(ClientMsg.Cmd(Cmds.SERIES))
    override fun emergency() = client.send(ClientMsg.Cmd(Cmds.EMERGENCY))
    override fun tolls() = client.send(ClientMsg.Cmd(Cmds.TOLLS))
}
