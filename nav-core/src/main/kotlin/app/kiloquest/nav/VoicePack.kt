package app.kiloquest.nav

import java.security.MessageDigest

/**
 * Pro-Fahrt-Stimmenpaket (Spec 016, Stufe 3): welche Strassennamen der Route in der Ansage vorkommen und noch keinen Clip haben.
 * Reine Logik; Abruf und Ablage macht die App.
 */
object VoicePack {
    const val MAX_NAMES = 150

    /** Dateiname-Hash eines Namens; gleich zu `VoiceIndex.hash` (game-engine), Test im App-Modul. */
    fun nameHash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(12)

    /**
     * Alle Namen, die [SpokenPlan] fuer die Manoever der Route als Name-Slot erzeugt, ohne Duplikate, in Routenreihenfolge,
     * ohne die, fuer die [hasClip] schon einen Clip kennt; hoechstens [limit].
     */
    fun neededNames(route: Route, lang: SpokenLang, limit: Int = MAX_NAMES, hasClip: (String) -> Boolean): List<String> {
        val out = LinkedHashSet<String>()
        for (m in route.maneuvers) {
            val spoken = SpokenPlan.now(m, lang) ?: continue
            for (s in spoken.slots) {
                if (s is Slot.Name && !hasClip(s.text)) out += s.text
                if (out.size >= limit) return out.toList()
            }
        }
        return out.toList()
    }
}
