package app.kiloquest.nav

enum class TollType { NONE, VIGNETTE, TOLL_ROADS, SPECIAL }

/** Maut-/Vignettenregel fuer Pkw eines Landes (ISO-Code). [url] = offizielle Seite, nur wo sicher bekannt. */
data class TollRule(val country: String, val type: TollType, val url: String? = null)

/**
 * Maut-/Vignetten-Lotse: sagt vor der Fahrt, in welchen Laendern der Route etwas zu kaufen oder zu beachten ist.
 * Bewusst nur grobe, Pkw-bezogene Hinweise; Preise und Regeln aendern sich, deshalb gibt es den Stand ([AS_OF]) und den Hinweis, vor Fahrtantritt
 * die offizielle Seite zu pruefen. Keine Rechtsberatung. Der Text je Land steht in den Ressourcen (`toll_<cc>`, Kleinbuchstaben).
 */
object TollGuide {
    /** Stand der Tabelle (JJJJ-MM). Bei jeder Pflege anpassen. */
    const val AS_OF = "2025-01"

    val rules: Map<String, TollRule> = listOf(
        TollRule("AT", TollType.VIGNETTE, "https://www.asfinag.at"),
        TollRule("CH", TollType.VIGNETTE, "https://www.e-vignette.ch"),
        TollRule("CZ", TollType.VIGNETTE, "https://edalnice.cz"),
        TollRule("SK", TollType.VIGNETTE, "https://eznamka.sk"),
        TollRule("SI", TollType.VIGNETTE, "https://evinjeta.dars.si"),
        TollRule("HU", TollType.VIGNETTE, "https://www.nemzetiutdij.hu"),
        TollRule("BG", TollType.VIGNETTE, "https://web.bgtoll.bg"),
        TollRule("RO", TollType.VIGNETTE, "https://www.roviniete.ro"),
        TollRule("FR", TollType.TOLL_ROADS), TollRule("IT", TollType.TOLL_ROADS, "https://www.autostrade.it"),
        TollRule("ES", TollType.TOLL_ROADS), TollRule("PT", TollType.TOLL_ROADS), TollRule("HR", TollType.TOLL_ROADS),
        TollRule("GR", TollType.TOLL_ROADS), TollRule("PL", TollType.TOLL_ROADS),
        TollRule("NO", TollType.SPECIAL, "https://www.autopass.no"),
        TollRule("DK", TollType.SPECIAL, "https://www.sundogbaelt.dk"),
        TollRule("SE", TollType.SPECIAL, "https://www.oresundsbron.com"),
        TollRule("NL", TollType.SPECIAL),
        TollRule("DE", TollType.NONE), TollRule("FI", TollType.NONE), TollRule("BE", TollType.NONE), TollRule("LU", TollType.NONE),
    ).associateBy { it.country }

    fun rule(country: String?): TollRule? = country?.uppercase()?.let { rules[it] }

    /** Hinweise fuer die Laender der Route in Reihenfolge ihres ersten Auftretens; ohne Doppelte, ohne Laender ohne Pkw-Maut, ohne unbekannte. */
    fun notices(countries: List<String>): List<TollRule> =
        countries.mapNotNull { rule(it) }.filter { it.type != TollType.NONE }.distinctBy { it.country }

    /** Ressourcen-Schluessel des Landestextes. */
    fun textKey(country: String) = "toll_" + country.lowercase()
}
