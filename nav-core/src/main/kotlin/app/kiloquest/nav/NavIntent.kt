package app.kiloquest.nav

import java.io.ByteArrayOutputStream

/** Was bei der Routenberechnung gemieden werden soll (Gemini-Parameter `avoid=f|h|t`). */
enum class RouteAvoid { FERRIES, HIGHWAYS, TOLLS }

/**
 * Eine Sprach-/App-Anfrage von aussen: Gemini/Google Assistant (Android Auto und Handy) schicken Intents mit `geo:` bzw. `geo.action:`
 * Adressen (siehe docs/specs/008-sprachbedienung.md). Reine Auswertung der Adresse, ohne Android-Klassen.
 */
sealed interface NavIntent {
    /** Navigation starten (kind "navigation"), Route zeigen ("directions") oder Zwischenstopp ("add_a_stop"). */
    data class Navigate(val query: String?, val position: LatLon?, val kind: String, val mode: Char, val avoid: Set<RouteAvoid>) : NavIntent

    /** Suche nach Orten, z. B. "restaurants nearby". */
    data class Search(val query: String) : NavIntent

    /** Aktion der laufenden Navigation, z. B. `mute`, `eta`, `avoid_tolls`, `report_crash`. */
    data class Action(val act: String, val params: Map<String, String>) : NavIntent

    /** Eigener Befehl ueber `kiloquest://cmd/<name>` (Automatisierung, Routinen). */
    data class Command(val name: String) : NavIntent
}

object NavIntents {
    const val ACTION_NAVIGATE_AUTO = "androidx.car.app.action.NAVIGATE"
    const val ACTION_NAVIGATE = "android.intent.action.NAVIGATE"
    const val ACTION_VIEW = "android.intent.action.VIEW"

    /** Aktionen, die KiloQuest umsetzt; alles andere wird dem Nutzer als "nicht unterstuetzt" gemeldet (so verlangt es die Google-Vorgabe). */
    val supportedActs: Set<String> = setOf(
        "mute", "unmute", "exit_navigation", "eta", "time_to_destination", "distance_to_destination",
        "distance_to_next_turn", "time_to_next_turn", "query_next_turn", "query_destination", "query_current_road",
        "avoid_tolls", "allow_tolls", "avoid_highways", "allow_highways", "avoid_ferries", "allow_ferries",
    )

    /** Liest [action] und [uri] eines Intents; null, wenn es keine passende Anfrage ist. */
    fun parse(action: String?, uri: String?): NavIntent? {
        val raw = uri?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val colon = raw.indexOf(':')
        if (colon <= 0) return null
        val scheme = raw.substring(0, colon).lowercase().removeSuffix(".offline")
        val rest = raw.substring(colon + 1)
        val q = rest.indexOf('?')
        val head = if (q >= 0) rest.substring(0, q) else rest
        val params = parseQuery(if (q >= 0) rest.substring(q + 1) else "")
        return when (scheme) {
            "geo.action" -> params["act"]?.takeIf { it.isNotBlank() }?.let { NavIntent.Action(it.lowercase(), params - "act") }
            // google.navigation:q=Ziel&mode=d (die Parameter folgen direkt nach dem Doppelpunkt)
            "google.navigation" -> parseQuery(rest.removePrefix("?")).let { p -> navigate(p["q"], null, "navigation", p) }
            "kiloquest" -> head.removePrefix("//").split('/').let { p -> if (p.firstOrNull() == "cmd" && p.getOrNull(1)?.isNotBlank() == true) NavIntent.Command(p[1].lowercase()) else null }
            "geo" -> parseGeo(action, head, params)
            else -> null
        }
    }

    private fun parseGeo(action: String?, head: String, params: Map<String, String>): NavIntent? {
        val coords = parseCoords(head.substringBefore(';'))?.takeIf { !(it.lat == 0.0 && it.lon == 0.0) }
        val query = params["q"]?.trim()?.takeIf { it.isNotEmpty() }
        val isNavigate = action == ACTION_NAVIGATE_AUTO || action == ACTION_NAVIGATE
        return when {
            isNavigate -> navigate(query, coords, params["intent"] ?: "navigation", params)
            // VIEW mit Suchbegriff = Suche; VIEW nur mit Koordinaten = Ort anzeigen (Route zeigen, nicht starten)
            query != null -> {
                val qc = parseCoordsWithLabel(query)
                if (qc != null) navigate(qc.second, qc.first, "directions", params) else NavIntent.Search(query)
            }
            coords != null -> navigate(null, coords, "directions", params)
            else -> null
        }
    }

    private fun navigate(query: String?, coords: LatLon?, kind: String, params: Map<String, String>): NavIntent? {
        var q = query?.trim()?.takeIf { it.isNotEmpty() }
        var pos = coords
        if (pos == null && q != null) parseCoordsWithLabel(q)?.let { pos = it.first; q = it.second }
        if (pos == null && q == null) return null
        val k = if (kind in setOf("navigation", "add_a_stop", "directions")) kind else "navigation"
        val avoid = params["avoid"].orEmpty().mapNotNull { when (it) { 'f' -> RouteAvoid.FERRIES; 'h' -> RouteAvoid.HIGHWAYS; 't' -> RouteAvoid.TOLLS; else -> null } }.toSet()
        return NavIntent.Navigate(q, pos, k, params["mode"]?.firstOrNull() ?: 'd', avoid)
    }

    private fun parseCoords(s: String): LatLon? {
        val p = s.split(',')
        if (p.size != 2) return null
        val lat = p[0].trim().toDoubleOrNull() ?: return null
        val lon = p[1].trim().toDoubleOrNull() ?: return null
        return if (lat in -90.0..90.0 && lon in -180.0..180.0) LatLon(lat, lon) else null
    }

    /** `51.4,6.7` oder `51.4,6.7(Duisburg)` -> Koordinaten und Beschriftung (oder null). */
    private fun parseCoordsWithLabel(s: String): Pair<LatLon, String?>? {
        val m = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*(?:\((.*)\))?\s*$""").matchEntire(s) ?: return null
        val c = parseCoords("${m.groupValues[1]},${m.groupValues[2]}") ?: return null
        return c to m.groupValues[3].takeIf { it.isNotBlank() }
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').filter { it.isNotEmpty() }.associate { kv ->
            val i = kv.indexOf('=')
            if (i < 0) decode(kv) to "" else decode(kv.substring(0, i)) to decode(kv.substring(i + 1))
        }

    /** Prozent-Dekodierung (UTF-8), `+` wird zu Leerzeichen; kaputte Folgen bleiben unveraendert. */
    fun decode(s: String): String {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '+' -> { out.write(' '.code); i++ }
                c == '%' && i + 2 < s.length + 0 && s.substring(i + 1, i + 3).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } -> {
                    out.write(s.substring(i + 1, i + 3).toInt(16)); i += 3
                }
                else -> { out.write(c.toString().toByteArray(Charsets.UTF_8)); i++ }
            }
        }
        return out.toString(Charsets.UTF_8)
    }
}
