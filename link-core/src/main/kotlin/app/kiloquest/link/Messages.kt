package app.kiloquest.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 1

/** Rolle eines gekoppelten Geraets; vergibt immer das Hauptgeraet. */
@Serializable
enum class Role { KID, COPILOT }

/** Quest ohne richtige Antwort (die kommt erst im Feedback). Texte nur als Schluessel + Argumente (i18n). */
@Serializable
data class QuestDto(
    val templateId: String,
    val textKey: String,
    val type: String,
    val kind: String,
    val optionKeys: List<String> = emptyList(),
    val args: List<String> = emptyList(),
    val points: Int = 10,
    val triggerMeters: Double = 0.0,
)

@Serializable
data class FeedbackDto(val correct: Boolean, val points: Int, val correctOptionKey: String? = null)

/** Umfahrungsvorschlag fuer den Beifahrer (Texte vom Hauptgeraet in dessen Sprache vorbereitet). */
@Serializable
data class RerouteDto(val savedMinutes: Int, val blocked: Boolean, val incidentType: String, val road: String)

@Serializable
data class BreakDto(val aheadMeters: Double, val stopText: String)

/** Route fuer die Karte der Kinder: vereinfachte Linie (Polyline, 6 Nachkommastellen) und Ziel; [id] erkennt Neuberechnungen. */
@Serializable
data class RouteDto(val id: Long, val encoded: String, val lengthMeters: Double, val destLat: Double? = null, val destLon: Double? = null)

/** Aktuelle Position fuer die Karte (leicht, oft gesendet; getrennt vom Schnappschuss). */
@Serializable
data class PosDto(val lat: Double, val lon: Double, val bearing: Float? = null, val alongMeters: Double = 0.0, val routeId: Long = 0)

/** Klo-Alarm: Stand fuer die gekoppelten Geraete. [phase]: SEARCHING, HEADING, ARRIVED oder NONE. */
@Serializable
data class ToiletDto(val phase: String, val label: String = "", val seconds: Double? = null, val meters: Double? = null)

/** Alles, was ein gekoppeltes Geraet zum Darstellen braucht. */
@Serializable
data class Snapshot(
    val seq: Long,
    val navigating: Boolean = false,
    val curvy: Boolean = false,
    val quest: QuestDto? = null,
    val feedback: FeedbackDto? = null,
    val newStickerId: String? = null,
    val points: Int = 0,
    val stickers: Int = 0,
    val questsDone: Int = 0,
    val questsTotal: Int = 0,
    val scores: List<Int> = emptyList(),
    val currentPlayer: Int = 0,
    val yourPlayer: Int = 0,
    val album: List<String> = emptyList(),
    val totalPoints: Int = 0,
    val plan: List<QuestDto> = emptyList(),
    /** Nur fuer Beifahrer-Geraete gesetzt. */
    val reroute: RerouteDto? = null,
    val breakProposal: BreakDto? = null,
    /** Klo-Alarm laeuft (fuer alle Rollen sichtbar). */
    val toilet: ToiletDto? = null,
    /** Ein Kind hat "Ich muss mal" gedrueckt (Name des Geraets); fuer Beifahrer-Geraete. */
    val toiletRequest: String? = null,
)

/** Nachrichten Geraet -> Hauptgeraet. */
@Serializable
sealed interface ClientMsg {
    @Serializable @SerialName("hello") data class Hello(val name: String, val ageBand: String, val version: Int = PROTOCOL_VERSION) : ClientMsg
    @Serializable @SerialName("answer") data class Answer(val choice: Int? = null) : ClientMsg
    @Serializable @SerialName("skip") data object Skip : ClientMsg
    @Serializable @SerialName("next") data object Next : ClientMsg
    @Serializable @SerialName("reroute") data class Reroute(val accept: Boolean) : ClientMsg
    @Serializable @SerialName("break") data class Break(val accept: Boolean) : ClientMsg
    @Serializable @SerialName("age") data class Age(val band: String) : ClientMsg
    /** Beifahrer: Klo-Alarm ausloesen, weiterfahren/abbrechen, Kinder-Wunsch wegklicken. */
    @Serializable @SerialName("toilet_alarm") data object ToiletAlarm : ClientMsg
    @Serializable @SerialName("toilet_resume") data object ToiletResume : ClientMsg
    @Serializable @SerialName("toilet_dismiss") data object ToiletDismiss : ClientMsg
    /** Kind: "Ich muss mal" (loest nichts aus, fragt nur den Beifahrer). */
    @Serializable @SerialName("toilet_request") data object ToiletRequest : ClientMsg
    @Serializable @SerialName("ping") data object Ping : ClientMsg
}

/** Nachrichten Hauptgeraet -> Geraet. */
@Serializable
sealed interface HostMsg {
    @Serializable @SerialName("welcome") data class Welcome(val role: Role, val player: Int, val version: Int = PROTOCOL_VERSION) : HostMsg
    @Serializable @SerialName("denied") data class Denied(val reason: DenyReason) : HostMsg
    @Serializable @SerialName("snapshot") data class SnapshotMsg(val snapshot: Snapshot) : HostMsg
    @Serializable @SerialName("route") data class RouteMsg(val route: RouteDto?) : HostMsg
    @Serializable @SerialName("pos") data class PositionMsg(val pos: PosDto?) : HostMsg
    @Serializable @SerialName("bye") data object Bye : HostMsg
}

@Serializable
enum class DenyReason { REFUSED, FULL, VERSION }

object Wire {
    const val MAX_LINE_BYTES = 256 * 1024
    private val json = Json { classDiscriminator = "t"; ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(m: ClientMsg): String = json.encodeToString(ClientMsg.serializer(), m) + "\n"
    fun encode(m: HostMsg): String = json.encodeToString(HostMsg.serializer(), m) + "\n"

    fun decodeClient(line: String): ClientMsg? = runCatching { json.decodeFromString(ClientMsg.serializer(), line.trim()) }.getOrNull()
    fun decodeHost(line: String): HostMsg? = runCatching { json.decodeFromString(HostMsg.serializer(), line.trim()) }.getOrNull()
}

/**
 * Liest zeilenweise (UTF-8) aus Byte-Bloecken; eine zu lange Zeile ohne Zeilenende wird verworfen
 * (Schutz gegen Speicherfresser), danach geht es an der naechsten Zeile weiter.
 */
class LineReader(private val maxBytes: Int = Wire.MAX_LINE_BYTES) {
    private val buf = java.io.ByteArrayOutputStream()
    private var skipping = false

    fun feed(data: ByteArray, len: Int = data.size): List<String> {
        val out = ArrayList<String>()
        for (i in 0 until len) {
            val b = data[i]
            if (b == '\n'.code.toByte()) {
                if (!skipping) out += buf.toString(Charsets.UTF_8)
                buf.reset(); skipping = false
            } else if (!skipping) {
                buf.write(b.toInt())
                if (buf.size() > maxBytes) { buf.reset(); skipping = true }
            }
        }
        return out
    }
}
