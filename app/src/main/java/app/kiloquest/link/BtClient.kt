package app.kiloquest.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.kiloquest.KeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

enum class ClientStatus { IDLE, CONNECTING, WAITING, CONNECTED, REFUSED, FULL, VERSION, LOST }

data class ClientState(
    val status: ClientStatus = ClientStatus.IDLE,
    val role: Role? = null,
    val player: Int = 0,
    val snapshot: Snapshot? = null,
    /** Route und Position fuer die Karte der Kinder (getrennt vom Schnappschuss, damit der klein bleibt). */
    val route: RouteDto? = null,
    val pos: PosDto? = null,
    val hostName: String? = null,
)

data class NearbyDevice(val address: String, val name: String, val bonded: Boolean)

/** Kinder-/Beifahrer-Geraet: verbindet sich per Bluetooth mit dem Hauptgeraet und spiegelt dessen Zustand. */
@SuppressLint("MissingPermission")
class BtClient(private val ctx: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(ClientState())
    val state: StateFlow<ClientState> = _state

    private val _nearby = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearby: StateFlow<List<NearbyDevice>> = _nearby

    private var job: Job? = null
    private var socket: BluetoothSocket? = null
    private var wanted = false
    private var scanning = false

    private val adapter: BluetoothAdapter? get() = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action != BluetoothDevice.ACTION_FOUND) return
            val d = if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            d ?: return
            val name = d.name ?: return
            _nearby.update { list -> if (list.any { it.address == d.address }) list else list + NearbyDevice(d.address, name, d.bondState == BluetoothDevice.BOND_BONDED) }
        }
    }

    /** Gekoppelte (System) Geraete sofort listen, danach die Umgebung absuchen. */
    fun scan() {
        val a = adapter ?: return
        if (!BtHost.hasPermissions(ctx) || !a.isEnabled) return
        _nearby.value = a.bondedDevices.orEmpty().mapNotNull { d -> d.name?.let { NearbyDevice(d.address, it, true) } }
        if (!scanning) {
            scanning = true
            ctx.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        }
        a.cancelDiscovery(); a.startDiscovery()
    }

    fun stopScan() {
        runCatching { adapter?.cancelDiscovery() }
        if (scanning) { scanning = false; runCatching { ctx.unregisterReceiver(receiver) } }
    }

    /** Verbindet und haelt die Verbindung (automatischer Neuaufbau, bis [disconnect] gerufen wird). */
    fun connect(address: String, name: String, ageBand: String) {
        disconnect()
        stopScan()
        wanted = true
        // Zustand sofort setzen, damit der Dienst beim Start schon "aktiv" sieht
        _state.value = ClientState(status = ClientStatus.CONNECTING, hostName = hostNameOf(address))
        KeepAliveService.start(ctx)
        job = scope.launch(Dispatchers.IO) {
            while (isActive && wanted) {
                _state.update { it.copy(status = ClientStatus.CONNECTING, hostName = hostNameOf(address)) }
                val ok = runSession(address, name, ageBand)
                if (!wanted || !ok) break
                _state.update { it.copy(status = ClientStatus.LOST) }
                delay(5_000)
            }
        }
    }

    private fun hostNameOf(address: String) = runCatching { adapter?.getRemoteDevice(address)?.name }.getOrNull()

    /** Gibt true zurueck, wenn die Verbindung stand und verloren ging (dann neu versuchen). */
    private fun runSession(address: String, name: String, ageBand: String): Boolean {
        val a = adapter ?: return false
        var connectedOnce = false
        try {
            val device = a.getRemoteDevice(address)
            a.cancelDiscovery()
            val s = device.createRfcommSocketToServiceRecord(LINK_UUID)
            socket = s
            s.connect()
            connectedOnce = true
            send(ClientMsg.Hello(name, ageBand))
            _state.update { it.copy(status = ClientStatus.WAITING) }
            val reader = LineReader()
            val buf = ByteArray(4096)
            val input = s.inputStream
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                for (line in reader.feed(buf, n)) Wire.decodeHost(line)?.let(::onHostMessage)
            }
        } catch (_: IOException) {
        } catch (_: SecurityException) {
            wanted = false
        } finally {
            runCatching { socket?.close() }
            socket = null
        }
        val terminal = _state.value.status in setOf(ClientStatus.REFUSED, ClientStatus.FULL, ClientStatus.VERSION)
        if (terminal) { wanted = false; return false }
        // Verbindung nie zustande gekommen: nach einer Pause erneut versuchen (Hauptgeraet evtl. noch nicht bereit)
        if (!connectedOnce) _state.update { it.copy(status = ClientStatus.LOST) }
        return wanted
    }

    private fun onHostMessage(m: HostMsg) {
        when (m) {
            is HostMsg.Welcome -> _state.update { it.copy(status = ClientStatus.CONNECTED, role = m.role, player = m.player) }
            is HostMsg.Denied -> _state.update {
                it.copy(status = when (m.reason) { DenyReason.REFUSED -> ClientStatus.REFUSED; DenyReason.FULL -> ClientStatus.FULL; DenyReason.VERSION -> ClientStatus.VERSION })
            }
            is HostMsg.SnapshotMsg -> _state.update { it.copy(snapshot = m.snapshot) }
            is HostMsg.RouteMsg -> _state.update { it.copy(route = m.route) }
            is HostMsg.PositionMsg -> _state.update { it.copy(pos = m.pos) }
            HostMsg.Bye -> { wanted = false; _state.update { ClientState() } }
        }
    }

    fun send(msg: ClientMsg) {
        val s = socket ?: return
        scope.launch(Dispatchers.IO) {
            try { synchronized(s) { s.outputStream.write(Wire.encode(msg).toByteArray(Charsets.UTF_8)); s.outputStream.flush() } } catch (_: IOException) { }
        }
    }

    fun disconnect() {
        wanted = false
        job?.cancel(); job = null
        runCatching { socket?.close() }; socket = null
        _state.value = ClientState()
    }
}
