package app.kiloquest.link

import android.app.Application
import app.kiloquest.NavController
import app.kiloquest.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class DeviceUi(val device: Device, val connected: Boolean)

data class LinkUi(
    val hosting: Boolean = false,
    val pending: List<JoinRequest> = emptyList(),
    val devices: List<DeviceUi> = emptyList(),
)

/**
 * Hauptgeraet: verbindet die Bluetooth-Schicht ([BtHost]) mit der App-Logik. Sendet jedem erlaubten Geraet den
 * Zustand seiner Rolle und fuehrt dessen Aktionen aus, nachdem [DeviceRoster.allows] sie freigegeben hat.
 */
class LinkController(private val app: Application, private val nav: NavController) : BtHost.Callback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences("kiloquest_link", android.content.Context.MODE_PRIVATE)
    private val roster = DeviceRoster().also { it.restore(loadDevices()) }
    private val host = BtHost(app, scope, this)

    private val _state = MutableStateFlow(LinkUi(devices = devicesUi()))
    val state: StateFlow<LinkUi> = _state

    private var pushJob: Job? = null
    private var seq = 0L
    private val lastSent = HashMap<String, String>()

    init { applyKidBands() }

    fun startHosting(): Boolean {
        if (!host.start()) return false
        _state.update { it.copy(hosting = true) }
        pushJob?.cancel()
        pushJob = scope.launch {
            var last: UiState? = null
            nav.state.collect { st ->
                if (st !== last) { last = st; pushAll(st) }
                delay(250) // gebuendelt, damit ein schnell wechselnder Zustand nicht jede Aenderung sendet
            }
        }
        return true
    }

    /** Nach einem Neustart wieder bereit sein, wenn es schon gekoppelte Geraete gibt (kein Dialog noetig). */
    fun resumeIfPaired() {
        if (roster.devices().isNotEmpty() && !_state.value.hosting) startHosting()
    }

    fun stopHosting() {
        pushJob?.cancel(); pushJob = null
        host.stop()
        lastSent.clear()
        _state.update { it.copy(hosting = false, devices = devicesUi()) }
    }

    fun approve(address: String, role: Role) {
        val d = roster.approve(address, role)
        if (d == null) {
            host.send(address, HostMsg.Denied(DenyReason.FULL)); host.disconnect(address)
        } else {
            saveDevices(); applyKidBands()
            host.send(address, HostMsg.Welcome(d.role, d.player))
            sendSnapshot(d, nav.state.value)
        }
        refresh()
    }

    fun deny(address: String) {
        roster.deny(address)
        host.send(address, HostMsg.Denied(DenyReason.REFUSED)); host.disconnect(address)
        refresh()
    }

    fun remove(address: String) {
        roster.remove(address); saveDevices(); applyKidBands()
        host.disconnect(address)
        lastSent.remove(address)
        refresh()
    }

    // --- BtHost.Callback (Threads: IO -> auf Main wechseln)

    override fun onConnected(address: String) { scope.launch { refresh() } }

    override fun onDisconnected(address: String) { scope.launch { lastSent.remove(address); refresh() } }

    override fun onMessage(address: String, msg: ClientMsg) {
        scope.launch { handle(address, msg) }
    }

    private fun handle(address: String, msg: ClientMsg) {
        if (msg is ClientMsg.Hello) {
            when (val r = roster.onHello(address, msg)) {
                is DeviceRoster.HelloResult.Known -> {
                    saveDevices(); applyKidBands()
                    host.send(address, HostMsg.Welcome(r.device.role, r.device.player))
                    sendSnapshot(r.device, nav.state.value)
                }
                is DeviceRoster.HelloResult.Rejected -> { host.send(address, HostMsg.Denied(r.reason)); host.disconnect(address) }
                DeviceRoster.HelloResult.Pending -> Unit
            }
            refresh()
            return
        }
        val dev = roster.device(address) ?: return // unbekannte Geraete duerfen nichts
        if (!roster.allows(dev.role, msg)) return
        val st = nav.state.value
        when (msg) {
            is ClientMsg.Answer -> if (mayAnswer(dev, st)) nav.answerQuestFrom(msg.choice, dev.player)
            ClientMsg.Skip -> if (mayAnswer(dev, st)) nav.skipQuest()
            ClientMsg.Next -> nav.dismissFeedback()
            is ClientMsg.Reroute -> if (st.proposal != null) { if (msg.accept) nav.acceptReroute() else nav.dismissReroute() }
            is ClientMsg.Break -> if (st.breakProposal != null) { if (msg.accept) nav.acceptBreak() else nav.laterBreak() }
            is ClientMsg.Age -> { roster.setAge(address, msg.band); saveDevices(); applyKidBands(); refresh() }
            else -> Unit
        }
    }

    /** Antworten darf der Besitzer der Aufgabe; ist dessen Geraet nicht verbunden, jedes Kinder-Geraet. */
    private fun mayAnswer(dev: Device, st: UiState): Boolean {
        if (st.quest == null) return false
        val owner = roster.kids().firstOrNull { it.player == st.currentPlayer } ?: return true
        return owner.address == dev.address || owner.address !in host.connectedAddresses()
    }

    // --- Zustand senden

    private fun pushAll(st: UiState) {
        for (d in roster.devices()) sendSnapshot(d, st)
    }

    private fun sendSnapshot(d: Device, st: UiState) {
        if (d.address !in host.connectedAddresses()) return
        val snap = st.toSnapshot(d.role, d.player, ++seq) { nav.stopText(it) }
        // Nur senden, wenn sich etwas geaendert hat (ohne die laufende Nummer verglichen)
        val key = snap.copy(seq = 0).toString()
        if (lastSent[d.address] == key) return
        lastSent[d.address] = key
        host.send(d.address, HostMsg.SnapshotMsg(snap))
    }

    private fun refresh() { _state.update { it.copy(pending = roster.pendingRequests(), devices = devicesUi()) } }

    private fun devicesUi(): List<DeviceUi> = roster.devices().map { DeviceUi(it, it.address in host.connectedAddresses()) }

    private fun applyKidBands() {
        val kids = roster.kids()
        val n = (kids.maxOfOrNull { it.player } ?: -1) + 1
        nav.setKidDevices(roster.ageBands(n, nav.state.value.ageBand.name).map(::parseBand))
    }

    // --- Speichern (Kopplungen ueberleben einen Neustart; nur Adresse, Name, Rolle, Alter)

    private fun saveDevices() {
        val arr = JSONArray()
        roster.devices().forEach { arr.put(JSONObject().put("a", it.address).put("n", it.name).put("r", it.role.name).put("p", it.player).put("g", it.ageBand)) }
        prefs.edit().putString("devices", arr.toString()).apply()
    }

    private fun loadDevices(): List<Device> = runCatching {
        val arr = JSONArray(prefs.getString("devices", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Device(o.getString("a"), o.getString("n"), Role.valueOf(o.getString("r")), o.getInt("p"), o.getString("g"))
        }
    }.getOrDefault(emptyList())
}
