package app.kiloquest.link

data class Device(val address: String, val name: String, val role: Role, val player: Int, val ageBand: String)
data class JoinRequest(val address: String, val name: String, val ageBand: String)

/**
 * Verwaltet gekoppelte Geraete auf dem Hauptgeraet: Anfragen, Erlauben/Ablehnen, Spieler-Nummern, Rechte.
 * Rechte vergibt nur das Hauptgeraet; jede Nachricht wird gegen die Rolle geprueft ([allows]).
 */
class DeviceRoster(private val maxKids: Int = 4, private val maxCopilots: Int = 2, private val maxPending: Int = 8) {
    private val pending = LinkedHashMap<String, JoinRequest>()
    private val paired = LinkedHashMap<String, Device>()

    fun pendingRequests(): List<JoinRequest> = pending.values.toList()
    fun devices(): List<Device> = paired.values.toList()
    fun kids(): List<Device> = paired.values.filter { it.role == Role.KID }.sortedBy { it.player }
    fun device(address: String): Device? = paired[address]

    /** Ergebnis einer Hello-Nachricht. */
    sealed interface HelloResult {
        data object Pending : HelloResult
        data class Known(val device: Device) : HelloResult
        data class Rejected(val reason: DenyReason) : HelloResult
    }

    fun onHello(address: String, hello: ClientMsg.Hello): HelloResult {
        if (hello.version != PROTOCOL_VERSION) return HelloResult.Rejected(DenyReason.VERSION)
        val known = paired[address]
        if (known != null) {
            val updated = known.copy(name = hello.name, ageBand = hello.ageBand)
            paired[address] = updated
            return HelloResult.Known(updated)
        }
        // Schutz vor Anfragen-Flut: wer nicht mehr in die Warteliste passt, wird abgewiesen
        if (address !in pending && pending.size >= maxPending) return HelloResult.Rejected(DenyReason.FULL)
        pending[address] = JoinRequest(address, hello.name.take(40), hello.ageBand)
        return HelloResult.Pending
    }

    /** Beifahrer erlaubt die Anfrage als [role]; null, wenn es keine Anfrage gibt oder das Limit erreicht ist. */
    fun approve(address: String, role: Role): Device? {
        val req = pending[address] ?: return null
        val count = paired.values.count { it.role == role }
        if (count >= (if (role == Role.KID) maxKids else maxCopilots)) return null
        pending.remove(address)
        val player = if (role == Role.KID) firstFreePlayer() else -1
        return Device(address, req.name, role, player, req.ageBand).also { paired[address] = it }
    }

    /** Gespeicherte Kopplungen nach einem App-Neustart wiederherstellen. */
    fun restore(devices: List<Device>) { paired.clear(); devices.forEach { paired[it.address] = it } }

    fun deny(address: String): Boolean = pending.remove(address) != null
    fun remove(address: String): Boolean = paired.remove(address) != null
    fun clear() { pending.clear(); paired.clear() }

    fun setAge(address: String, band: String): Device? {
        val d = paired[address] ?: return null
        return d.copy(ageBand = band).also { paired[address] = it }
    }

    private fun firstFreePlayer(): Int {
        val used = paired.values.filter { it.role == Role.KID }.map { it.player }.toSet()
        return generateSequence(0) { it + 1 }.first { it !in used }
    }

    /** Altersstufe je Spielernummer (Kinder-Geraete); fehlende Spieler bekommen [default]. */
    fun ageBands(players: Int, default: String): List<String> =
        List(players) { p -> paired.values.firstOrNull { it.role == Role.KID && it.player == p }?.ageBand ?: default }

    /** Darf ein Geraet mit dieser Rolle die Nachricht ausloesen? */
    fun allows(role: Role, msg: ClientMsg): Boolean = when (msg) {
        is ClientMsg.Answer, ClientMsg.Skip, ClientMsg.Next -> role == Role.KID
        is ClientMsg.Reroute, is ClientMsg.Break -> role == Role.COPILOT
        is ClientMsg.Age -> role == Role.KID
        ClientMsg.ToiletAlarm, ClientMsg.ToiletResume, ClientMsg.ToiletDismiss, ClientMsg.BreakRequest, is ClientMsg.PhotoAnswer -> role == Role.COPILOT
        ClientMsg.ToiletRequest -> role == Role.KID
        is ClientMsg.Cmd -> role == Role.COPILOT && msg.name in Cmds.all
        ClientMsg.Ping, is ClientMsg.Hello -> true
    }
}
