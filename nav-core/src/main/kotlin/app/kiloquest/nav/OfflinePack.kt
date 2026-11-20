package app.kiloquest.nav

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class PackManeuver(
    val type: Int, val text: String, val pre: String? = null, val post: String? = null,
    val streets: List<String> = emptyList(), val at: Double, val length: Double, val exit: Int? = null,
)
@Serializable data class PackSpeed(val from: Double, val to: Double, val limit: Int? = null)
@Serializable data class PackCountry(val at: Double, val code: String)
@Serializable data class PackToilet(
    val kind: String, val name: String? = null, val lat: Double, val lon: Double,
    val verified: Boolean = false, val features: List<String> = emptyList(), val hours: String? = null,
)
@Serializable data class PackLaneItem(val indications: List<String>, val valid: Boolean, val active: Boolean)
@Serializable data class PackLane(val at: Double, val lanes: List<PackLaneItem>)

/** Eine Quest, sprachneutral (Enum-Namen als Text). Die Umwandlung von/zu `Quest` liegt in der App. */
@Serializable data class PackQuest(
    val templateId: String, val textKey: String, val type: String, val trigger: Double, val kind: String,
    val options: List<String> = emptyList(), val correct: Int = -1, val points: Int = 10, val args: List<String> = emptyList(),
)

/**
 * Alles, was eine Route ohne Netz braucht: Linie und Anweisungen, Tempolimits, Laender, Toiletten/Rastplaetze am Weg,
 * Spurinfos und die bereits aufgeloesten Aufgaben. Kartenkacheln liegen im Offline-Speicher der Karte (siehe [mapMb]).
 */
@Serializable data class OfflinePack(
    val version: Int = 1,
    val id: String,
    val name: String,
    val destinationName: String,
    /** Tag der Erstellung (Epochentag). */
    val createdDay: Int,
    val shape: String,
    val lengthMeters: Double,
    val durationSeconds: Double,
    val maneuvers: List<PackManeuver> = emptyList(),
    val speed: List<PackSpeed> = emptyList(),
    val speedCoveredUntil: Double = -1.0,
    val countries: List<PackCountry> = emptyList(),
    val toilets: List<PackToilet> = emptyList(),
    val lanes: List<PackLane> = emptyList(),
    val quests: List<PackQuest> = emptyList(),
    val mapMb: Int = 0,
)

/** Kleine Kopfdaten fuer die Liste, ohne die ganze Route zu laden. */
@Serializable data class OfflinePackMeta(val id: String, val name: String, val destinationName: String, val createdDay: Int, val lengthMeters: Double, val mapMb: Int, val quests: Int, val toilets: Int)

object OfflinePackCodec {
    const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(p: OfflinePack): String = json.encodeToString(p)
    fun encodeMeta(m: OfflinePackMeta): String = json.encodeToString(m)

    /** null bei kaputten oder neueren Daten, nie ein Absturz. */
    fun decode(text: String?): OfflinePack? =
        runCatching { json.decodeFromString<OfflinePack>(text ?: return null) }.getOrNull()?.takeIf { it.version <= VERSION }

    fun decodeMeta(text: String?): OfflinePackMeta? = runCatching { json.decodeFromString<OfflinePackMeta>(text ?: return null) }.getOrNull()

    fun meta(p: OfflinePack) = OfflinePackMeta(p.id, p.name, p.destinationName, p.createdDay, p.lengthMeters, p.mapMb, p.quests.size, p.toilets.size)

    fun build(
        id: String, name: String, destinationName: String, createdDay: Int, route: Route,
        speed: SpeedProfile, countries: List<Pair<Double, String>>, toilets: List<ToiletStop>, lanes: List<LaneGuidance>,
        quests: List<PackQuest>, mapMb: Int,
    ) = OfflinePack(
        VERSION, id, name, destinationName, createdDay, PolylineCodec.encode(route.line.points), route.lengthMeters, route.durationSeconds,
        route.maneuvers.map { PackManeuver(it.type, it.instruction, it.verbalPre, it.verbalPost, it.streetNames, it.atMeters, it.lengthMeters, it.roundaboutExit) },
        speed.snapshot().map { PackSpeed(it.fromMeters, it.toMeters, it.limitKmh) }, speed.coveredUntilMeters,
        countries.map { PackCountry(it.first, it.second) },
        toilets.map { PackToilet(it.kind.name, it.name, it.position.lat, it.position.lon, it.verified, it.features.map { f -> f.name }, it.openingHours) },
        lanes.map { l -> PackLane(l.atMeters, l.lanes.map { PackLaneItem(it.indications, it.valid, it.active) }) },
        quests, mapMb,
    )

    fun route(p: OfflinePack): Route {
        val line = Polyline(PolylineCodec.decode(p.shape))
        return Route(
            line,
            p.maneuvers.map { Maneuver(it.type, it.text, it.pre, it.post, it.streets, it.at, it.length, it.exit) },
            p.lengthMeters, p.durationSeconds,
        )
    }

    fun speedProfile(p: OfflinePack) = SpeedProfile.restore(p.speed.map { SpeedSegment(it.from, it.to, it.limit) }, p.speedCoveredUntil)

    fun countryTimeline(p: OfflinePack): CountryTimeline? = p.countries.takeIf { it.isNotEmpty() }?.let { c -> CountryTimeline(c.map { it.at to it.code }) }

    fun toilets(p: OfflinePack): List<ToiletStop> = p.toilets.mapNotNull { t ->
        val kind = runCatching { ToiletKind.valueOf(t.kind) }.getOrNull() ?: return@mapNotNull null
        ToiletStop(kind, t.name, LatLon(t.lat, t.lon), t.verified, t.features.mapNotNull { f -> runCatching { StopFeature.valueOf(f) }.getOrNull() }.toSet(), t.hours)
    }

    fun lanes(p: OfflinePack): List<LaneGuidance> = p.lanes.map { l -> LaneGuidance(l.at, l.lanes.map { Lane(it.indications, it.valid, it.active) }) }
}
