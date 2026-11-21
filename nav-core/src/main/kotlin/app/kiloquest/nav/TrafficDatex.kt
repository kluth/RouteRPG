package app.kiloquest.nav

import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

enum class SituationType { ROADWORKS, CONGESTION, CLOSURE, OTHER }

/** Meldung aus DATEX II. Kein Anzeigetext: Texte entstehen spaeter aus Strings anhand von [type]. */
data class DatexSituation(
    val id: String,
    val type: SituationType,
    val road: String?,
    val start: LatLon,
    /** Gleich [start] bei Punktmeldungen. */
    val end: LatLon,
    val validFromMillis: Long?,
    val validToMillis: Long?,
)

/** Meldung entlang der Route: Meter ab Routenstart. */
data class RouteSituation(val situation: DatexSituation, val alongMeters: Double, val lengthMeters: Double)

/**
 * Parser fuer DATEX-II-v3-Situationsmeldungen (z. B. Mobilithek). Nur JVM-Bordmittel (javax XML).
 *
 * Annahmen (nicht gegen echte Mobilithek-Daten verifiziert):
 * - Elemente werden nach lokalem Namen gelesen, Namespace-Praefixe sind egal.
 * - `situationRecord` mit `xsi:type`: Roadworks/MaintenanceWorks/ConstructionWorks -> ROADWORKS, AbnormalTraffic -> CONGESTION,
 *   Elemente mit "closed"/"closure" im Namen oder Wert (z. B. roadClosed) -> CLOSURE, sonst OTHER.
 * - Position: Koordinatenpaare `latitude`/`longitude` unter `locationReference` in Dokumentreihenfolge; erstes = Start, letztes = Ende
 *   (Fallback `locationForDisplay` ist ebenfalls ein solches Paar). Meldungen ohne Koordinaten werden verworfen.
 * - Strasse: erstes `roadNumber`, sonst `roadName` (Text des ersten `value` bzw. des Elements).
 * - Gueltigkeit: `overallStartTime`/`overallEndTime` (ISO 8601 mit Zeitzone); fehlend = null (offen).
 * - Fahrtrichtung wird nicht ausgewertet; Meldungen beider Richtungen koennen treffen.
 */
object DatexParser {
    fun parse(xml: String): List<DatexSituation> {
        val doc = runCatching {
            val f = DocumentBuilderFactory.newInstance()
            f.isNamespaceAware = true
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            f.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        }.getOrNull() ?: return emptyList()
        val records = doc.getElementsByTagNameNS("*", "situationRecord")
        return (0 until records.length).mapNotNull { runCatching { toSituation(records.item(it) as Element) }.getOrNull() }
    }

    private fun name(n: Node) = n.localName ?: n.nodeName.substringAfter(':')

    private fun descendants(e: Element, local: String): List<Element> {
        val l = e.getElementsByTagNameNS("*", local)
        return (0 until l.length).map { l.item(it) as Element }
    }

    private fun toSituation(r: Element): DatexSituation? {
        val id = r.getAttribute("id").ifBlank { return null }
        val xsiType = r.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type").substringAfter(':')
        val loc = descendants(r, "locationReference").firstOrNull() ?: return null
        val lats = descendants(loc, "latitude").mapNotNull { it.textContent.trim().toDoubleOrNull() }
        val lons = descendants(loc, "longitude").mapNotNull { it.textContent.trim().toDoubleOrNull() }
        val n = minOf(lats.size, lons.size)
        if (n == 0) return null
        val start = LatLon(lats[0], lons[0])
        val end = LatLon(lats[n - 1], lons[n - 1])
        val road = (descendants(loc, "roadNumber") + descendants(loc, "roadName"))
            .firstNotNullOfOrNull { e -> (descendants(e, "value").firstOrNull() ?: e).textContent.trim().takeIf { it.isNotEmpty() } }
        val closed = descendants(r, "*").any { e ->
            val nm = name(e).lowercase()
            val own = e.childNodes.let { c -> (0 until c.length).map { c.item(it) }.filter { it.nodeType == Node.TEXT_NODE }.joinToString("") { it.textContent }.trim().lowercase() }
            nm.contains("closed") || nm.contains("closure") || own.contains("closed")
        }
        val type = when {
            xsiType in setOf("Roadworks", "MaintenanceWorks", "ConstructionWorks") -> SituationType.ROADWORKS
            xsiType == "AbnormalTraffic" -> SituationType.CONGESTION
            closed -> SituationType.CLOSURE
            else -> SituationType.OTHER
        }
        fun time(local: String) = descendants(r, local).firstOrNull()?.textContent?.trim()
            ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
        return DatexSituation(id, type, road, start, end, time("overallStartTime"), time("overallEndTime"))
    }
}

/** Meldungen entlang einer Route. */
object DatexOnRoute {
    /**
     * Meldungen, deren Start oder Ende hoechstens [bufferMeters] von der Route entfernt liegt; Ergebnis nach Streckenmeter sortiert.
     * Mit [atMillis] werden nur zu diesem Zeitpunkt gueltige Meldungen behalten (fehlende Grenzen = offen).
     */
    fun along(route: Polyline, situations: List<DatexSituation>, bufferMeters: Double = 100.0, atMillis: Long? = null): List<RouteSituation> =
        situations.mapNotNull { s ->
            if (atMillis != null && ((s.validFromMillis != null && atMillis < s.validFromMillis) || (s.validToMillis != null && atMillis > s.validToMillis))) return@mapNotNull null
            val a = route.project(s.start)
            val b = route.project(s.end)
            if (a.offsetMeters > bufferMeters && b.offsetMeters > bufferMeters) return@mapNotNull null
            val from = minOf(a.alongMeters, b.alongMeters)
            val to = maxOf(a.alongMeters, b.alongMeters)
            RouteSituation(s, from, to - from)
        }.sortedWith(compareBy({ it.alongMeters }, { it.situation.id }))
}
