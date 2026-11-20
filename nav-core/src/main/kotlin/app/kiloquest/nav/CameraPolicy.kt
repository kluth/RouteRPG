package app.kiloquest.nav

/** Was in einem Land zu Geschwindigkeitskontrollen angezeigt werden darf. Tempolimit-Anzeige ist davon unabhaengig und ueberall aktiv. */
enum class CameraMode {
    /** Keine Hinweise auf Kontrollen (Standard). */
    OFF,
    /** Nur allgemein formulierte Hinweise ohne genaue Position (z. B. "Streckenabschnitt mit Geschwindigkeitsueberwachung"). */
    DANGER_ZONE_ONLY,
    /** Warnung vor fest installierten Anlagen (OSM `highway=speed_camera`), keine mobilen Kontrollen, keine Crowd-Meldungen. */
    FIXED_CAMERAS,
}

/** Ergebnis der rechtlichen Recherche zu einem Land (siehe `docs/legal/speed-cameras.md`). Keine Rechtsberatung. */
enum class LegalStatus {
    /** Nutzung von Warnfunktionen durch Fahrer ist untersagt. */
    BANNED,
    /** Nur eingeschraenkte Formen erlaubt (z. B. Gefahrenzonen statt Positionen). */
    RESTRICTED,
    /** Quellen widersprechen sich oder es gibt keine belastbare amtliche Aussage. */
    UNCLEAR,
}

/**
 * Regel eines Landes. [mode] gilt nur, wenn [verifiedOn] (Datum der dokumentierten Pruefung, ISO) gesetzt ist; sonst bleibt es OFF.
 * [doc] = Datei unter `docs/legal/` mit Quellen und Pruefergebnis.
 */
data class CameraRule(
    val country: String,
    val status: LegalStatus,
    val mode: CameraMode = CameraMode.OFF,
    val verifiedOn: String? = null,
    val doc: String = "speed-cameras.md",
)

/**
 * Laender-Policy fuer Blitzerwarnungen (AGENTS.md Regel 5). Standard ueberall AUS. Ein Land wird nur freigeschaltet, wenn
 * eine dokumentierte Rechtspruefung vorliegt; die Tests erzwingen das. Unbekannte Laender und unbekannter Standort: AUS.
 */
object CameraPolicy {
    /** Stand der Recherche: siehe docs/legal/speed-cameras.md. Alle Laender sind (noch) nicht abschliessend geprueft, daher alles OFF. */
    val rules: List<CameraRule> = listOf(
        CameraRule("DE", LegalStatus.BANNED),
        CameraRule("CH", LegalStatus.BANNED),
        CameraRule("FR", LegalStatus.RESTRICTED),
        CameraRule("SE", LegalStatus.UNCLEAR),
        CameraRule("DK", LegalStatus.UNCLEAR),
        CameraRule("FI", LegalStatus.UNCLEAR),
        CameraRule("NO", LegalStatus.UNCLEAR),
        CameraRule("NL", LegalStatus.UNCLEAR),
        CameraRule("BE", LegalStatus.UNCLEAR),
        CameraRule("AT", LegalStatus.UNCLEAR),
        CameraRule("IT", LegalStatus.UNCLEAR),
        CameraRule("ES", LegalStatus.UNCLEAR),
        CameraRule("PL", LegalStatus.UNCLEAR),
    )

    private val byCountry = rules.associateBy { it.country }

    fun ruleFor(country: String?): CameraRule? = byCountry[country?.trim()?.uppercase()]

    /** Erlaubter Modus: nur mit dokumentierter Pruefung, nie in Laendern mit Verbot; sonst OFF. */
    fun modeFor(country: String?): CameraMode {
        val r = ruleFor(country) ?: return CameraMode.OFF
        if (r.status == LegalStatus.BANNED || r.verifiedOn.isNullOrBlank()) return CameraMode.OFF
        return r.mode
    }

    /** Ist irgendwo eine Warnung freigeschaltet? Wenn nicht, zeigt die App nur die Erklaerung. */
    fun anyEnabled(): Boolean = rules.any { modeFor(it.country) != CameraMode.OFF }
}
