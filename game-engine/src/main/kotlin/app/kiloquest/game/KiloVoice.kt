package app.kiloquest.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Index der aufgenommenen Kilo-Saetze. Eine Aufnahme gehoert zu genau einem Text; der Text wird ueber einen Hash gefunden,
 * damit eine geaenderte Formulierung automatisch auf die Geraete-Sprachausgabe zurueckfaellt (veraltete Aufnahmen werden nie gespielt).
 */
class VoiceIndex(private val byLang: Map<String, Map<String, String>>) {
    /** Id der Aufnahme fuer [text] in [lang], null wenn es keine passende gibt. */
    fun idFor(lang: String, text: String): String? = byLang[lang]?.get(hash(text))

    fun count(lang: String): Int = byLang[lang]?.size ?: 0

    companion object {
        val EMPTY = VoiceIndex(emptyMap())

        /** Kurzer, stabiler Hash des gesprochenen Textes (Leerraum am Rand ignoriert). Muss zu tools/voice/build_lines.py passen. */
        fun hash(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(12)

        /** Liest `manifest.json`; kaputte Daten ergeben einen leeren Index, nie einen Absturz. */
        fun parse(json: String?): VoiceIndex = runCatching {
            val lines = Json.parseToJsonElement(json ?: return EMPTY).jsonObject["lines"] as? JsonObject ?: return EMPTY
            VoiceIndex(lines.mapValues { (_, v) -> v.jsonObject.mapValues { (_, id) -> id.jsonPrimitive.content } })
        }.getOrDefault(EMPTY)
    }
}

/** Lob und Trost von Kilo: waehlt eine Zeile, ohne dieselbe zweimal hintereinander zu nehmen. */
object KiloPraise {
    const val PRAISE_LINES = 6
    const val RETRY_LINES = 4

    /** Nummer (1-basiert) der naechsten Zeile; [last] = zuletzt gespielte Nummer derselben Art (0 = keine). */
    fun pick(correct: Boolean, last: Int, random: Random): Int {
        val n = if (correct) PRAISE_LINES else RETRY_LINES
        if (n == 1) return 1
        var i = random.nextInt(1, n + 1)
        if (i == last) i = if (i == n) 1 else i + 1
        return i
    }

    /** Schluessel der String-Ressource, z. B. `kilo_praise_3`. */
    fun key(correct: Boolean, n: Int) = (if (correct) "kilo_praise_" else "kilo_retry_") + n
}
