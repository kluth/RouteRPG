package app.kiloquest.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Dienst-Kennung fuer KiloQuest-Kopplung (beide Seiten muessen sie kennen). */
val LINK_UUID: UUID = UUID.fromString("6b1f0f3e-5d3a-4d1e-9a52-4b17c0de0001")

/**
 * Hauptgeraet: nimmt Bluetooth-Verbindungen (RFCOMM, mit System-Kopplung/Verschluesselung) an und
 * tauscht zeilenweise JSON-Nachrichten aus (siehe :link-core). Freie Framework-API, keine Play-Services.
 * Bluetooth-Rechte prueft die Oberflaeche vor dem Start ([hasPermissions]).
 */
@SuppressLint("MissingPermission")
class BtHost(private val ctx: Context, private val scope: CoroutineScope, private val cb: Callback) {
    interface Callback {
        fun onMessage(address: String, msg: ClientMsg)
        fun onConnected(address: String)
        fun onDisconnected(address: String)
    }

    private class Conn(val socket: BluetoothSocket) { val lock = Any() }

    private val conns = ConcurrentHashMap<String, Conn>()
    private var server: BluetoothServerSocket? = null
    private var acceptJob: Job? = null

    val isRunning: Boolean get() = acceptJob?.isActive == true
    fun connectedAddresses(): Set<String> = conns.keys.toSet()

    fun start(): Boolean {
        if (isRunning) return true
        if (!hasPermissions(ctx)) return false
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        if (!adapter.isEnabled) return false
        server = try {
            adapter.listenUsingRfcommWithServiceRecord("KiloQuest", LINK_UUID)
        } catch (_: IOException) { return false } catch (_: SecurityException) { return false }
        acceptJob = scope.launch(Dispatchers.IO) {
            val srv = server ?: return@launch
            while (isActive) {
                val socket = try { srv.accept() } catch (_: IOException) { break }
                scope.launch(Dispatchers.IO) { serve(socket) }
            }
        }
        return true
    }

    private fun serve(socket: BluetoothSocket) {
        val address = socket.remoteDevice.address
        conns.put(address, Conn(socket))?.let { runCatching { it.socket.close() } }
        cb.onConnected(address)
        val reader = LineReader()
        val buf = ByteArray(4096)
        try {
            val input = socket.inputStream
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                for (line in reader.feed(buf, n)) Wire.decodeClient(line)?.let { cb.onMessage(address, it) }
            }
        } catch (_: IOException) {
        } finally {
            if (conns[address]?.socket === socket) conns.remove(address)
            runCatching { socket.close() }
            cb.onDisconnected(address)
        }
    }

    fun send(address: String, msg: HostMsg): Boolean {
        val c = conns[address] ?: return false
        return try {
            synchronized(c.lock) { c.socket.outputStream.write(Wire.encode(msg).toByteArray(Charsets.UTF_8)); c.socket.outputStream.flush() }
            true
        } catch (_: IOException) {
            runCatching { c.socket.close() }
            false
        }
    }

    fun disconnect(address: String) {
        send(address, HostMsg.Bye)
        conns.remove(address)?.let { runCatching { it.socket.close() } }
    }

    fun stop() {
        acceptJob?.cancel(); acceptJob = null
        runCatching { server?.close() }; server = null
        conns.values.forEach { runCatching { it.socket.close() } }
        conns.clear()
    }

    companion object {
        /** Welche Laufzeit-Rechte fehlen? Vor Android 12 reichen die normalen Bluetooth-Rechte aus dem Manifest. */
        fun missingPermissions(ctx: Context): List<String> {
            if (android.os.Build.VERSION.SDK_INT < 31) return emptyList()
            return listOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
            ).filter { androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        }

        fun hasPermissions(ctx: Context) = missingPermissions(ctx).isEmpty()
    }
}
