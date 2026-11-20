package app.kiloquest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import android.content.Context
import app.kiloquest.game.AdventureSession
import app.kiloquest.game.AgeBand
import app.kiloquest.game.Quest
import app.kiloquest.game.QuestCatalog
import app.kiloquest.game.QuestGenerator
import app.kiloquest.game.QuestType
import app.kiloquest.game.RouteFacts
import app.kiloquest.game.Story
import app.kiloquest.game.WikiStories
import app.kiloquest.game.Sticker
import app.kiloquest.game.StickerBook
import app.kiloquest.nav.SpeechArbiter
import kotlin.random.Random
import kotlinx.coroutines.withContext
import app.kiloquest.nav.AnnouncementPlanner
import app.kiloquest.nav.BreakPlanner
import app.kiloquest.nav.CurveProfile
import app.kiloquest.nav.RestStop
import app.kiloquest.nav.RestStops
import app.kiloquest.nav.SicknessCoach
import app.kiloquest.nav.StopKind
import app.kiloquest.game.toQuest
import app.kiloquest.nav.AutobahnApi
import app.kiloquest.nav.ExclusionZones
import app.kiloquest.nav.IncidentMatcher
import app.kiloquest.nav.LaneAnnouncer
import app.kiloquest.nav.LaneGuidance
import app.kiloquest.nav.LaneHint
import app.kiloquest.nav.LaneParser
import app.kiloquest.nav.SpeedProfile
import app.kiloquest.nav.TraceAttributes
import app.kiloquest.nav.IncidentType
import app.kiloquest.nav.RerouteAdvisor
import app.kiloquest.nav.RouteIncident
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import app.kiloquest.nav.GuidanceEngine
import app.kiloquest.nav.GuidanceState
import app.kiloquest.nav.LatLon
import app.kiloquest.nav.Route
import app.kiloquest.nav.ValhallaApi
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuestFeedback(val correct: Boolean, val points: Int, val correctOptionKey: String?)

/** Text einer String-Ressource per Schluessel (Quest-Texte sind sprachneutral als Schluessel gespeichert). */
fun Context.stringByKey(key: String, args: List<String> = emptyList()): String {
    @Suppress("DiscouragedApi")
    val id = resources.getIdentifier(key, "string", packageName)
    return if (id != 0) getString(id, *args.toTypedArray()) else key
}

/** Vorschlag fuer eine schnellere Route um eine Verkehrsmeldung herum. */
data class RerouteProposal(
    val route: Route,
    val lanes: List<LaneGuidance>,
    val savedMinutes: Int,
    val blocked: Boolean,
    val incident: RouteIncident,
)

/** Vorgeschlagene Pause voraus. */
data class BreakProposal(val stop: RestStop, val aheadMeters: Double)

/** Route samt Spurinformation. */
private data class LoadedRoute(val route: Route, val lanes: List<LaneGuidance>)

data class UiState(
    val results: List<Place> = emptyList(),
    val destination: Place? = null,
    val route: Route? = null,
    val navigating: Boolean = false,
    val guidance: GuidanceState? = null,
    val position: LatLon? = null,
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
    val quest: Quest? = null,
    val feedback: QuestFeedback? = null,
    val points: Int = 0,
    val stickers: Int = 0,
    val questsDone: Int = 0,
    val questsTotal: Int = 0,
    val ageBand: AgeBand = AgeBand.EXPLORER,
    val kidsScreen: Boolean = false,
    val players: Int = 1,
    val currentPlayer: Int = 0,
    val scores: List<Int> = listOf(0),
    val album: Set<String> = emptySet(),
    val newSticker: Sticker? = null,
    val totalPoints: Int = 0,
    val incidents: List<RouteIncident> = emptyList(),
    val proposal: RerouteProposal? = null,
    val speedKmh: Int? = null,
    val speedLimit: Int? = null,
    val overspeed: Boolean = false,
    val lanes: LaneGuidance? = null,
    val recents: List<Place> = emptyList(),
    val curvy: Boolean = false,
    val sicknessMode: Boolean = true,
    val breakProposal: BreakProposal? = null,
    /** Angenommene Pause: Ziel, zu dem es noch [breakAheadMeters] sind. */
    val breakTarget: RestStop? = null,
    val breakAheadMeters: Double = 0.0,
)

private const val TRAFFIC_REFRESH_MS = 5 * 60_000L
private const val MAX_TRAFFIC_ROADS = 10
private const val ANNOUNCE_AHEAD_METERS = 100_000.0
private const val MIN_SAVED_MINUTES = 5
private const val MAX_RECENTS = 5
private const val BREAK_ARRIVAL_METERS = 400.0
private const val SPEED_WINDOW_METERS = 25_000.0
private const val SPEED_PREFETCH_METERS = 5_000.0

/** Gemeinsamer Navigationszustand fuer Handy-UI und Android-Auto (lebt im Application-Prozess). */
class NavController(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences("kq", Context.MODE_PRIVATE)
    private val generator = QuestGenerator(QuestCatalog.default)
    private var adventure: AdventureSession? = null

    private val arbiter = SpeechArbiter()
    private val advisor = RerouteAdvisor()
    private val announcedIncidents = HashSet<String>()
    private var trafficJob: Job? = null
    private var speedProfile = SpeedProfile()
    private var speedFetching = false
    private var speedRetryAt = 0L
    private var laneAnnouncer: LaneAnnouncer? = null
    private var curveProfile: CurveProfile? = null
    private val sicknessCoach = SicknessCoach()
    private var sicknessPromptIndex = 0
    private var breakPlanner = BreakPlanner(intervalMs = 75 * 60_000L)
    private var breakPlanning = false
    /** Meter, die auf frueheren Routen schon gefahren wurden (Quest-Positionen gelten fuer die Gesamtfahrt). */
    private var questOffset = 0.0
    private val book = StickerBook(prefs.getStringSet("album", emptySet()).orEmpty())

    private val _state = MutableStateFlow(
        UiState(
            ageBand = runCatching { AgeBand.valueOf(prefs.getString("age", null) ?: "EXPLORER") }.getOrDefault(AgeBand.EXPLORER),
            players = prefs.getInt("players", 1).coerceIn(1, 4),
            album = book.collected.toSet(),
            totalPoints = prefs.getInt("total_points", 0),
            recents = loadRecents(),
            sicknessMode = prefs.getBoolean("sickness_mode", true),
        ),
    )
    val state: StateFlow<UiState> = _state

    private val locationManager = app.getSystemService(LocationManager::class.java)
    private var tts: TextToSpeech? = TextToSpeech(app) { status ->
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
    }
    private var engine: GuidanceEngine? = null
    private var planner: AnnouncementPlanner? = null
    private var lastReroute = 0L

    private val listener = LocationListener { onLocation(it) }
    private var listening = false

    fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        app, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /** Position schon vor dem Navigationsstart verfolgen (fuer Routenstart). */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (listening || !hasLocationPermission()) return
        listening = true
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper(),
            )
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::onLocation)
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        scope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { searchPlaces(query, Locale.getDefault().language) }
                .onSuccess { r ->
                    _state.update {
                        it.copy(results = r, busy = false, error = if (r.isEmpty()) R.string.error_no_results else null)
                    }
                }
                .onFailure { _state.update { it.copy(busy = false, error = R.string.error_search) } }
        }
    }

    fun selectPlace(place: Place) {
        val recents = (listOf(place) + _state.value.recents.filter { it.name != place.name }).take(MAX_RECENTS)
        saveRecents(recents)
        _state.update { it.copy(destination = place, results = emptyList(), recents = recents) }
        calculateRoute()
    }

    /** Zuletzt gewaehlte Ziele (auch fuer die Suche im Auto, ohne Tippen). */
    private fun loadRecents(): List<Place> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("recents", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Place(o.getString("n"), LatLon(o.getDouble("lat"), o.getDouble("lon")))
        }
    }.getOrDefault(emptyList())

    private fun saveRecents(list: List<Place>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(org.json.JSONObject().put("n", it.name).put("lat", it.position.lat).put("lon", it.position.lon)) }
        prefs.edit().putString("recents", arr.toString()).apply()
    }

    fun clearResults() = _state.update { it.copy(results = emptyList(), error = null) }

    private fun calculateRoute() {
        val dest = _state.value.destination ?: return
        val from = _state.value.position
        if (from == null) {
            _state.update { it.copy(error = R.string.error_no_position) }
            return
        }
        if (BuildConfig.VALHALLA_URL.isBlank()) {
            _state.update { it.copy(error = R.string.error_no_server) }
            return
        }
        scope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { loadRoute(from, dest.position, emptyList()) }.onSuccess { loaded ->
                val route = loaded.route
                installRoute(loaded)
                // Abenteuer nur beim ersten Berechnen anlegen, nicht bei Neuberechnung unterwegs
                if (adventure == null || !_state.value.navigating) newAdventure(route)
                else questOffset += _state.value.guidance?.alongMeters ?: 0.0
                _state.update { it.copy(route = route, busy = false, guidance = null, incidents = emptyList()) }
                if (_state.value.navigating) scope.launch { refreshTraffic() }
            }.onFailure {
                _state.update { it.copy(busy = false, error = R.string.error_route) }
            }
        }
    }

    /** Route plus Spurinformation (zweite Anfrage im OSRM-Format; Spuren sind optional). */
    private suspend fun loadRoute(from: LatLon, to: LatLon, excludes: List<List<LatLon>>): LoadedRoute {
        val lang = Locale.getDefault().toLanguageTag()
        val route = ValhallaApi.parseRoute(fetchRoute(ValhallaApi.buildRouteRequest(from, to, lang, excludePolygons = excludes)))
        val lanes = runCatching {
            LaneParser.parseOsrm(fetchRoute(ValhallaApi.buildRouteRequest(from, to, lang, excludePolygons = excludes, osrm = true)), route)
        }.getOrDefault(emptyList())
        return LoadedRoute(route, lanes)
    }

    /** Richtet Fuehrung, Spuren und Tempolimit-Cache fuer eine neue Route ein. */
    private fun installRoute(l: LoadedRoute) {
        engine = GuidanceEngine(l.route)
        planner = AnnouncementPlanner(l.route)
        laneAnnouncer = LaneAnnouncer(l.lanes)
        curveProfile = CurveProfile(l.route)
        speedProfile = SpeedProfile()
        speedFetching = false
        speedRetryAt = 0L
    }

    private fun newAdventure(route: Route) {
        // Keine Quests nahe Abbiegungen (Fahrsicherheit)
        val quests = generator.generate(
            routeLengthMeters = route.lengthMeters,
            ageBand = _state.value.ageBand,
            seed = System.nanoTime(),
            avoidMeters = route.maneuvers.map { it.atMeters },
        )
        val players = _state.value.players
        questOffset = 0.0
        adventure = AdventureSession(quests, playerCount = players)
        _state.update {
            it.copy(
                quest = null, feedback = null, newSticker = null, points = 0, stickers = 0, questsDone = 0,
                questsTotal = quests.size, scores = List(players) { 0 }, currentPlayer = 0,
            )
        }
    }

    fun setAgeBand(band: AgeBand) {
        prefs.edit().putString("age", band.name).apply()
        _state.update { it.copy(ageBand = band) }
        // Vor dem Start neu wuerfeln, damit Altersstufe gilt
        val r = _state.value.route
        if (r != null && !_state.value.navigating) newAdventure(r)
    }

    fun setPlayers(count: Int) {
        val n = count.coerceIn(1, 4)
        prefs.edit().putInt("players", n).apply()
        _state.update { it.copy(players = n) }
        val r = _state.value.route
        if (r != null && !_state.value.navigating) newAdventure(r)
    }

    fun showKids(show: Boolean) = _state.update { it.copy(kidsScreen = show) }

    fun answerQuest(choice: Int? = null) {
        val q = _state.value.quest ?: return
        val adv = adventure ?: return
        val before = adv.stickers
        val r = adv.answer(choice) ?: return
        val correctKey = q.optionKeys.getOrNull(r.correctIndex)
        val total = _state.value.totalPoints + r.pointsAwarded
        var sticker: Sticker? = null
        if (adv.stickers > before) {
            sticker = book.award(Random.Default)
            if (sticker != null) prefs.edit().putStringSet("album", book.collected.toSet()).apply()
        }
        prefs.edit().putInt("total_points", total).apply()
        arbiter.cancelQuest()
        _state.update {
            it.copy(
                quest = null,
                feedback = QuestFeedback(r.correct, r.pointsAwarded, correctKey),
                points = adv.points, stickers = adv.stickers, questsDone = adv.completed,
                scores = adv.scores.toList(), totalPoints = total,
                newSticker = sticker, album = book.collected.toSet(),
            )
        }
    }

    fun skipQuest() {
        adventure?.skip()
        arbiter.cancelQuest()
        _state.update { it.copy(quest = null) }
    }

    fun dismissFeedback() = _state.update { it.copy(feedback = null, newSticker = null) }

    fun startNavigation() {
        if (_state.value.route == null) return
        _state.update { it.copy(navigating = true) }
        _state.value.route?.maneuvers?.firstOrNull()?.instruction?.let(::speak)
        breakPlanner = BreakPlanner(intervalMs = breakIntervalMs(_state.value.ageBand))
        breakPlanning = false
        trafficJob?.cancel()
        trafficJob = scope.launch {
            while (isActive) {
                refreshTraffic()
                delay(TRAFFIC_REFRESH_MS)
            }
        }
    }

    fun stopNavigation() {
        _state.update {
            it.copy(
                navigating = false, route = null, destination = null, guidance = null, quest = null, feedback = null,
                kidsScreen = false, incidents = emptyList(), proposal = null,
                speedKmh = null, speedLimit = null, overspeed = false, lanes = null,
                curvy = false, breakProposal = null, breakTarget = null,
            )
        }
        trafficJob?.cancel()
        trafficJob = null
        announcedIncidents.clear()
        adventure = null
        engine = null
        planner = null
        tts?.stop()
    }

    private fun onLocation(loc: Location) {
        val pos = LatLon(loc.latitude, loc.longitude)
        _state.update { it.copy(position = pos) }
        val eng = engine ?: return
        if (!_state.value.navigating) return
        val g = eng.update(pos)
        _state.update { it.copy(guidance = g) }
        if (g.arrived) {
            speak(app.getString(R.string.arrived))
            stopNavigation()
            return
        }
        planner?.next(g)?.let(::speak)
        updateDrivingAssist(loc, g.alongMeters)
        val now0 = System.currentTimeMillis()
        val curvy = _state.value.sicknessMode && curveProfile?.isCurvy(g.alongMeters) == true
        if (curvy != _state.value.curvy) _state.update { it.copy(curvy = curvy) }
        if (sicknessCoach.onTick(curvy, now0)) {
            val res = when (sicknessPromptIndex++ % 3) {
                0 -> R.string.sickness_prompt_1
                1 -> R.string.sickness_prompt_2
                else -> R.string.sickness_prompt_3
            }
            speakQuest(app.getString(res))
        }
        updateBreaks(loc, g.alongMeters, now0)
        // Auf kurvigen Abschnitten keine neue Bildschirm-Aufgabe ausloesen; sie kommt auf gerader Strecke
        if (!curvy) adventure?.onProgress(questOffset + g.alongMeters)?.let { q -> onQuestTriggered(q, pos) }
        arbiter.poll(System.currentTimeMillis(), tts?.isSpeaking == true)?.let(::speakQuest)
        val now = System.currentTimeMillis()
        if (g.isOffRoute && now - lastReroute > 15_000) {
            lastReroute = now
            speak(app.getString(R.string.rerouting))
            calculateRoute()
        }
    }

    // ---------------------------------------------------------------- Pausen & Reiseuebelkeit

    fun setSicknessMode(on: Boolean) {
        prefs.edit().putBoolean("sickness_mode", on).apply()
        _state.update { it.copy(sicknessMode = on, curvy = if (on) it.curvy else false) }
    }

    /** Abstand zwischen Pausen nach Altersstufe der juengsten Gruppe. */
    private fun breakIntervalMs(age: AgeBand): Long = when (age) {
        AgeBand.MINI -> 60
        AgeBand.EXPLORER -> 75
        AgeBand.PRO -> 100
    } * 60_000L

    private fun updateBreaks(loc: Location, along: Double, nowMs: Long) {
        val route = _state.value.route ?: return
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0
        val due = breakPlanner.onTick(nowMs, kmh)
        if (due && !breakPlanning && _state.value.breakTarget == null && _state.value.breakProposal == null) planBreak(route, along)

        // Angenommene Pause: Abstand fortschreiben, Ankunft erkennen
        val target = _state.value.breakTarget ?: return
        val ahead = target.alongMeters - along
        _state.update { it.copy(breakAheadMeters = ahead) }
        if (ahead <= BREAK_ARRIVAL_METERS && kmh < 15.0) {
            val q = QuestCatalog.default.filter { it.type == QuestType.BREAK }.random().toQuest(questOffset + along)
            adventure?.forceQuest(q)
            breakPlanner.reset()
            _state.update { it.copy(breakTarget = null, quest = q, feedback = null, newSticker = null) }
            speak(app.getString(R.string.break_arrived))
            announceQuest(q)
        } else if (ahead < -800.0) {
            // vorbeigefahren: spaeter erneut erinnern
            breakPlanner.snooze()
            _state.update { it.copy(breakTarget = null) }
        }
    }

    private fun planBreak(route: Route, along: Double) {
        if (route.lengthMeters - along < 8_000.0) { breakPlanner.snooze(); return }
        breakPlanning = true
        scope.launch {
            try {
                val from = along + 4_000.0
                val to = minOf(route.lengthMeters - 1_000.0, along + 45_000.0)
                val stops = runCatching {
                    RestStops.parse(withContext(Dispatchers.IO) { fetchOverpass(RestStops.buildQuery(route, from, to)) }, route, from, to)
                }.getOrDefault(emptyList())
                val best = RestStops.pick(stops, targetAlongMeters = along + 20_000.0)
                if (best == null) {
                    breakPlanner.snooze()
                } else {
                    val ahead = best.alongMeters - along
                    _state.update { it.copy(breakProposal = BreakProposal(best, ahead)) }
                    speakQuest(app.getString(R.string.break_speech, distanceText(ahead), stopText(best)))
                }
            } finally {
                breakPlanning = false
            }
        }
    }

    fun acceptBreak() {
        val p = _state.value.breakProposal ?: return
        _state.update { it.copy(breakProposal = null, breakTarget = p.stop, breakAheadMeters = p.aheadMeters) }
    }

    fun laterBreak() {
        breakPlanner.snooze()
        _state.update { it.copy(breakProposal = null) }
    }

    private fun distanceText(meters: Double): String =
        if (meters >= 1000) app.getString(R.string.unit_km, meters / 1000.0) else app.getString(R.string.unit_m, meters.toInt().coerceAtLeast(0))

    /** "Rastplatz Name (mit Spielplatz & WC)". */
    fun stopText(s: RestStop): String {
        val kind = app.getString(
            when (s.kind) {
                StopKind.REST_AREA -> R.string.stop_rest_area
                StopKind.SERVICES -> R.string.stop_services
                StopKind.PLAYGROUND -> R.string.stop_playground
                StopKind.TOILETS -> R.string.stop_toilets
            },
        )
        val features = buildList {
            if (s.playgroundNearby && s.kind != StopKind.PLAYGROUND) add(app.getString(R.string.break_playground))
            if (s.toiletsNearby && s.kind != StopKind.TOILETS) add(app.getString(R.string.break_toilets))
        }
        return listOfNotNull(kind, s.name).joinToString(" ") +
            if (features.isNotEmpty()) " (" + app.getString(R.string.break_with, features.joinToString(" & ")) + ")" else ""
    }

    // ---------------------------------------------------------------- Tempolimit & Spuren

    private fun updateDrivingAssist(loc: Location, along: Double) {
        val route = _state.value.route ?: return
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6 else null
        val limit = speedProfile.limitAt(along)
        val laneNow = laneAnnouncer?.current(along)
        _state.update {
            it.copy(
                speedKmh = kmh?.toInt(),
                speedLimit = limit,
                overspeed = kmh != null && SpeedProfile.isOverspeed(kmh, limit),
                lanes = laneNow,
            )
        }
        laneAnnouncer?.next(along)?.let { g ->
            val res = when (g.hint()) {
                LaneHint.LEFT -> R.string.lane_hint_left
                LaneHint.RIGHT -> R.string.lane_hint_right
                LaneHint.MIDDLE -> R.string.lane_hint_middle
                LaneHint.ANY -> null
            }
            if (res != null) speak(app.getString(res))
        }
        ensureSpeedWindow(route, along)
    }

    /** Laedt die Tempolimits fuer die naechsten ca. 25 km nach, bevor das Fenster endet. */
    private fun ensureSpeedWindow(route: Route, along: Double) {
        val now = System.currentTimeMillis()
        if (speedFetching || now < speedRetryAt) return
        if (speedProfile.coveredUntilMeters >= route.lengthMeters - 1.0) return
        if (speedProfile.coveredUntilMeters - along > SPEED_PREFETCH_METERS) return
        speedFetching = true
        val startM = (along - 200.0).coerceAtLeast(0.0)
        val endM = minOf(route.lengthMeters, along + SPEED_WINDOW_METERS)
        val last = route.line.points.size - 1
        val i0 = route.line.indexAtMeters(startM)
        val i1 = minOf(last, route.line.indexAtMeters(endM) + 1)
        if (i1 <= i0) { speedFetching = false; return }
        scope.launch {
            val ok = runCatching {
                val body = fetchValhalla("trace_attributes", TraceAttributes.buildRequest(route, i0, i1))
                val segs = TraceAttributes.parse(body, route, i0)
                if (_state.value.route === route) {
                    speedProfile.addWindow(route.line.cumulative[i0], route.line.cumulative[i1], segs)
                }
            }.isSuccess
            if (!ok) speedRetryAt = System.currentTimeMillis() + 30_000
            speedFetching = false
        }
    }

    // ---------------------------------------------------------------- Verkehr

    /** Holt Meldungen fuer die Autobahnen der Route (DE) und gleicht sie mit der Route ab. */
    private suspend fun refreshTraffic() {
        val route = _state.value.route ?: return
        val roads = AutobahnApi.roadsFromNames(route.maneuvers.flatMap { it.streetNames }).take(MAX_TRAFFIC_ROADS)
        if (roads.isEmpty()) return
        val all = withContext(Dispatchers.IO) {
            roads.flatMap { r ->
                listOf(AutobahnApi.WARNING, AutobahnApi.CLOSURE).flatMap { svc ->
                    runCatching { AutobahnApi.parse(fetchAutobahn(r, svc), svc) }.getOrDefault(emptyList())
                }
            }
        }
        if (_state.value.route !== route) return // Route hat sich inzwischen geaendert
        val progress = _state.value.guidance?.alongMeters ?: 0.0
        val onRoute = IncidentMatcher.match(route, all, progress)
        _state.update { it.copy(incidents = onRoute) }

        for (ri in onRoute) {
            if (advisor.isSignificant(ri) && ri.aheadMeters in 0.0..ANNOUNCE_AHEAD_METERS && announcedIncidents.add(ri.incident.id)) {
                speak(trafficAheadText(ri))
            }
        }
        advisor.next(onRoute)?.let { considerReroute(it) }
    }

    private fun typeName(t: IncidentType): String = app.getString(
        when (t) {
            IncidentType.JAM -> R.string.incident_jam
            IncidentType.ACCIDENT -> R.string.incident_accident
            IncidentType.CLOSURE -> R.string.incident_closure
            IncidentType.HAZARD -> R.string.incident_hazard
        },
    )

    private fun trafficAheadText(ri: RouteIncident): String {
        val dist = if (ri.aheadMeters >= 1000) app.getString(R.string.unit_km, ri.aheadMeters / 1000.0)
        else app.getString(R.string.unit_m, ri.aheadMeters.toInt().coerceAtLeast(0))
        val i = ri.incident
        val delay = i.delayMinutes
        return if (delay != null && delay > 0) {
            app.getString(R.string.traffic_ahead, dist, typeName(i.type), i.road, delay)
        } else {
            app.getString(R.string.traffic_ahead_nodelay, dist, typeName(i.type), i.road)
        }
    }

    /** Berechnet eine Route um die Meldung herum und schlaegt sie vor, wenn sie sich lohnt. */
    private suspend fun considerReroute(ri: RouteIncident) {
        advisor.markConsidered(ri.incident.id)
        val s = _state.value
        val from = s.position ?: return
        val dest = s.destination ?: return
        val route = s.route ?: return
        val loaded = runCatching {
            loadRoute(from, dest.position, ExclusionZones.forIncident(ri.incident))
        }.getOrNull() ?: return
        val alt = loaded.route

        val remainingFraction = ((s.guidance?.remainingMeters ?: route.lengthMeters) / route.lengthMeters).coerceIn(0.0, 1.0)
        val currentSeconds = route.durationSeconds * remainingFraction + (ri.incident.delayMinutes ?: 0) * 60.0
        val savedMin = ((currentSeconds - alt.durationSeconds) / 60.0).toInt()
        // Bei Vollsperrung immer anbieten, sonst nur wenn es sich lohnt
        if (ri.incident.blocked || savedMin >= MIN_SAVED_MINUTES) {
            _state.update { it.copy(proposal = RerouteProposal(alt, loaded.lanes, savedMin.coerceAtLeast(0), ri.incident.blocked, ri)) }
            speak(app.getString(R.string.reroute_speech))
        }
    }

    fun acceptReroute() {
        val p = _state.value.proposal ?: return
        questOffset += _state.value.guidance?.alongMeters ?: 0.0
        installRoute(LoadedRoute(p.route, p.lanes))
        _state.update { it.copy(route = p.route, guidance = null, proposal = null, incidents = emptyList(), lanes = null) }
        scope.launch { refreshTraffic() }
    }

    fun dismissReroute() = _state.update { it.copy(proposal = null) }

    /** Navigationsansage: hat Vorrang und unterbricht Quest-Ansagen. */
    private fun speak(text: String) {
        arbiter.navSpoken(System.currentTimeMillis())
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kq-nav-${System.nanoTime()}")
    }

    private fun speakQuest(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "kq-quest-${System.nanoTime()}")
    }

    private fun onQuestTriggered(q: Quest, pos: LatLon) {
        val adv = adventure
        _state.update {
            it.copy(quest = q, feedback = null, newSticker = null, currentPlayer = adv?.currentPlayer ?: 0)
        }
        when {
            q.textKey == RouteFacts.PENDING_KEY -> resolveQuest(q) {
                val facts = runCatching {
                    RouteFacts.parse(withContext(Dispatchers.IO) { fetchOverpass(RouteFacts.buildQuery(pos.lat, pos.lon)) })
                }.getOrDefault(emptyList())
                RouteFacts.resolve(q, RouteFacts.pick(facts, Random.Default))
            }
            q.textKey == WikiStories.PENDING_KEY -> resolveQuest(q) {
                val age = _state.value.ageBand
                val lang = Locale.getDefault().language
                var story: Story? = null
                // Erst Geraetesprache, dann Englisch als Rueckfall
                for (l in listOf(lang, "en").distinct()) {
                    val list = runCatching { WikiStories.parse(fetchUrl(WikiStories.buildUrl(l, pos.lat, pos.lon, age)), age) }
                        .getOrDefault(emptyList())
                    story = WikiStories.pick(list, Random.Default)
                    if (story != null) break
                }
                WikiStories.resolve(q, story)
            }
            else -> announceQuest(q)
        }
    }

    /** Platzhalter-Quest asynchron durch konkrete Quest ersetzen (bei Fehler: Rueckfall-Aufgabe). */
    private fun resolveQuest(q: Quest, build: suspend () -> Quest) {
        scope.launch {
            val resolved = runCatching { build() }.getOrElse { RouteFacts.resolve(q, null) }
            if (adventure?.active?.triggerMeters == q.triggerMeters) {
                adventure?.replaceActive(resolved)
                _state.update { it.copy(quest = resolved) }
                announceQuest(resolved)
            }
        }
    }

    private fun announceQuest(q: Quest) {
        val s = _state.value
        val text = buildString {
            if (s.players > 1) append(app.getString(R.string.kids_turn, app.getString(R.string.kids_player, s.currentPlayer + 1))).append(' ')
            append(app.stringByKey(q.textKey, q.args))
        }
        arbiter.offerQuest(text, System.currentTimeMillis(), tts?.isSpeaking == true)?.let(::speakQuest)
    }

}
