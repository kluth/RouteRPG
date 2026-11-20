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
import app.kiloquest.game.Avatars
import app.kiloquest.nav.AutobahnRestAreas
import app.kiloquest.nav.StopFeature
import app.kiloquest.nav.ToiletChoice
import app.kiloquest.nav.ToiletFinder
import app.kiloquest.nav.ToiletKind
import app.kiloquest.nav.ToiletStop
import app.kiloquest.game.CountryGuess
import app.kiloquest.game.QuestContext
import app.kiloquest.game.QuestFit
import app.kiloquest.game.BonusPlanner
import app.kiloquest.game.AgeBand
import app.kiloquest.game.AnswerMatcher
import app.kiloquest.game.KiloPraise
import app.kiloquest.game.Quest
import app.kiloquest.game.QuestCatalog
import app.kiloquest.game.QuestGenerator
import app.kiloquest.game.QuestType
import app.kiloquest.game.LanguageCourse
import app.kiloquest.game.LanguageCourses
import app.kiloquest.game.Aurora
import app.kiloquest.game.AuroraChance
import app.kiloquest.game.Moon
import app.kiloquest.game.NatureSpotting
import app.kiloquest.game.PlaneSpotting
import app.kiloquest.game.Sun
import app.kiloquest.game.Phrase
import app.kiloquest.game.QuestKind
import app.kiloquest.game.RouteFacts
import app.kiloquest.game.Story
import app.kiloquest.game.WikiStories
import app.kiloquest.game.Sticker
import app.kiloquest.game.StickerBook
import app.kiloquest.nav.SpeechArbiter
import kotlin.random.Random
import kotlinx.coroutines.withContext
import app.kiloquest.nav.AnnouncementPlanner
import app.kiloquest.nav.Corridor
import app.kiloquest.nav.CountryTimeline
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
import app.kiloquest.nav.LegSummary
import app.kiloquest.nav.TripApi
import app.kiloquest.nav.BookedPlanner
import app.kiloquest.nav.BookedStay
import app.kiloquest.nav.DayEnd
import app.kiloquest.nav.haversine
import app.kiloquest.nav.DayPlanner
import app.kiloquest.nav.LodgingFinder
import app.kiloquest.nav.LodgingOption
import app.kiloquest.nav.Itinerary
import app.kiloquest.nav.ItineraryPlanner
import app.kiloquest.nav.PackItem
import app.kiloquest.nav.PlanPlace
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import app.kiloquest.nav.OfflinePackMeta
import app.kiloquest.nav.OfflinePackCodec
import app.kiloquest.nav.VoiceCommands
import app.kiloquest.nav.VoiceCommand
import app.kiloquest.nav.RouteAvoid
import app.kiloquest.nav.NavIntents
import app.kiloquest.nav.NavIntent
import app.kiloquest.nav.ScenicFinder
import app.kiloquest.nav.Scenic
import app.kiloquest.nav.PhotoPrompter
import app.kiloquest.nav.PhotoPrompt
import app.kiloquest.nav.TravelDiary
import app.kiloquest.nav.DiaryKind
import app.kiloquest.nav.DiaryEntry
import app.kiloquest.nav.OverspeedAlerter
import app.kiloquest.nav.TripOps
import app.kiloquest.nav.PlanStay
import app.kiloquest.nav.PlanStore
import app.kiloquest.nav.PlannedTrip
import app.kiloquest.nav.LaneGuidance
import app.kiloquest.nav.LaneHint
import app.kiloquest.nav.LaneParser
import app.kiloquest.nav.SpeedProfile
import app.kiloquest.nav.TraceAttributes
import app.kiloquest.nav.WeatherApi
import app.kiloquest.nav.WeatherCondition
import app.kiloquest.nav.WeatherHazard
import app.kiloquest.nav.WeatherSummary
import app.kiloquest.nav.IncidentType
import app.kiloquest.nav.RerouteAdvisor
import app.kiloquest.nav.Riddle
import app.kiloquest.nav.RiddleRules
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

data class QuestFeedback(val correct: Boolean, val points: Int, val correctOptionKey: String?)

/** Text einer String-Ressource per Schluessel (Quest-Texte sind sprachneutral als Schluessel gespeichert). */
fun Context.stringByKey(key: String, args: List<String> = emptyList()): String {
    // "=Text": direkter Text (z. B. Woerter in der Landessprache), keine Ressource
    if (key.startsWith("=")) return key.substring(1)
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

/** Mehrtagesreise: vom Nutzer gesetzte Etappenziele; jede Etappe ist eine eigene Route mit eigenem Abenteuer. */
data class TripState(
    val origin: LatLon?,
    val stops: List<Place>,
    val legs: List<LegSummary> = emptyList(),
    val currentStage: Int = 0,
    val stagePoints: List<Int> = emptyList(),
    val loading: Boolean = false,
)

/** Fortschritt des Offline-Reise-Pakets. */
enum class PackagePhase { IDLE, MAP, LIMITS, COUNTRIES, STOPS, QUESTS, READY, ERROR }

data class PackageState(val phase: PackagePhase = PackagePhase.IDLE, val progress: Double = 0.0, val estimatedMb: Int = 0, val part: Int = 0, val parts: Int = 0)

enum class ToiletPhase { SEARCHING, HEADING, ARRIVED, NONE }

data class ToiletState(val phase: ToiletPhase, val label: String = "", val seconds: Double? = null, val meters: Double? = null)

/** Vorgeschlagene Pause voraus. */
data class BreakProposal(val stop: RestStop, val aheadMeters: Double)

/** Route samt Spurinformation. */
/** Unterkunftssuche fuer die Uebernachtung am Ende von Etappe [stopIndex]. */
data class LodgingUi(
    val stopIndex: Int,
    val loading: Boolean = true,
    val options: List<LodgingOption> = emptyList(),
    val failed: Boolean = false,
    /** Gesetzt, wenn die Suche fuer eine Uebernachtung einer geplanten Reise laeuft (Tag als Epochentag). */
    val planDay: Int? = null,
)

/** Uebernachtung unterwegs ohne Buchung: Vorschlagspunkt auf der Route mit Datum. */
data class PlanNight(val legIndex: Int, val day: Int, val point: LatLon, val name: String)

data class PlanResult(
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    val itinerary: Itinerary? = null,
    val nights: List<PlanNight> = emptyList(),
    /** Zusatzfahrzeit ueber alle Buchungen gegenueber der direkten Route. */
    val extraMinutes: Int? = null,
    /** Abstand jeder Buchung zur direkten Route in km. */
    val offRouteKm: List<Pair<String, Int>> = emptyList(),
    /** Routen je Etappe (fuer das Offline-Paket) und geschaetzte Kartengroesse aller Etappen. */
    val legRoutes: List<Route> = emptyList(),
    val packMb: Int = 0,
)

/** Hoergeschichte: wird gesucht ([loading]), ist da ([title]/[text]) oder nicht gefunden ([failed]). */
data class StoryCard(val loading: Boolean = false, val title: String = "", val text: String = "", val failed: Boolean = false)

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
    /** Noch ausstehende Aufgaben (Vorschau); Platzhalter werden vorgeladen oder live gefuellt. */
    val plan: List<Quest> = emptyList(),
    /** Gekoppeltes Geraet: Zustand kommt vom Hauptgeraet (keine eigene Route). */
    val remote: Boolean = false,
    /** Figur, die die eigene Position auf der Karte zeigt (Emoji, frei waehlbar). */
    val avatar: String = Avatars.DEFAULT,
    /** Kinderkarte mit echten Kacheln (sonst schematisch); faellt ohne Netz von selbst auf schematisch zurueck. */
    val realMap: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val settingsScreen: Boolean = false,
    /** Klo-Alarm: laeuft gerade (Suche, Fahrt zum Halt, Angekommen). */
    val toilet: ToiletState? = null,
    /** Ein Kind (Geraetename) hat "Ich muss mal" gedrueckt. */
    val toiletRequest: String? = null,
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
    val weather: WeatherSummary? = null,
    val trip: TripState? = null,
    val tripScreen: Boolean = false,
    /** Gespeicherte Lieblingsorte (Zuhause, Hotels, Campingplaetze ...). */
    val favorites: List<Place> = emptyList(),
    /** Bereits gebuchte Unterkuenfte; die Aufteilung der Route richtet sich danach. */
    val booked: List<Place> = emptyList(),
    val lodging: LodgingUi? = null,
    /** Fest geplante Reisen mit Datum (auch Monate im Voraus). */
    val plans: List<PlannedTrip> = emptyList(),
    val plansScreen: Boolean = false,
    /** Reisetagebuch (lokal) und seine Ansicht. */
    val diary: List<DiaryEntry> = emptyList(),
    val diaryScreen: Boolean = false,
    /** Hoergeschichte auf Wunsch (Wikipedia, wird vorgelesen). */
    val storyCard: StoryCard? = null,
    /** Gespeicherte Offline-Pakete (Routen, die ohne Netz navigierbar sind). */
    val packs: List<OfflinePackMeta> = emptyList(),
    val planOpenId: String? = null,
    val planResult: PlanResult? = null,
    /** Hinweise zur letzten Aufteilung (z. B. Umweg, lange Etappe). */
    val tripNotes: List<String> = emptyList(),
    /** Land der aktuellen Position (ISO-Code), falls erkannt. */
    val country: String? = null,
    val riddles: List<Riddle> = emptyList(),
    val riddleScreen: Boolean = false,
    val parentScreen: Boolean = false,
    val voiceMode: Boolean = false,
    val voiceAvailable: Boolean = false,
    val listening: Boolean = false,
    val pkg: PackageState = PackageState(),
    /** Fahrtrichtung in Grad (0 = Norden), solange man sich bewegt. */
    val bearing: Float? = null,
    /** Notausgang: im Auto die einfache Zeichnung statt der MapLibre-Karte nutzen. */
    val carSimpleMap: Boolean = false,
)

private const val CONTENT_HISTORY = 300
private const val PRELOAD_WINDOW = 80
private const val PRELOAD_GAP_MS = 1_200L
private const val PRELOAD_IDLE_MS = 30_000L
private const val TRAFFIC_REFRESH_MS = 5 * 60_000L
private const val MAX_TRAFFIC_ROADS = 10
private const val ANNOUNCE_AHEAD_METERS = 100_000.0
private const val MIN_SAVED_MINUTES = 5
private const val MAX_RECENTS = 5
private const val PACKAGE_MIN_ZOOM = 5
private const val PACKAGE_MAX_ZOOM = 12
private const val VOICE_ATTEMPTS = 3
private const val COUNTRY_CHECK_METERS = 10_000.0
private const val WEATHER_REFRESH_MS = 30 * 60_000L
private const val BREAK_ARRIVAL_METERS = 400.0
private const val SPEED_WINDOW_METERS = 25_000.0
private const val SPEED_PREFETCH_METERS = 5_000.0

/** Gemeinsamer Navigationszustand fuer Handy-UI und Android-Auto (lebt im Application-Prozess). */
class NavController(private val app: Application) : KidsActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences("kq", Context.MODE_PRIVATE)
    private val generator = QuestGenerator(QuestCatalog.default)
    private var adventure: AdventureSession? = null
    private var bonusPlanner: BonusPlanner? = null
    private var preloadJob: kotlinx.coroutines.Job? = null
    private val preloadFailed = HashSet<Double>()
    private val tripStatics = HashMap<String, Int>()
    /** Ziel, zu dem es nach dem Klo-Halt weitergeht (null, wenn vorher nicht navigiert wurde). */
    private var toiletResume: Place? = null
    /** Beim letzten Klo-Alarm gab es Kandidaten, aber alle waren bei Ankunft geschlossen. */
    private var toiletOnlyClosed = false
    private var toiletJob: kotlinx.coroutines.Job? = null
    /** Altersstufe je gekoppeltem Kinder-Geraet (Spielernummer = Index). */
    private var kidBands: List<AgeBand> = emptyList()

    private val arbiter = SpeechArbiter()
    private val overspeed = OverspeedAlerter()

    /** Roadtrip-Fotostory: Entscheidung auf dem Hauptgeraet, Bitte nur an gekoppelte Beifahrer-Geraete (siehe LinkController). */
    interface PhotoAudience {
        fun available(): Boolean
        fun send(prompt: PhotoPrompt)
    }
    var photoAudience: PhotoAudience? = null
    private val photoPrompter = PhotoPrompter(seed = System.nanoTime())
    private var scenic: Scenic = Scenic.NONE
    private var scenicAt = 0L
    private var scenicJob: Job? = null
    private var lastPhotoTick = 0L
    private val kiloClips = KiloVoicePlayer(app)
    private var lastPraise = 0
    private var lastRetry = 0
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
    private var weatherJob: Job? = null
    private var autoStartAfterRoute = false
    private var countryChecking = false
    private val usedRiddles = HashSet<String>()
    private val voice = VoiceAnswers(app)
    private val offline by lazy { OfflinePackage(app) }
    private val packStore by lazy { OfflineStore(app) }
    private var countryTimeline: CountryTimeline? = null
    private var packageJob: Job? = null
    private var voiceJob: Job? = null
    private var lastCountryCheckAlong = -1e9
    private val taughtPhrases = HashSet<Phrase>()
    private val announcedHazards = HashSet<WeatherHazard>()
    /** Meter, die auf frueheren Routen schon gefahren wurden (Quest-Positionen gelten fuer die Gesamtfahrt). */
    private var questOffset = 0.0
    private val book = StickerBook(prefs.getStringSet("album", emptySet()).orEmpty())

    private val _state = MutableStateFlow(
        UiState(
            ageBand = runCatching { AgeBand.valueOf(prefs.getString("age", null) ?: "EXPLORER") }.getOrDefault(AgeBand.EXPLORER),
            players = prefs.getInt("players", 1).coerceIn(1, 4),
            realMap = prefs.getBoolean("real_map", true),
            settings = SettingsStore.load(prefs),
            avatar = prefs.getString("avatar", null)?.let { Avatars.sanitizeCustom(it) } ?: Avatars.DEFAULT,
            album = book.collected.toSet(),
            totalPoints = prefs.getInt("total_points", 0),
            recents = loadRecents(),
            sicknessMode = prefs.getBoolean("sickness_mode", true),
            trip = loadTrip(),
            favorites = loadPlaces("favorites"),
            booked = loadPlaces("booked"),
            plans = PlanStore.decode(prefs.getString("plans", null)),
            packs = packStore.list(),
            diary = TravelDiary.decode(prefs.getString("diary", null)),
            riddles = loadRiddles(),
            voiceMode = prefs.getBoolean("voice_mode", false),
            voiceAvailable = VoiceAnswers.isAvailable(app),
            carSimpleMap = prefs.getBoolean("car_simple_map", false),
        ),
    )
    val state: StateFlow<UiState> = _state

    init {
        // Bei jeder neuen Aufgabe (Sprachantworten an): nach der Ansage zuhoeren
        scope.launch {
            var lastQuest: Quest? = null
            _state.collect { st ->
                if (st.quest !== lastQuest) {
                    lastQuest = st.quest
                    onQuestForVoice(st.quest)
                }
            }
        }
    }

    private val locationManager = app.getSystemService(LocationManager::class.java)
    private val mainHandler = android.os.Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = TextToSpeech(app) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { mainHandler.post { onSpeechDone(utteranceId) } }
                override fun onDone(utteranceId: String?) { mainHandler.post { onSpeechDone(utteranceId) } }
            })
        }
    }
    // Fremdsprachige Woerter nacheinander in der Landessprache sprechen (Sprache nur kurz umschalten)
    private val foreignQueue = ArrayDeque<String>()
    private var foreignLocale: Locale? = null
    private var foreignActive = false
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
        oneOffAvoid = emptySet()
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
                scope.launch { refreshWeather(announce = false) }
                if (autoStartAfterRoute) { autoStartAfterRoute = false; startNavigation() }
            }.onFailure {
                autoStartAfterRoute = false
                _state.update { it.copy(busy = false, error = R.string.error_route) }
            }
        }
    }

    /** Route plus Spurinformation (zweite Anfrage im OSRM-Format; Spuren sind optional). */
    private suspend fun loadRoute(from: LatLon, to: LatLon, excludes: List<List<LatLon>>): LoadedRoute {
        val lang = Locale.getDefault().toLanguageTag()
        val avoid = routeAvoid()
        val route = ValhallaApi.parseRoute(fetchRoute(ValhallaApi.buildRouteRequest(from, to, lang, excludePolygons = excludes, avoid = avoid)))
        val lanes = runCatching {
            LaneParser.parseOsrm(fetchRoute(ValhallaApi.buildRouteRequest(from, to, lang, excludePolygons = excludes, osrm = true, avoid = avoid)), route)
        }.getOrDefault(emptyList())
        return LoadedRoute(route, lanes)
    }

    /** Richtet Fuehrung, Spuren und Tempolimit-Cache fuer eine neue Route ein. */
    private fun installRoute(l: LoadedRoute) {
        engine = GuidanceEngine(l.route)
        planner = AnnouncementPlanner(l.route)
        laneAnnouncer = LaneAnnouncer(l.lanes)
        currentLanes = l.lanes
        offlineToilets = emptyList()
        curveProfile = CurveProfile(l.route)
        speedProfile = SpeedProfile()
        overspeed.reset()
        speedFetching = false
        speedRetryAt = 0L
        countryTimeline = null
        packageJob?.cancel()
        _state.update { it.copy(pkg = PackageState(estimatedMb = estimateMb(l.route))) }
    }

    private fun newAdventure(route: Route) {
        val players = maxOf(_state.value.players, kidBands.size)
        // Keine Quests nahe Abbiegungen (Fahrsicherheit)
        val quests = generator.generate(
            routeLengthMeters = route.lengthMeters,
            ageBand = _state.value.ageBand,
            seed = System.nanoTime(),
            avoidMeters = route.maneuvers.map { it.atMeters },
            // Aufgaben passend zu Ort, Land und (geschaetzter) Tageszeit am Ausloesepunkt
            contextAt = { m -> contextAt(route, m) },
            usage = loadUsage(),
            ageBandAt = if (kidBands.isEmpty()) null else { i -> kidBands.getOrNull(i % players) ?: _state.value.ageBand },
        )
        adoptQuests(quests)
    }

    /** Uebernimmt einen fertigen Aufgabenplan (neu erzeugt oder aus einem Offline-Paket). */
    private fun adoptQuests(quests: List<Quest>) {
        val players = maxOf(_state.value.players, kidBands.size)
        tripStatics.clear()
        quests.forEach { q -> if (QuestCatalog.default.firstOrNull { it.id == q.templateId }?.dynamic == false) tripStatics.merge(q.templateId, 1, Int::plus) }
        questOffset = 0.0
        adventure = AdventureSession(quests, playerCount = players)
        val bonusKeys = setOf(RouteFacts.PENDING_KEY, WikiStories.PENDING_KEY, NatureSpotting.PENDING_KEY, PlaneSpotting.PENDING_KEY, LanguageCourses.PENDING_KEY)
        bonusPlanner = BonusPlanner(QuestCatalog.default.filter { it.textKey in bonusKeys && _state.value.ageBand in it.ageBands }, System.nanoTime())
        preloadFailed.clear()
        _state.update {
            it.copy(
                quest = null, feedback = null, newSticker = null, points = 0, stickers = 0, questsDone = 0,
                questsTotal = quests.size, scores = List(players) { 0 }, currentPlayer = 0, plan = quests, players = players,
            )
        }
        startPreload()
    }

    /** Gekoppelte Kinder-Geraete bestimmen Spielerzahl und Altersstufen; vor der Fahrt wird neu geplant. */
    fun setKidDevices(bands: List<AgeBand>) {
        if (bands == kidBands) return
        kidBands = bands
        val r = _state.value.route
        if (r != null && !_state.value.navigating) newAdventure(r)
        else _state.update { it.copy(players = maxOf(it.players, bands.size)) }
    }

    /** Figur fuer die eigene Position waehlen (aus dem Katalog oder ein eigenes Emoji); ungueltige Eingaben werden ignoriert. */
    fun showSettings(show: Boolean) = _state.update { it.copy(settingsScreen = show) }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value.settings)
        SettingsStore.save(prefs, next)
        ReminderScheduler.sync(app, next.tripReminders)
        _state.update { it.copy(settings = next) }
    }

    /** Zuletzt gesuchte Ziele und Reise-Etappen vergessen. */
    fun clearRecents() {
        saveRecents(emptyList())
        _state.update { it.copy(recents = emptyList()) }
    }

    /** Gemerkte Inhalte (gestellte Orte, Tiere, Fakten) und die Haeufigkeit fester Aufgaben zuruecksetzen. */
    fun resetQuestMemory() {
        prefs.edit().remove("content_used").remove("quest_usage").apply()
        contentUsed.clear()
    }

    /** Alle Einstellungen dieses Dialogs auf Standard (Kinder-Daten, Album und Punkte bleiben). */
    fun resetSettings() {
        SettingsStore.save(prefs, AppSettings())
        prefs.edit().putBoolean("sickness_mode", true).putBoolean("real_map", true).putBoolean("car_simple_map", false).apply()
        _state.update { it.copy(settings = AppSettings(), realMap = true, sicknessMode = true, carSimpleMap = false) }
    }

    override fun setRealMap(on: Boolean) {
        prefs.edit().putBoolean("real_map", on).apply()
        _state.update { it.copy(realMap = on) }
    }

    override fun setAvatar(emoji: String) {
        val a = Avatars.sanitizeCustom(emoji) ?: return
        prefs.edit().putString("avatar", a).apply()
        _state.update { it.copy(avatar = a) }
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

    fun setCarSimpleMap(on: Boolean) {
        prefs.edit().putBoolean("car_simple_map", on).apply()
        _state.update { it.copy(carSimpleMap = on) }
    }

    fun showParent(show: Boolean) = _state.update { it.copy(parentScreen = show) }
    override fun requestToilet() = toiletRequested(null)

    override fun showKids(show: Boolean) = _state.update { it.copy(kidsScreen = show) }

    override fun speakAloud(text: String): Boolean {
        if (!_state.value.settings.kiloVoice) return false
        kiloClips.stop()
        return kiloClips.play(text)
    }

    override fun answerQuest(choice: Int?) = answerQuestFrom(choice, null)

    /** [player]: Spieler eines gekoppelten Geraets, der antwortet (bekommt die Punkte); null = aktueller Spieler. */
    fun answerQuestFrom(choice: Int?, player: Int?) {
        val q = _state.value.quest ?: return
        val adv = adventure ?: return
        val before = adv.stickers
        val r = adv.answer(choice, player) ?: return
        kiloClips.stop() // die Frage nicht mehr weitersprechen
        val correctKey = q.optionKeys.getOrNull(r.correctIndex)
        val total = _state.value.totalPoints + r.pointsAwarded
        var sticker: Sticker? = null
        if (adv.stickers > before) {
            sticker = book.award(Random.Default)
            if (sticker != null) prefs.edit().putStringSet("album", book.collected.toSet()).apply()
        }
        prefs.edit().putInt("total_points", total).apply()
        if (q.type != QuestType.STORY) {
            logDiary(DiaryKind.QUEST, app.stringByKey(q.textKey, q.args).take(90), points = r.pointsAwarded)
        }
        sticker?.let { logDiary(DiaryKind.STICKER, it.emoji) }
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
        speakFeedback(r.correct, sticker != null)
    }

    /** Kilo lobt oder tröstet mit einer wechselnden Zeile; Navigationsansagen werden dabei nie gestört. */
    private fun speakFeedback(correct: Boolean, newSticker: Boolean) {
        if (tts?.isSpeaking == true) return
        val n = KiloPraise.pick(correct, if (correct) lastPraise else lastRetry, Random.Default)
        if (correct) lastPraise = n else lastRetry = n
        speakQuest(app.stringByKey(KiloPraise.key(correct, n)))
        if (newSticker) speakQuest(app.getString(R.string.kids_new_sticker))
    }

    override fun skipQuest() {
        adventure?.skip()
        kiloClips.stop()
        arbiter.cancelQuest()
        _state.update { it.copy(quest = null) }
    }

    override fun dismissFeedback() = _state.update { it.copy(feedback = null, newSticker = null) }

    fun startNavigation() {
        if (_state.value.route == null) return
        _state.update { it.copy(navigating = true) }
        logDiary(DiaryKind.TRIP_START, _state.value.destination?.name.orEmpty())
        photoPrompter.start(System.currentTimeMillis())
        startScenicJob()
        if (_state.value.settings.voiceCommands) startCommandListening()
        // Navigation laeuft auch im Hintergrund/bei gesperrtem Bildschirm weiter (Vordergrunddienst)
        KeepAliveService.start(app)
        _state.value.route?.maneuvers?.firstOrNull()?.instruction?.let(::speak)
        usedRiddles.clear()
        breakPlanner = BreakPlanner(intervalMs = breakIntervalMs(_state.value.ageBand))
        bonusPlanner?.reset(questOffset)
        breakPlanning = false
        weatherJob?.cancel()
        announcedHazards.clear()
        weatherJob = scope.launch {
            var first = true
            while (isActive) {
                refreshWeather(announce = true, speakSummary = first)
                first = false
                delay(WEATHER_REFRESH_MS)
            }
        }
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
                curvy = false, breakProposal = null, breakTarget = null, weather = null, country = null,
                toilet = null, toiletRequest = null,
            )
        }
        toiletJob?.cancel(); toiletJob = null; toiletResume = null
        voiceJob?.cancel()
        voice.stop()
        trafficJob?.cancel()
        trafficJob = null
        weatherJob?.cancel()
        weatherJob = null
        taughtPhrases.clear()
        usedRiddles.clear()
        lastCountryCheckAlong = -1e9
        announcedIncidents.clear()
        adventure = null
        engine = null
        planner = null
        tts?.stop()
        kiloClips.stop()
        photoPrompter.stop()
        scenicJob?.cancel()
        stopCommandListening()
        oneOffAvoid = emptySet()
    }

    private fun onLocation(loc: Location) {
        val pos = LatLon(loc.latitude, loc.longitude)
        _state.update { it.copy(position = pos, bearing = if (loc.hasBearing() && loc.speed > 1.5f) loc.bearing else it.bearing) }
        val eng = engine ?: return
        if (!_state.value.navigating) return
        val g = eng.update(pos)
        _state.update { it.copy(guidance = g) }
        if (g.arrived) {
            // Klo-Alarm: am Halt bleibt die Navigation offen, bis der Beifahrer "Weiter" tippt (Kinder brauchen Zeit)
            _state.value.toilet?.let { t ->
                if (t.phase == ToiletPhase.HEADING) {
                    _state.update { it.copy(toilet = t.copy(phase = ToiletPhase.ARRIVED)) }
                    speak(app.getString(R.string.toilet_arrived))
                }
                return
            }
            val stageMessage = onStageArrived()
            logDiary(DiaryKind.ARRIVAL, _state.value.destination?.name.orEmpty(), meters = _state.value.route?.lengthMeters ?: 0.0)
            speak(stageMessage ?: app.getString(R.string.arrived))
            stopNavigation()
            return
        }
        planner?.next(g)?.let(::speak)
        updateDrivingAssist(loc, g.alongMeters)
        maybePromptPhoto(g)
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
        checkCountry(pos, g.alongMeters)
        // Auf kurvigen Abschnitten keine neue Bildschirm-Aufgabe ausloesen; sie kommt auf gerader Strecke
        if (!curvy) {
            maybeAddBonus(questOffset + g.alongMeters, g.metersToNextManeuver)
            adventure?.onProgress(questOffset + g.alongMeters)?.let { q -> onQuestTriggered(contextualize(q, pos), pos); publishPlan() }
        }
        if (!curvy && _state.value.quest == null) checkRiddles(g.alongMeters, pos)
        arbiter.poll(System.currentTimeMillis(), tts?.isSpeaking == true || kiloClips.isPlaying)?.let(::speakQuest)
        val now = System.currentTimeMillis()
        if (g.isOffRoute && now - lastReroute > 15_000) {
            lastReroute = now
            speak(app.getString(R.string.rerouting))
            calculateRoute()
        }
    }

    // ---------------------------------------------------------------- Sprachantworten (nur auf dem Geraet)

    fun setVoiceMode(on: Boolean) {
        val allowed = on && VoiceAnswers.isAvailable(app)
        prefs.edit().putBoolean("voice_mode", allowed).apply()
        _state.update { it.copy(voiceMode = allowed, voiceAvailable = VoiceAnswers.isAvailable(app)) }
        if (!allowed) { voiceJob?.cancel(); voice.stop(); _state.update { it.copy(listening = false) } }
    }

    private fun hasMicPermission() = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun onQuestForVoice(q: Quest?) {
        voiceJob?.cancel()
        voice.stop()
        _state.update { if (it.listening) it.copy(listening = false) else it }
        if (q == null || !_state.value.voiceMode || !_state.value.navigating || !hasMicPermission()) return
        if (q.textKey == RouteFacts.PENDING_KEY || q.textKey == WikiStories.PENDING_KEY || q.textKey == NatureSpotting.PENDING_KEY ||
            q.textKey == PlaneSpotting.PENDING_KEY || q.textKey == LanguageCourses.PENDING_KEY
        ) return // Platzhalter: erst zuhoeren, wenn die konkrete Aufgabe da ist (Quest-Objekt wechselt dann erneut)
        voiceJob = scope.launch {
            // erst zuhoeren, wenn die Ansage fertig ist (sonst hoert die App sich selbst)
            delay(1500)
            var waited = 0
            while (tts?.isSpeaking == true && waited < 40) { delay(500); waited++ }
            delay(500)
            repeat(VOICE_ATTEMPTS) {
                if (_state.value.quest !== q) return@launch
                val heard = listenOnce()
                if (_state.value.quest !== q) return@launch
                if (handleSpoken(q, heard)) return@launch
                delay(800)
            }
        }
    }

    private suspend fun listenOnce(): List<String> = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        _state.update { it.copy(listening = true) }
        cont.invokeOnCancellation { voice.stop() }
        voice.listen { heard ->
            _state.update { it.copy(listening = false) }
            if (cont.isActive) cont.resume(heard, null)
        }
    }

    /** Gibt true zurueck, wenn die Antwort erkannt und abgegeben wurde. */
    private fun handleSpoken(q: Quest, heard: List<String>): Boolean {
        if (heard.isEmpty()) return false
        if (q.kind == QuestKind.CHOICE) {
            val options = q.optionKeys.map { app.stringByKey(it) }
            val ordinals = app.getString(R.string.voice_ordinals).split(';').map { g -> g.split(',').map { it.trim() } }
            val idx = AnswerMatcher.matchOption(heard, options, ordinals) ?: return false
            answerQuest(idx)
            return true
        }
        val done = app.getString(R.string.voice_done_words).split(',').map { it.trim() }
        if (!AnswerMatcher.isDone(heard, done)) return false
        answerQuest()
        return true
    }

    // ---------------------------------------------------------------- Eigene Raetsel (Eltern)

    private fun loadRiddles(): List<Riddle> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("riddles", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val opts = o.getJSONArray("opts")
            Riddle(
                id = o.getString("id"), question = o.getString("q"),
                options = (0 until opts.length()).map { opts.getString(it) },
                correctIndex = o.getInt("c"),
                atKm = if (o.has("km")) o.getDouble("km") else null,
                near = if (o.has("lat")) LatLon(o.getDouble("lat"), o.getDouble("lon")) else null,
                nearName = o.optString("place").takeIf { it.isNotBlank() },
            )
        }
    }.getOrDefault(emptyList())

    private fun saveRiddles(list: List<Riddle>) {
        val arr = org.json.JSONArray()
        list.forEach { r ->
            val o = org.json.JSONObject().put("id", r.id).put("q", r.question).put("opts", org.json.JSONArray(r.options)).put("c", r.correctIndex)
            r.atKm?.let { o.put("km", it) }
            r.near?.let { o.put("lat", it.lat).put("lon", it.lon) }
            r.nearName?.let { o.put("place", it) }
            arr.put(o)
        }
        prefs.edit().putString("riddles", arr.toString()).apply()
    }

    fun showRiddles(show: Boolean) = _state.update { it.copy(riddleScreen = show) }

    /** Speichert ein gueltiges Raetsel; gibt die Fehlerschluessel zurueck (leer = gespeichert). */
    fun addRiddle(r: Riddle): List<String> {
        val errors = RiddleRules.validate(r)
        if (errors.isEmpty()) {
            val list = _state.value.riddles + r
            saveRiddles(list)
            _state.update { it.copy(riddles = list, results = emptyList()) }
        }
        return errors
    }

    fun deleteRiddle(id: String) {
        val list = _state.value.riddles.filterNot { it.id == id }
        saveRiddles(list)
        _state.update { it.copy(riddles = list) }
    }

    /** Faelliges Eltern-Raetsel als Quest zeigen und ansagen (hoechstens eines zugleich). */
    private fun checkRiddles(along: Double, pos: LatLon) {
        val r = _state.value.riddles.firstOrNull { RiddleRules.due(it, along, pos, usedRiddles) } ?: return
        usedRiddles += r.id
        val q = Quest(
            templateId = "riddle:${r.id}", textKey = "=${r.question}", type = QuestType.QUIZ,
            triggerMeters = questOffset + along, kind = QuestKind.CHOICE,
            optionKeys = r.options.map { "=$it" }, correctIndex = r.correctIndex, points = 20,
        )
        adventure?.forceQuest(q)
        _state.update { it.copy(quest = q, feedback = null, newSticker = null) }
        announceQuest(q)
    }

    // ---------------------------------------------------------------- Himmel & Flugzeuge

    /**
     * Nachts: Polarlicht-Chance (NOAA Kp, nur noerdlich von 55 Grad) oder Mondphase; tagsueber: Flugzeug in der Naehe
     * (adsb.lol). Ohne Daten gibt es die Rueckfall-Aufgabe.
     */
    private suspend fun skyQuest(q: Quest, pos: LatLon): Quest {
        val nowSec = System.currentTimeMillis() / 1000
        val dark = Sun.isDark(pos.lat, pos.lon, nowSec)
        if (dark) {
            if (pos.lat >= 55.0) {
                val kp = runCatching { Aurora.latestKp(fetchUrl("https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json")) }.getOrNull()
                if (Aurora.chance(pos.lat, kp, true) != AuroraChance.NONE && kp != null) {
                    return q.copy(type = QuestType.SKY, textKey = PlaneSpotting.AURORA_KEY, args = listOf(String.format(Locale.ROOT, "%.1f", kp)))
                }
            }
            val moon = app.stringByKey("moon_" + Moon.phase(nowSec).name.lowercase())
            return q.copy(type = QuestType.SKY, textKey = PlaneSpotting.MOON_KEY, args = listOf(moon))
        }
        val planes = runCatching {
            PlaneSpotting.parse(fetchUrl(PlaneSpotting.buildUrl(pos.lat, pos.lon)), pos.lat, pos.lon)
        }.getOrDefault(emptyList())
        val plane = PlaneSpotting.pick(planes, Random.Default) ?: return RouteFacts.resolve(q, null)
        val direction = app.stringByKey("dir_" + PlaneSpotting.compass(plane.bearingDegrees).name.lowercase())
        val km = String.format(Locale.getDefault(), "%.1f", plane.altitudeMeters / 1000.0)
        return q.copy(type = QuestType.SKY, textKey = PlaneSpotting.PLANE_KEY, args = listOf(PlaneSpotting.typeName(plane.typeCode), direction, km))
    }

    // ---------------------------------------------------------------- Laender-Mini-Sprachkurs

    private fun languageName(course: LanguageCourse): String =
        Locale(course.language).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }

    /** Quizfrage "Wie sagt man X auf Schwedisch?" mit drei Woertern als Antwort. */
    private fun languageQuest(base: Quest, course: LanguageCourse, taught: Set<Phrase>): Quest {
        val quiz = LanguageCourses.quiz(course, taught, Random.Default)
        taughtPhrases += quiz.phrase
        return base.copy(
            type = QuestType.LANGUAGE,
            kind = QuestKind.CHOICE,
            textKey = LanguageCourses.QUESTION_KEY,
            args = listOf(app.stringByKey(quiz.phrase.textKey), languageName(course)),
            optionKeys = quiz.options.map { "=$it" },
            correctIndex = quiz.correctIndex,
            points = 15,
        )
    }

    /** Alle ca. 10 km das Land pruefen (Photon-Reverse); bei Wechsel in ein Land mit Kurs: Willkommen + Quiz. */
    private fun checkCountry(pos: LatLon, along: Double) {
        // Vorab ermittelt (Reise-Paket): ohne Netz
        countryTimeline?.countryAt(along)?.let { code ->
            val previous = _state.value.country
            if (code != previous) {
                _state.update { it.copy(country = code) }
                noteCountry(code)
                if (previous != null) onCountryChanged(code)
            }
            return
        }
        if (countryChecking || along - lastCountryCheckAlong < COUNTRY_CHECK_METERS) return
        countryChecking = true
        lastCountryCheckAlong = along
        scope.launch {
            try {
                val code = fetchCountryCode(pos.lat, pos.lon) ?: return@launch
                val previous = _state.value.country
                if (code == previous) return@launch
                _state.update { it.copy(country = code) }
                noteCountry(code)
                if (previous != null) onCountryChanged(code)
            } finally {
                countryChecking = false
            }
        }
    }

    private fun onCountryChanged(code: String) {
        val course = LanguageCourses.forCountry(code) ?: return
        val words = LanguageCourses.welcomePhrases
        val countryName = Locale("", code).getDisplayCountry(Locale.getDefault())
        val intro = app.getString(
            R.string.lang_welcome, countryName, languageName(course),
            app.stringByKey(words[0].textKey), course.words.getValue(words[0]),
            app.stringByKey(words[1].textKey), course.words.getValue(words[1]),
        )
        // Willkommen sagen, danach die Woerter in der Landessprache
        speakQuest(intro, utteranceId = "kq-lang-intro")
        foreignQueue.clear()
        words.forEach { foreignQueue.addLast(course.words.getValue(it)) }
        foreignLocale = Locale(course.language)
        taughtPhrases.addAll(words)
        // Quiz zu den eben gelernten Woertern
        val base = Quest("lang_welcome", LanguageCourses.PENDING_KEY, QuestType.LANGUAGE, questOffset + (_state.value.guidance?.alongMeters ?: 0.0))
        val q = languageQuest(base, course, Phrase.entries.toSet() - words.toSet())
        adventure?.forceQuest(q)
        _state.update { it.copy(quest = q, feedback = null, newSticker = null) }
    }

    private fun onSpeechDone(id: String?) {
        if (id == null || !id.startsWith("kq-lang")) return
        val next = foreignQueue.removeFirstOrNull()
        if (next == null) { if (foreignActive) endForeign(); return }
        val loc = foreignLocale ?: return
        val engine = tts ?: return
        if (!foreignActive) {
            if (engine.isLanguageAvailable(loc) < TextToSpeech.LANG_AVAILABLE) { foreignQueue.clear(); return }
            foreignActive = true
            engine.language = loc
        }
        engine.speak(next, TextToSpeech.QUEUE_ADD, null, "kq-lang-word-${System.nanoTime()}")
    }

    private fun endForeign() {
        foreignActive = false
        foreignQueue.clear()
        tts?.language = Locale.getDefault()
    }

    // ---------------------------------------------------------------- Mehrtagesreise

    private fun loadTrip(): TripState? = runCatching {
        val o = org.json.JSONObject(prefs.getString("trip", null) ?: return null)
        val arr = o.getJSONArray("stops")
        val stops = (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            Place(p.getString("n"), LatLon(p.getDouble("lat"), p.getDouble("lon")))
        }
        val pts = o.optJSONArray("points")
        val origin = if (o.has("olat")) LatLon(o.getDouble("olat"), o.getDouble("olon")) else null
        TripState(
            origin = origin, stops = stops,
            currentStage = o.optInt("stage", 0).coerceIn(0, stops.size),
            stagePoints = (0 until (pts?.length() ?: 0)).map { pts!!.getInt(it) },
        )
    }.getOrNull()

    private fun saveTrip(t: TripState?) {
        if (t == null || t.stops.isEmpty()) { prefs.edit().remove("trip").apply(); return }
        val o = org.json.JSONObject()
        o.put("stops", org.json.JSONArray().also { a ->
            t.stops.forEach { a.put(org.json.JSONObject().put("n", it.name).put("lat", it.position.lat).put("lon", it.position.lon)) }
        })
        o.put("stage", t.currentStage)
        o.put("points", org.json.JSONArray(t.stagePoints))
        t.origin?.let { o.put("olat", it.lat).put("olon", it.lon) }
        prefs.edit().putString("trip", o.toString()).apply()
    }

    fun showTrip(show: Boolean) = _state.update { it.copy(tripScreen = show) }

    fun addTripStop(place: Place) {
        val cur = _state.value.trip ?: TripState(origin = _state.value.position, stops = emptyList())
        if (cur.origin == null && _state.value.position == null) {
            _state.update { it.copy(error = R.string.trip_no_position) }
            return
        }
        val updated = cur.copy(origin = cur.origin ?: _state.value.position, stops = cur.stops + place)
        _state.update { it.copy(trip = updated, results = emptyList()) }
        saveTrip(updated)
        planTrip()
    }

    fun removeTripStop(index: Int) {
        val cur = _state.value.trip ?: return
        if (index < cur.currentStage || index !in cur.stops.indices) return // erledigte Etappen bleiben
        val updated = cur.copy(stops = cur.stops.filterIndexed { i, _ -> i != index })
        _state.update { it.copy(trip = updated) }
        saveTrip(updated)
        planTrip()
    }

    // --- Favoriten und gebuchte Unterkuenfte (lokal gespeichert)

    private fun loadPlaces(key: String): List<Place> = runCatching {
        val a = org.json.JSONArray(prefs.getString(key, null) ?: return emptyList())
        (0 until a.length()).map { i ->
            val p = a.getJSONObject(i)
            Place(p.getString("n"), LatLon(p.getDouble("lat"), p.getDouble("lon")))
        }
    }.getOrDefault(emptyList())

    private fun savePlaces(key: String, places: List<Place>) {
        val a = org.json.JSONArray()
        places.forEach { a.put(org.json.JSONObject().put("n", it.name).put("lat", it.position.lat).put("lon", it.position.lon)) }
        prefs.edit().putString(key, a.toString()).apply()
    }

    private fun sameSpot(a: Place, b: Place) = a.name == b.name && haversine(a.position, b.position) < 100.0

    fun addFavorite(place: Place) {
        val cur = _state.value.favorites
        if (cur.any { sameSpot(it, place) }) return
        val next = (cur + place).takeLast(60)
        savePlaces("favorites", next)
        _state.update { it.copy(favorites = next) }
    }

    fun removeFavorite(place: Place) {
        val next = _state.value.favorites.filterNot { sameSpot(it, place) }
        savePlaces("favorites", next)
        _state.update { it.copy(favorites = next) }
    }

    fun addBooked(place: Place) {
        val cur = _state.value.booked
        if (cur.any { sameSpot(it, place) }) return
        val next = cur + place
        savePlaces("booked", next)
        _state.update { it.copy(booked = next, results = emptyList()) }
    }

    fun removeBooked(place: Place) {
        val next = _state.value.booked.filterNot { sameSpot(it, place) }
        savePlaces("booked", next)
        _state.update { it.copy(booked = next) }
    }

    /**
     * Teilt die aktuelle Route nach der Tagesfahrzeit in Etappen. Gebuchte Unterkuenfte sind feste Tagesenden;
     * andere Uebernachtungsorte werden per Reverse-Geocoding benannt.
     */
    fun splitRouteIntoDays() {
        val s = _state.value
        val route = s.route ?: return
        val dest = s.destination ?: return
        val origin = s.position ?: return
        val days = BookedPlanner.plan(route, s.booked.map { BookedStay(it.name, it.position) }, s.settings.dailyDriveHours * 3600.0)
        if (days.size <= 1) {
            _state.update { it.copy(error = R.string.trip_split_one_day) }
            return
        }
        scope.launch {
            val lang = Locale.getDefault().language
            val notes = ArrayList<String>()
            val stops = days.map { d ->
                when (d.end) {
                    DayEnd.FINAL -> dest
                    DayEnd.BOOKED -> {
                        if (d.offRouteMeters > BookedPlanner.FAR_OFF_ROUTE_METERS) {
                            notes += app.getString(R.string.trip_note_far, d.name.orEmpty(), (d.offRouteMeters / 1000).toInt())
                        }
                        Place(d.name.orEmpty(), d.point)
                    }
                    DayEnd.COMPUTED -> {
                        val n = fetchPlaceName(d.point.lat, d.point.lon, lang) ?: app.getString(R.string.trip_day_label, d.day)
                        Place(app.getString(R.string.trip_overnight_near, n), d.point)
                    }
                }
            }
            days.filter { it.overBudget }.forEach { notes += app.getString(R.string.trip_note_long, it.day) }
            val updated = TripState(origin = origin, stops = stops)
            _state.update { it.copy(trip = updated, error = null, tripNotes = notes) }
            saveTrip(updated)
            planTrip()
        }
    }

    // --- Unterkunftssuche

    /** Sucht Unterkuenfte um das Ende von Etappe [index] (Overpass), zuerst 8 km, bei leerem Ergebnis 20 km. */
    fun findLodging(index: Int) {
        val t = _state.value.trip ?: return
        val stop = t.stops.getOrNull(index) ?: return
        _state.update { it.copy(lodging = LodgingUi(index)) }
        scope.launch {
            val found = runCatching {
                withContext(Dispatchers.IO) {
                    var r = LodgingFinder.parse(fetchOverpass(LodgingFinder.buildQuery(stop.position, 8000, includeCamping = true)))
                    if (r.isEmpty()) r = LodgingFinder.parse(fetchOverpass(LodgingFinder.buildQuery(stop.position, 20000, includeCamping = true)))
                    r
                }
            }
            val ui = found.fold(
                { LodgingUi(index, loading = false, options = LodgingFinder.rank(it, stop.position), failed = false) },
                { LodgingUi(index, loading = false, failed = true) },
            )
            if (_state.value.lodging?.stopIndex == index) _state.update { it.copy(lodging = ui) }
        }
    }

    // --- Freihaendige Bedienung: Gemini/Assistant-Intents und Sprachbefehle (Spec 008)

    /** Einmalige Routenwuensche aus einem Intent (z. B. avoid=t); gelten bis zur naechsten manuellen Zielwahl. */
    private var oneOffAvoid: Set<RouteAvoid> = emptySet()

    private fun routeAvoid(): Set<RouteAvoid> {
        val s = _state.value.settings
        return buildSet {
            if (s.avoidTolls) add(RouteAvoid.TOLLS)
            if (s.avoidHighways) add(RouteAvoid.HIGHWAYS)
            if (s.avoidFerries) add(RouteAvoid.FERRIES)
            addAll(oneOffAvoid)
        }
    }

    /** Sprachausgabe fuer Rueckmeldungen auf Sprachbefehle und Intents (nur Audio; Navigationsstimme, folgt deren Einstellung). */
    private fun say(text: String) = speak(text)

    /** Einstiegspunkt fuer Intents von Gemini/Assistant (`geo:`, `geo.action:`, `google.navigation:`) und `kiloquest://cmd/...`. */
    fun handleIntent(action: String?, uri: String?) {
        val intent = NavIntents.parse(action, uri) ?: return
        when (intent) {
            is NavIntent.Navigate -> navigateByIntent(intent)
            is NavIntent.Search -> {
                search(intent.query)
                say(app.getString(R.string.intent_search_done))
            }
            is NavIntent.Action -> customAction(intent)
            is NavIntent.Command -> runNamedCommand(intent.name)
        }
    }

    private fun navigateByIntent(i: NavIntent.Navigate) {
        // Google verlangt, dass nicht unterstuetzte Aktionen dem Nutzer gemeldet werden
        if (i.kind == "add_a_stop") { say(app.getString(R.string.intent_unsupported_stop)); return }
        oneOffAvoid = i.avoid
        scope.launch {
            val place = i.position?.let { Place(i.query ?: app.getString(R.string.intent_destination_unnamed), it) }
                ?: i.query?.let { q -> runCatching { searchPlaces(q, Locale.getDefault().language).firstOrNull() }.getOrNull() }
            if (place == null) { say(app.getString(R.string.intent_not_found)); return@launch }
            _state.update { it.copy(destination = place, results = emptyList(), error = null) }
            if (i.kind == "navigation") autoStartAfterRoute = true
            calculateRoute()
            say(app.getString(if (i.kind == "navigation") R.string.intent_navigating_to else R.string.intent_route_to, place.name))
        }
    }

    private fun customAction(a: NavIntent.Action) {
        when (a.act) {
            "mute" -> { say(app.getString(R.string.cmd_muted)); updateSettings { it.copy(naviVoice = false) } }
            "unmute" -> { updateSettings { it.copy(naviVoice = true) }; say(app.getString(R.string.cmd_unmuted)) }
            "exit_navigation" -> { say(app.getString(R.string.cmd_navigation_ended)); stopNavigation() }
            "eta", "time_to_destination", "distance_to_destination" -> sayRemaining()
            "distance_to_next_turn", "time_to_next_turn", "query_next_turn" -> sayNextTurn()
            "query_destination" -> sayDestination()
            "query_current_road" -> sayCurrentRoad()
            "avoid_tolls" -> setAvoid(tolls = true)
            "allow_tolls" -> setAvoid(tolls = false)
            "avoid_highways" -> setAvoid(highways = true)
            "allow_highways" -> setAvoid(highways = false)
            "avoid_ferries" -> setAvoid(ferries = true)
            "allow_ferries" -> setAvoid(ferries = false)
            else -> say(app.getString(R.string.intent_unsupported))
        }
    }

    private fun setAvoid(tolls: Boolean? = null, highways: Boolean? = null, ferries: Boolean? = null) {
        updateSettings { it.copy(avoidTolls = tolls ?: it.avoidTolls, avoidHighways = highways ?: it.avoidHighways, avoidFerries = ferries ?: it.avoidFerries) }
        val text = when {
            tolls == true -> R.string.cmd_avoid_tolls
            tolls == false -> R.string.cmd_allow_tolls
            highways == true -> R.string.cmd_avoid_highways
            highways == false -> R.string.cmd_allow_highways
            ferries == true -> R.string.cmd_avoid_ferries
            else -> R.string.cmd_allow_ferries
        }
        say(app.getString(text))
        // Laufende oder gezeigte Route mit den neuen Wuenschen neu berechnen
        if (_state.value.destination != null && _state.value.route != null) calculateRoute()
    }

    private fun sayRemaining() {
        val s = _state.value
        val route = s.route
        val g = s.guidance
        if (route == null || g == null) { say(app.getString(R.string.cmd_no_trip)); return }
        val seconds = if (route.lengthMeters > 0) route.durationSeconds * (g.remainingMeters / route.lengthMeters) else 0.0
        val minutes = (seconds / 60).toInt().coerceAtLeast(0)
        val duration = if (minutes >= 60) app.getString(R.string.unit_h_min, minutes / 60, minutes % 60) else app.getString(R.string.unit_min, minutes)
        val clock = java.time.LocalTime.now().plusSeconds(seconds.toLong()).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
        say(app.getString(R.string.cmd_eta, duration, clock, distanceText(g.remainingMeters)))
    }

    private fun sayNextTurn() {
        val s = _state.value
        val g = s.guidance
        val m = g?.let { s.route?.maneuvers?.getOrNull(it.nextManeuverIndex) }
        if (g == null || m == null) { say(app.getString(R.string.cmd_no_trip)); return }
        say(app.getString(R.string.cmd_next_turn, m.instruction, distanceText(g.metersToNextManeuver)))
    }

    private fun sayDestination() {
        val name = _state.value.destination?.name
        say(if (name.isNullOrBlank()) app.getString(R.string.cmd_no_trip) else app.getString(R.string.cmd_destination, name))
    }

    private fun sayCurrentRoad() {
        val s = _state.value
        val idx = (s.guidance?.nextManeuverIndex ?: 0) - 1
        val road = s.route?.maneuvers?.getOrNull(idx.coerceAtLeast(0))?.streetNames?.firstOrNull()
        say(if (road.isNullOrBlank()) app.getString(R.string.cmd_unknown_road) else app.getString(R.string.cmd_current_road, road))
    }

    private fun sayLimit() {
        val limit = _state.value.speedLimit
        say(if (limit == null || limit >= SpeedProfile.UNLIMITED) app.getString(R.string.cmd_no_limit) else app.getString(R.string.cmd_limit, limit))
    }

    private fun runNamedCommand(name: String) {
        val cmd = when (name) {
            "toilet" -> VoiceCommand.TOILET
            "break" -> VoiceCommand.BREAK
            "story" -> VoiceCommand.STORY
            "mute" -> VoiceCommand.MUTE
            "unmute" -> VoiceCommand.UNMUTE
            "eta" -> VoiceCommand.ETA
            else -> null
        }
        if (cmd == null) say(app.getString(R.string.intent_unsupported)) else executeCommand(cmd)
    }

    /** Fuehrt einen erkannten Sprachbefehl aus; die Antwort kommt als Sprachausgabe (der Fahrer muss nichts bedienen). */
    fun executeCommand(cmd: VoiceCommand) {
        when (cmd) {
            VoiceCommand.TOILET -> toiletAlarm()
            VoiceCommand.BREAK -> { say(app.getString(R.string.cmd_break)); suggestBreakNow() }
            VoiceCommand.STORY -> tellStory()
            VoiceCommand.MUTE -> customAction(NavIntent.Action("mute", emptyMap()))
            VoiceCommand.UNMUTE -> customAction(NavIntent.Action("unmute", emptyMap()))
            VoiceCommand.ETA -> sayRemaining()
            VoiceCommand.NEXT_TURN -> sayNextTurn()
            VoiceCommand.SPEED_LIMIT -> sayLimit()
            VoiceCommand.DESTINATION -> sayDestination()
            VoiceCommand.STOP_NAVIGATION -> customAction(NavIntent.Action("exit_navigation", emptyMap()))
            VoiceCommand.HELP -> say(app.getString(R.string.cmd_help))
        }
    }

    private var commandJob: Job? = null

    /** Schaltet die Freisprech-Erkennung ein/aus (braucht On-Device-Erkennung und Mikrofon-Erlaubnis). Gibt zurueck, ob sie aktiv ist. */
    fun setVoiceCommands(on: Boolean): Boolean {
        val allowed = on && VoiceAnswers.isAvailable(app) && hasMicPermission()
        updateSettings { it.copy(voiceCommands = allowed) }
        if (allowed && _state.value.navigating) startCommandListening() else stopCommandListening()
        return allowed
    }

    /** Einmal zuhoeren (Knopf im Beifahrer-Menue): Befehl ohne Anrede. */
    fun listenForCommand() {
        if (!VoiceAnswers.isAvailable(app) || !hasMicPermission()) { say(app.getString(R.string.cmd_voice_unavailable)); return }
        scope.launch {
            val heard = listenOnce()
            val cmd = VoiceCommands.parseAny(heard, requireWake = false)
            if (cmd != null) executeCommand(cmd) else say(app.getString(R.string.cmd_unknown))
        }
    }

    /** Dauerhaft zuhoeren, solange navigiert wird: nur Befehle mit "Kilo ..." (nicht beim Sprechen der App, nicht waehrend einer Quiz-Antwort). */
    private fun startCommandListening() {
        if (commandJob?.isActive == true) return
        commandJob = scope.launch {
            var quiet = 0
            while (isActive && _state.value.navigating && _state.value.settings.voiceCommands) {
                if (tts?.isSpeaking == true || kiloClips.isPlaying || (_state.value.voiceMode && _state.value.quest != null)) { delay(1000); continue }
                val heard = listenOnce()
                val cmd = VoiceCommands.parseAny(heard, requireWake = true)
                if (cmd != null) { quiet = 0; executeCommand(cmd); delay(1500); continue }
                // Bei Stille/Fehlern laenger warten (Akku, Piepton mancher Geraete)
                quiet = if (heard.isEmpty()) quiet + 1 else 0
                delay(if (quiet >= 3) 4000 else 600)
            }
        }
    }

    private fun stopCommandListening() {
        commandJob?.cancel(); commandJob = null
        voice.stop()
        _state.update { if (it.listening) it.copy(listening = false) else it }
    }

    // --- Roadtrip-Fotostory

    /** Beifahrer hat auf eine Foto-Bitte geantwortet: bei "Spaeter" gibt es eine Pause. */
    fun photoAnswered(taken: Boolean) = photoPrompter.onAnswered(System.currentTimeMillis(), taken)

    /** Tagebuch-Eintrag fuer ein Foto-Paar (Dateinamen "rueck|front"); [place] = Ort oder leer. */
    fun logPhoto(files: List<String>, place: String?, pos: LatLon?) {
        if (files.isEmpty()) return
        logDiary(DiaryKind.PHOTO, place.orEmpty(), files.joinToString("|"), pos = pos)
    }

    private fun maybePromptPhoto(g: GuidanceState) {
        val s = _state.value
        if (!s.settings.photoPrompts) return
        val audience = photoAudience ?: return
        val now = System.currentTimeMillis()
        if (now - lastPhotoTick < 5_000) return
        lastPhotoTick = now
        if (!audience.available()) return
        // Nie ablenken: nicht bei Abbiegung, Umleitung, Pause, Klo-Alarm oder Abweichung von der Route
        val busy = s.toilet != null || s.proposal != null || s.breakProposal != null || s.breakTarget != null ||
            g.metersToNextManeuver < 1_000.0 || g.isOffRoute
        val sc = if (now - scenicAt < 5 * 60_000L) scenic else Scenic.NONE
        val prompt = photoPrompter.onTick(now, java.time.LocalDate.now().toEpochDay().toInt(), sc.score, sc.name, busy) ?: return
        audience.send(prompt)
    }

    /** Alle 3 Minuten waehrend der Fahrt: Ist die Gegend schoen (Aussichtspunkt, Berg, See ...)? Nur mit Beifahrer-Geraet und Netz. */
    private fun startScenicJob() {
        scenicJob?.cancel()
        scenicJob = scope.launch {
            while (isActive) {
                val pos = _state.value.position
                if (pos != null && _state.value.settings.photoPrompts && photoAudience?.available() == true) {
                    runCatching { ScenicFinder.assess(withContext(Dispatchers.IO) { fetchOverpass(ScenicFinder.buildQuery(pos.lat, pos.lon)) }) }
                        .onSuccess { scenic = it; scenicAt = System.currentTimeMillis() }
                }
                delay(180_000)
            }
        }
    }

    // --- Reisetagebuch und Hoergeschichten

    private fun logDiary(
        kind: DiaryKind, title: String, text: String = "", country: String? = null, points: Int = 0, meters: Double = 0.0,
        pos: LatLon? = _state.value.position,
    ) {
        val now = System.currentTimeMillis()
        val e = DiaryEntry(
            java.util.UUID.randomUUID().toString(), now, java.time.LocalDate.now().toEpochDay().toInt(), kind.name,
            title.take(200), text.take(1500), country, points, meters, pos?.lat, pos?.lon,
        )
        val next = TravelDiary.add(_state.value.diary, e)
        if (next === _state.value.diary) return
        prefs.edit().putString("diary", TravelDiary.encode(next)).apply()
        _state.update { it.copy(diary = next) }
    }

    fun showDiary(show: Boolean) = _state.update { it.copy(diaryScreen = show) }

    fun clearDiary() {
        PhotoStore.deleteAll(app)
        prefs.edit().remove("diary").apply()
        _state.update { it.copy(diary = emptyList()) }
    }

    /** Einen Tagebuch-Eintrag mit Text (Geschichte) noch einmal vorlesen. */
    fun speakDiaryEntry(e: DiaryEntry) {
        if (e.text.isBlank()) return
        kiloClips.stop()
        tts?.speak(e.title + ". " + e.text, TextToSpeech.QUEUE_FLUSH, null, "kq-diary-${System.nanoTime()}")
    }

    private fun noteCountry(code: String) {
        if (!_state.value.navigating) return
        logDiary(DiaryKind.COUNTRY, Locale("", code).getDisplayCountry(Locale.getDefault()), country = code)
    }

    /** Hoergeschichte auf Wunsch: eine laengere Wikipedia-Geschichte zu einem Ort in der Naehe, vorgelesen und im Tagebuch gemerkt. */
    override fun tellStory() {
        val pos = _state.value.position
        if (pos == null) { _state.update { it.copy(storyCard = StoryCard(failed = true)) }; return }
        _state.update { it.copy(storyCard = StoryCard(loading = true)) }
        scope.launch {
            val lang = Locale.getDefault().language
            var story: Story? = null
            for (l in listOf(lang, "en").distinct()) {
                val list = runCatching { WikiStories.parseListen(fetchUrl(WikiStories.buildListenUrl(l, pos.lat, pos.lon))) }.getOrDefault(emptyList())
                story = pickFresh(list, { "story:" + it.title }) { WikiStories.pick(it, Random.Default) }
                if (story != null) break
            }
            if (story == null) {
                _state.update { it.copy(storyCard = StoryCard(failed = true)) }
                return@launch
            }
            _state.update { it.copy(storyCard = StoryCard(title = story.title, text = story.text)) }
            logDiary(DiaryKind.STORY, story.title, story.text, pos = pos)
            speakQuest(story.title + ". " + story.text)
        }
    }

    override fun dismissStory() {
        tts?.stop()
        _state.update { it.copy(storyCard = null) }
    }

    // --- Fest geplante Reisen

    private fun savePlans(list: List<PlannedTrip>) {
        prefs.edit().putString("plans", PlanStore.encode(list)).apply()
        _state.update { it.copy(plans = list) }
    }

    fun showPlans(show: Boolean) = _state.update { it.copy(plansScreen = show, planOpenId = if (show) it.planOpenId else null, planResult = null, results = emptyList()) }

    fun openPlan(id: String?) = _state.update { it.copy(planOpenId = id, planResult = null, results = emptyList()) }

    fun newPlan() {
        val today = java.time.LocalDate.now().toEpochDay().toInt()
        val p = PlannedTrip(java.util.UUID.randomUUID().toString(), "", today + 60)
        savePlans(_state.value.plans + p)
        openPlan(p.id)
    }

    fun deletePlan(id: String) {
        savePlans(_state.value.plans.filterNot { it.id == id })
        openPlan(null)
    }

    /** Aendert die gerade geoeffnete Reise; ein altes Ergebnis wird verworfen, weil es nicht mehr passt. */
    fun updatePlan(transform: (PlannedTrip) -> PlannedTrip) {
        val id = _state.value.planOpenId ?: return
        savePlans(_state.value.plans.map { if (it.id == id) transform(it) else it })
        _state.update { it.copy(planResult = null) }
    }

    /** Wie [updatePlan], aber das berechnete Ergebnis bleibt (z. B. beim Abhaken der Packliste). */
    private fun updatePlanKeepingResult(transform: (PlannedTrip) -> PlannedTrip) {
        val id = _state.value.planOpenId ?: return
        savePlans(_state.value.plans.map { if (it.id == id) transform(it) else it })
    }

    fun togglePacked(item: PackItem) = updatePlanKeepingResult { p ->
        p.copy(packed = if (item.name in p.packed) p.packed - item.name else p.packed + item.name)
    }

    fun setPlanCamping(on: Boolean) = updatePlanKeepingResult { it.copy(camping = on) }

    /** Rueckreise anlegen und oeffnen: Start/Ziel getauscht, Abreise eine Woche nach der berechneten Ankunft. */
    fun createReturnTrip(namePrefix: String) {
        val s = _state.value
        val t = s.plans.firstOrNull { it.id == s.planOpenId } ?: return
        val start = (s.planResult?.itinerary?.arrivalDay ?: (t.startDay + 7)) + 7
        val r = TripOps.returnTrip(t, java.util.UUID.randomUUID().toString(), start, namePrefix)
        savePlans(s.plans + r)
        openPlan(r.id)
    }

    /** Kopie der Reise ein Jahr (364 Tage, gleicher Wochentag) spaeter. */
    fun duplicatePlan(nameSuffix: String) {
        val s = _state.value
        val t = s.plans.firstOrNull { it.id == s.planOpenId } ?: return
        val c = TripOps.duplicate(t, java.util.UUID.randomUUID().toString(), t.startDay + 364, nameSuffix)
        savePlans(s.plans + c)
        openPlan(c.id)
    }

    fun setPlanPlace(place: Place, asOrigin: Boolean) = updatePlan {
        val pp = PlanPlace(place.name, place.position.lat, place.position.lon)
        if (asOrigin) it.copy(origin = pp) else it.copy(destination = pp)
    }

    fun setPlanOriginHere() {
        val pos = _state.value.position ?: return
        updatePlan { it.copy(origin = PlanPlace(app.getString(R.string.plan_my_position), pos.lat, pos.lon)) }
    }

    fun addPlanStay(place: Place) = updatePlan { p ->
        val last = p.stays.maxByOrNull { it.checkInDay }
        val day = if (last != null) last.checkOutDay + 1 else p.startDay + 1
        p.copy(stays = p.stays + PlanStay(place.name, place.position.lat, place.position.lon, day, 1))
    }

    fun clearPlanStay(index: Int) = updatePlan { p -> p.copy(stays = p.stays.filterIndexed { i, _ -> i != index }) }

    fun changePlanStay(index: Int, transform: (PlanStay) -> PlanStay) =
        updatePlan { p -> p.copy(stays = p.stays.mapIndexed { i, s -> if (i == index) transform(s) else s }) }

    /** Plant die Reise: Route durch alle Buchungen, Tage und Naechte, Vorschlaege fuer fehlende Uebernachtungen, Umweg. */
    fun calculatePlan() {
        val t = _state.value.plans.firstOrNull { it.id == _state.value.planOpenId } ?: return
        val o = t.origin
        val d = t.destination
        if (o == null || d == null) {
            _state.update { it.copy(planResult = PlanResult(error = R.string.plan_need_places)) }
            return
        }
        if (BuildConfig.VALHALLA_URL.isBlank()) {
            _state.update { it.copy(planResult = PlanResult(error = R.string.error_no_server)) }
            return
        }
        scope.launch {
            _state.update { it.copy(planResult = PlanResult(loading = true)) }
            val result = runCatching {
                val lang = Locale.getDefault().toLanguageTag()
                val sorted = t.stays.sortedWith(compareBy({ it.checkInDay }, { it.name }))
                val via = ValhallaApi.parseLegRoutes(fetchRoute(ValhallaApi.buildRequest(listOf(o.position) + sorted.map { it.position } + d.position, lang)))
                val direct = if (sorted.isNotEmpty()) ValhallaApi.parseRoute(fetchRoute(ValhallaApi.buildRouteRequest(o.position, d.position, lang))) else null
                val budget = _state.value.settings.dailyDriveHours * 3600.0
                val it = ItineraryPlanner.build(t.startDay, o.name, d.name, sorted, via.map { r -> LegSummary(r.lengthMeters, r.durationSeconds) }, budget)
                val nights = ArrayList<PlanNight>()
                val code = Locale.getDefault().language
                it.legs.forEach { lp ->
                    if (lp.roadNights.isNotEmpty()) {
                        val cuts = DayPlanner.split(via[lp.index], budget).filterNot { c -> c.isFinal }
                        lp.roadNights.forEachIndexed { k, day ->
                            cuts.getOrNull(k)?.let { c -> nights += PlanNight(lp.index, day, c.point, fetchPlaceName(c.point.lat, c.point.lon, code).orEmpty()) }
                        }
                    }
                }
                PlanResult(
                    itinerary = it,
                    nights = nights,
                    extraMinutes = direct?.let { r -> (ItineraryPlanner.extraSeconds(via.sumOf { v -> v.durationSeconds }, r.durationSeconds) / 60).toInt() },
                    legRoutes = via,
                    packMb = via.sumOf { v -> estimateMb(v) },
                    offRouteKm = direct?.let { r -> sorted.zip(ItineraryPlanner.offRouteMeters(r.line, sorted)).map { (s, m) -> s.name to (m / 1000).toInt() } }.orEmpty(),
                )
            }.getOrElse { PlanResult(error = R.string.error_route) }
            _state.update { it.copy(planResult = result) }
        }
    }

    /** Suche nach einer Unterkunft fuer eine noch unbelegte Nacht der geplanten Reise. */
    fun findPlanLodging(night: PlanNight) {
        _state.update { it.copy(lodging = LodgingUi(-1, planDay = night.day)) }
        scope.launch {
            val found = runCatching {
                withContext(Dispatchers.IO) {
                    var r = LodgingFinder.parse(fetchOverpass(LodgingFinder.buildQuery(night.point, 8000, includeCamping = true)))
                    if (r.isEmpty()) r = LodgingFinder.parse(fetchOverpass(LodgingFinder.buildQuery(night.point, 20000, includeCamping = true)))
                    r
                }
            }
            val ui = found.fold(
                { LodgingUi(-1, loading = false, options = LodgingFinder.rank(it, night.point), planDay = night.day) },
                { LodgingUi(-1, loading = false, failed = true, planDay = night.day) },
            )
            if (_state.value.lodging?.planDay == night.day) _state.update { it.copy(lodging = ui) }
        }
    }

    /** Uebernimmt die berechnete Reise als aktuelle Reise (Etappen: Uebernachtungsvorschlaege, Buchungen, Ziel). */
    fun usePlanAsTrip() {
        val s = _state.value
        val t = s.plans.firstOrNull { it.id == s.planOpenId } ?: return
        val res = s.planResult?.itinerary ?: return
        val o = t.origin ?: return
        val d = t.destination ?: return
        val sorted = t.stays.sortedWith(compareBy({ it.checkInDay }, { it.name }))
        val stops = ArrayList<Place>()
        res.legs.forEach { lp ->
            s.planResult.nights.filter { n -> n.legIndex == lp.index }.forEach { n ->
                stops += Place(if (n.name.isBlank()) app.getString(R.string.trip_day_label, stops.size + 1) else app.getString(R.string.trip_overnight_near, n.name), n.point)
            }
            val stay = sorted.getOrNull(lp.index)
            stops += if (stay != null) Place(stay.name, stay.position) else Place(d.name, d.position)
        }
        val trip = TripState(origin = o.position, stops = stops)
        _state.update { it.copy(trip = trip, plansScreen = false, tripScreen = true, planOpenId = null, planResult = null) }
        saveTrip(trip)
        planTrip()
    }

    fun closeLodging() = _state.update { it.copy(lodging = null) }

    /** Uebernimmt die gewaehlte Unterkunft als Etappenziel (Ort und Name ersetzt). */
    fun chooseLodging(option: LodgingOption) {
        val ui = _state.value.lodging ?: return
        if (ui.planDay != null) {
            _state.update { it.copy(lodging = null) }
            updatePlan { p -> p.copy(stays = p.stays + PlanStay(option.name, option.position.lat, option.position.lon, ui.planDay, 1)) }
            calculatePlan()
            return
        }
        val t = _state.value.trip ?: return
        if (ui.stopIndex < t.currentStage || ui.stopIndex !in t.stops.indices) return
        val updated = t.copy(stops = t.stops.mapIndexed { i, p -> if (i == ui.stopIndex) Place(option.name, option.position) else p })
        _state.update { it.copy(trip = updated, lodging = null) }
        saveTrip(updated)
        planTrip()
    }

    fun clearTrip() {
        saveTrip(null)
        _state.update { it.copy(trip = null, tripNotes = emptyList(), lodging = null) }
    }

    /** Etappenlaengen/-dauern der ganzen Reise (eine Valhalla-Anfrage). */
    private fun planTrip() {
        val t = _state.value.trip ?: return
        val start = (if (t.currentStage > 0) t.stops.getOrNull(t.currentStage - 1)?.position else null) ?: t.origin ?: return
        val remaining = t.stops.drop(t.currentStage)
        if (remaining.isEmpty() || BuildConfig.VALHALLA_URL.isBlank()) return
        scope.launch {
            _state.update { it.copy(trip = it.trip?.copy(loading = true)) }
            val legs = runCatching {
                TripApi.parseLegs(fetchRoute(TripApi.buildRequest(start, remaining.map { it.position }, Locale.getDefault().toLanguageTag())))
            }.getOrDefault(emptyList())
            _state.update { it.copy(trip = it.trip?.copy(legs = legs, loading = false)) }
        }
    }

    /** Startet Etappe [stage]: Route von der aktuellen Position zum Etappenziel, dann Navigation. */
    fun startStage(stage: Int) {
        val t = _state.value.trip ?: return
        val stop = t.stops.getOrNull(stage) ?: return
        autoStartAfterRoute = true
        _state.update { it.copy(destination = stop, results = emptyList(), tripScreen = false, kidsScreen = false) }
        calculateRoute()
    }

    /** Beim Ankommen am Etappenziel: Punkte der Etappe merken, naechste Etappe freigeben. Gibt den Ansagetext zurueck. */
    private fun onStageArrived(): String? {
        val s = _state.value
        val t = s.trip ?: return null
        val stop = t.stops.getOrNull(t.currentStage) ?: return null
        if (s.destination?.name != stop.name) return null
        val updated = t.copy(currentStage = t.currentStage + 1, stagePoints = t.stagePoints + s.points)
        _state.update { it.copy(trip = updated) }
        saveTrip(updated)
        if (updated.currentStage < updated.stops.size) planTrip()
        val done = app.getString(R.string.trip_stage_done_speech, t.currentStage + 1, s.points)
        return if (updated.currentStage >= updated.stops.size) done + " " + app.getString(R.string.trip_finished) else done
    }

    // ---------------------------------------------------------------- Wetter

    fun conditionName(c: WeatherCondition): String = app.getString(
        when (c) {
            WeatherCondition.CLEAR -> R.string.weather_cond_clear
            WeatherCondition.CLOUDY -> R.string.weather_cond_cloudy
            WeatherCondition.FOG -> R.string.weather_cond_fog
            WeatherCondition.DRIZZLE -> R.string.weather_cond_drizzle
            WeatherCondition.RAIN -> R.string.weather_cond_rain
            WeatherCondition.SNOW -> R.string.weather_cond_snow
            WeatherCondition.SHOWERS -> R.string.weather_cond_showers
            WeatherCondition.STORM -> R.string.weather_cond_storm
            WeatherCondition.UNKNOWN -> R.string.weather_cond_unknown
        },
    )

    fun hazardName(h: WeatherHazard): String = app.getString(
        when (h) {
            WeatherHazard.HEAVY_RAIN -> R.string.weather_hazard_heavy_rain
            WeatherHazard.SNOW -> R.string.weather_hazard_snow
            WeatherHazard.ICE -> R.string.weather_hazard_ice
            WeatherHazard.STORM -> R.string.weather_hazard_storm
            WeatherHazard.FOG -> R.string.weather_hazard_fog
            WeatherHazard.WIND -> R.string.weather_hazard_wind
            WeatherHazard.FROST -> R.string.weather_hazard_frost
        },
    )

    /** Wetter entlang der Route zur geschaetzten Ankunftszeit (Open-Meteo); Ansage nur waehrend der Fahrt. */
    private suspend fun refreshWeather(announce: Boolean, speakSummary: Boolean = false) {
        if (!_state.value.settings.weatherHints) { _state.update { it.copy(weather = null) }; return }
        val route = _state.value.route ?: return
        val along = _state.value.guidance?.alongMeters ?: 0.0
        val samples = WeatherApi.samples(route, along, System.currentTimeMillis() / 1000)
        val summary = runCatching { WeatherApi.parse(fetchUrl(WeatherApi.buildUrl(samples)), samples) }.getOrNull() ?: return
        if (summary.points.isEmpty() || _state.value.route !== route) return
        _state.update { it.copy(weather = summary) }
        if (!announce) return
        val dest = summary.destination
        if (speakSummary && dest?.tempC != null) {
            speakQuest(app.getString(R.string.weather_speech, conditionName(dest.condition), dest.tempC!!.toInt()))
        }
        for ((hazard, at) in summary.hazardsAhead) {
            if (announcedHazards.add(hazard)) {
                speakQuest(app.getString(R.string.weather_hazard_speech, hazardName(hazard), distanceText((at - along).coerceAtLeast(0.0))))
                break // pro Aktualisierung hoechstens eine neue Warnung
            }
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
        if (!_state.value.settings.breakSuggestions) return
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

    /** Beifahrer-Wunsch: jetzt eine Pause vorschlagen (nur waehrend der Fahrt und ohne offenen Vorschlag). */
    fun suggestBreakNow() {
        val s = _state.value
        val route = s.route ?: return
        val along = s.guidance?.alongMeters ?: return
        if (!s.navigating || breakPlanning || s.breakTarget != null || s.breakProposal != null) return
        planBreak(route, along)
    }

    private fun planBreak(route: Route, along: Double) {
        if (route.lengthMeters - along < 8_000.0) { breakPlanner.snooze(); return }
        breakPlanning = true
        scope.launch {
            try {
                val from = along + 4_000.0
                val to = minOf(route.lengthMeters - 1_000.0, along + 45_000.0)
                val osm = runCatching {
                    RestStops.parse(withContext(Dispatchers.IO) { fetchOverpass(RestStops.buildQuery(route, from, to)) }, route, from, to)
                }.getOrDefault(emptyList())
                // Autobahn-Rastplaetze mit bestaetigter Ausstattung (Spielplatz, WC) auf unserer Fahrbahn; sie gehen vor OSM-Treffern
                val official = AutobahnRestAreas.toRestStops(
                    autobahnRestAreas(route, along).filter { AutobahnRestAreas.onOurSide(route, it) }, route, from, to,
                )
                val stops = RestStops.merge(osm, official)
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

    // ---------------------------------------------------------------- Offline-Reise-Paket

    private fun estimateMb(route: Route): Int {
        val tiles = Corridor.estimateTiles(Corridor.boxes(route), PACKAGE_MIN_ZOOM, PACKAGE_MAX_ZOOM)
        return Corridor.estimateMegabytes(tiles).toInt().coerceAtLeast(1)
    }

    /** Toiletten/Rastplaetze eines geladenen Offline-Pakets; Rueckfall, wenn Overpass nicht erreichbar ist. */
    private var offlineToilets: List<ToiletStop> = emptyList()
    private var currentLanes: List<LaneGuidance> = emptyList()

    fun refreshPacks() = _state.update { it.copy(packs = packStore.list()) }

    /** Speichert die aktuelle Route als Offline-Paket: Karte, Route, Tempolimits, Laender, Halte am Weg, Spuren und Aufgaben. */
    fun downloadPackage() {
        val route = _state.value.route ?: return
        val dest = _state.value.destination?.name.orEmpty()
        packageJob?.cancel()
        packageJob = scope.launch {
            if (createPack(route, dest, dest, current = true, part = 0, parts = 0)) {
                refreshPacks()
                _state.update { it.copy(pkg = PackageState(PackagePhase.READY, 1.0, estimateMb(route))) }
            }
        }
    }

    /** Speichert alle Etappen der berechneten Reise nacheinander als Offline-Pakete. */
    fun downloadPlanPackages() {
        val s = _state.value
        val plan = s.plans.firstOrNull { it.id == s.planOpenId } ?: return
        val res = s.planResult ?: return
        val legs = res.itinerary?.legs ?: return
        if (res.legRoutes.size != legs.size) return
        packageJob?.cancel()
        packageJob = scope.launch {
            val label = plan.name.ifBlank { plan.destination?.name.orEmpty() }
            for ((i, r) in res.legRoutes.withIndex()) {
                val leg = legs[i]
                if (!createPack(r, "$label: ${leg.fromName} → ${leg.toName}", leg.toName, current = false, part = i + 1, parts = res.legRoutes.size)) return@launch
            }
            refreshPacks()
            _state.update { it.copy(pkg = PackageState(PackagePhase.READY, 1.0, res.packMb)) }
        }
    }

    private suspend fun createPack(route: Route, name: String, destName: String, current: Boolean, part: Int, parts: Int): Boolean {
        val mb = estimateMb(route)
        fun set(phase: PackagePhase, p: Double) = _state.update { it.copy(pkg = PackageState(phase, p, mb, part, parts)) }
        set(PackagePhase.MAP, 0.0)
        val mapOk = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
            offline.download(
                Corridor.boxes(route), PACKAGE_MIN_ZOOM, PACKAGE_MAX_ZOOM,
                onProgress = { set(PackagePhase.MAP, it) },
                onFinished = { ok -> if (cont.isActive) cont.resume(ok, null) },
            )
        }
        if (!mapOk) { set(PackagePhase.ERROR, 0.0); return false }

        // Tempolimits der ganzen Route in 25-km-Fenstern (fuer die aktuelle Route direkt ins Live-Profil)
        set(PackagePhase.LIMITS, 0.0)
        val profile = if (current) speedProfile else SpeedProfile()
        var m = 0.0
        while (m < route.lengthMeters) {
            currentCoroutineContext().ensureActive()
            val end = minOf(route.lengthMeters, m + SPEED_WINDOW_METERS)
            val ok = runCatching { fetchSpeedWindow(route, m, end, profile) }.isSuccess
            if (!ok) { set(PackagePhase.ERROR, m / route.lengthMeters); return false }
            m = end
            set(PackagePhase.LIMITS, m / route.lengthMeters)
        }

        // Laender entlang der Strecke (Photon, hoefliche Abstaende)
        set(PackagePhase.COUNTRIES, 0.0)
        val samples = Corridor.samplePositions(route)
        val found = ArrayList<Pair<Double, String>>()
        for ((i, smp) in samples.withIndex()) {
            currentCoroutineContext().ensureActive()
            val code = fetchCountryCode(smp.second.lat, smp.second.lon)
            if (code != null) found += smp.first to code
            set(PackagePhase.COUNTRIES, (i + 1.0) / samples.size)
            delay(400)
        }
        if (current && found.isNotEmpty() && _state.value.route === route) countryTimeline = CountryTimeline(found)

        // Toiletten und Rastplaetze am Weg (Overpass, fensterweise; Luecken sind kein Abbruchgrund)
        set(PackagePhase.STOPS, 0.0)
        val stops = fetchStopsAlong(route) { set(PackagePhase.STOPS, it) }

        // Aufgaben vorab aufloesen (Wikipedia, Natur, Routenfakten) und mitspeichern
        set(PackagePhase.QUESTS, 0.0)
        val offset = if (current) questOffset else 0.0
        val quests = resolveQuestsFor(route, current) { set(PackagePhase.QUESTS, it) }
            .filter { it.triggerMeters - offset >= 0.0 }
            .map { PackQuests.toPack(it, offset) }

        val lanes = if (current) currentLanes else runCatching {
            LaneParser.parseOsrm(
                fetchRoute(ValhallaApi.buildRouteRequest(route.line.points.first(), route.line.points.last(), Locale.getDefault().toLanguageTag(), osrm = true)),
                route,
            )
        }.getOrDefault(emptyList())

        val pack = OfflinePackCodec.build(
            java.util.UUID.randomUUID().toString(), name.ifBlank { app.getString(R.string.plan_unnamed) }, destName,
            java.time.LocalDate.now().toEpochDay().toInt(), route, profile, found, stops, lanes, quests, mb,
        )
        withContext(Dispatchers.IO) { packStore.save(pack) }
        return true
    }

    /** Toiletten/Rastplaetze entlang der ganzen Route, 50-km-Fenster mit Abstand zwischen den Anfragen. */
    private suspend fun fetchStopsAlong(route: Route, progress: (Double) -> Unit): List<ToiletStop> {
        val corridor = maxOf(2_000, _state.value.settings.toiletCorridorKm * 1_000)
        val all = ArrayList<ToiletStop>()
        var m = 0.0
        while (m < route.lengthMeters) {
            currentCoroutineContext().ensureActive()
            val end = minOf(route.lengthMeters, m + 50_000.0)
            runCatching { ToiletFinder.parse(withContext(Dispatchers.IO) { fetchOverpass(ToiletFinder.buildRouteQuery(route, m, end, corridor)) }) }
                .onSuccess { all += it }
            m = end
            progress(m / route.lengthMeters)
            delay(1_200)
        }
        return ToiletFinder.merge(all, emptyList())
    }

    /**
     * Aufgaben der Route mit allen Platzhaltern aufgeloest. Fuer die aktuelle Route wird der laufende Plan ersetzt
     * (und angezeigt), fuer fremde Routen entsteht ein frischer Plan, ohne den laufenden Zustand zu aendern.
     */
    private suspend fun resolveQuestsFor(route: Route, current: Boolean, progress: (Double) -> Unit): List<Quest> {
        val adv = if (current) adventure else null
        val offset = if (current) questOffset else 0.0
        val players = maxOf(_state.value.players, kidBands.size)
        val base: List<Quest> = if (current) _state.value.plan else generator.generate(
            routeLengthMeters = route.lengthMeters,
            ageBand = _state.value.ageBand,
            seed = System.nanoTime(),
            avoidMeters = route.maneuvers.map { it.atMeters },
            contextAt = { mm -> contextAt(route, mm) },
            usage = loadUsage(),
            ageBandAt = if (kidBands.isEmpty()) null else { i -> kidBands.getOrNull(i % players) ?: _state.value.ageBand },
        )
        val pending = setOf(RouteFacts.PENDING_KEY, WikiStories.PENDING_KEY, NatureSpotting.PENDING_KEY)
        val out = base.toMutableList()
        val todo = out.indices.filter { out[it].textKey in pending }
        for ((n, i) in todo.withIndex()) {
            currentCoroutineContext().ensureActive()
            val q = out[i]
            val along = q.triggerMeters - offset
            if (along in 0.0..route.lengthMeters) {
                val pos = route.line.points[route.line.indexAtMeters(along)]
                val resolved = when (q.textKey) {
                    RouteFacts.PENDING_KEY -> buildFact(q, pos)
                    WikiStories.PENDING_KEY -> buildStory(q, pos)
                    else -> buildNature(q, pos)
                }
                out[i] = resolved
                if (adv != null && adv.replaceUpcoming(q, resolved)) publishPlan()
            }
            progress((n + 1.0) / todo.size)
            delay(PRELOAD_GAP_MS)
        }
        return out
    }

    /** Laedt ein gespeichertes Paket ohne Netz: Route, Tempolimits, Laender, Halte, Spuren und Aufgaben. */
    fun loadPack(id: String) {
        scope.launch {
            val p = withContext(Dispatchers.IO) { packStore.load(id) }
            if (p == null) { _state.update { it.copy(error = R.string.pack_load_failed) }; return@launch }
            val route = OfflinePackCodec.route(p)
            installRoute(LoadedRoute(route, OfflinePackCodec.lanes(p)))
            speedProfile = OfflinePackCodec.speedProfile(p)
            countryTimeline = OfflinePackCodec.countryTimeline(p)
            offlineToilets = OfflinePackCodec.toilets(p)
            adoptQuests(p.quests.mapNotNull(PackQuests::fromPack))
            _state.update {
                it.copy(
                    destination = Place(p.destinationName, route.line.points.last()), route = route, guidance = null, incidents = emptyList(),
                    results = emptyList(), tripScreen = false, plansScreen = false, error = null,
                    pkg = PackageState(PackagePhase.READY, 1.0, p.mapMb),
                )
            }
            scope.launch { refreshWeather(announce = false) }
        }
    }

    fun deletePack(id: String) {
        packStore.delete(id)
        refreshPacks()
    }

    fun clearOfflineMaps() {
        offline.clear { _state.update { it.copy(pkg = PackageState(estimatedMb = it.pkg.estimatedMb)) } }
    }

    /** Ein Tempolimit-Fenster laden und in den Cache eintragen (wirft bei Fehler). */
    private suspend fun fetchSpeedWindow(route: Route, startM: Double, endM: Double, profile: SpeedProfile = speedProfile) {
        val last = route.line.points.size - 1
        val i0 = route.line.indexAtMeters(startM)
        val i1 = minOf(last, route.line.indexAtMeters(endM) + 1)
        if (i1 <= i0) return
        val body = fetchValhalla("trace_attributes", TraceAttributes.buildRequest(route, i0, i1))
        val segs = TraceAttributes.parse(body, route, i0)
        profile.addWindow(route.line.cumulative[i0], route.line.cumulative[i1], segs)
    }

    // ---------------------------------------------------------------- Tempolimit & Spuren

    private fun updateDrivingAssist(loc: Location, along: Double) {
        val route = _state.value.route ?: return
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6 else null
        val limit = speedProfile.limitAt(along)
        val laneNow = if (_state.value.settings.laneHints) laneAnnouncer?.current(along) else null
        _state.update {
            it.copy(
                speedKmh = kmh?.toInt(),
                speedLimit = limit,
                overspeed = kmh != null && SpeedProfile.isOverspeed(kmh, limit),
                lanes = laneNow,
            )
        }
        if (_state.value.settings.overspeedWarning) {
            // Nur Audio; laeuft gerade eine Ansage, wird die Warnung spaeter nachgeholt
            overspeed.onTick(System.currentTimeMillis(), kmh, limit, busy = tts?.isSpeaking == true)?.let {
                speak(app.getString(R.string.overspeed_warning, it.limitKmh))
            }
        } else overspeed.reset()
        if (_state.value.settings.laneHints) laneAnnouncer?.next(along)?.let { g ->
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
        if (!_state.value.settings.detourSuggestions) return
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
        scope.launch { refreshWeather(announce = false) }
    }

    fun dismissReroute() = _state.update { it.copy(proposal = null) }

    /** Navigationsansage: hat Vorrang und unterbricht Quest-Ansagen. */
    private fun speak(text: String) {
        if (!_state.value.settings.naviVoice) return
        kiloClips.stop() // Navigationsansagen haben Vorrang vor Kilos Aufnahmen
        if (foreignActive) endForeign()
        arbiter.navSpoken(System.currentTimeMillis())
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kq-nav-${System.nanoTime()}")
    }

    private fun speakQuest(text: String, utteranceId: String = "kq-quest-${System.nanoTime()}") {
        if (!_state.value.settings.kiloVoice) return
        // Passt der Text zu einer Aufnahme, spricht Kilo mit der aufgenommenen Stimme, sonst die Sprachausgabe
        if (kiloClips.play(text)) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    private fun onQuestTriggered(q: Quest, pos: LatLon) {
        val adv = adventure
        _state.update {
            it.copy(quest = q, feedback = null, newSticker = null, currentPlayer = adv?.currentPlayer ?: 0)
        }
        when {
            q.textKey == RouteFacts.PENDING_KEY -> resolveQuest(q) { buildFact(q, pos) }
            q.textKey == WikiStories.PENDING_KEY -> resolveQuest(q) { buildStory(q, pos) }
            q.textKey == PlaneSpotting.PENDING_KEY -> resolveQuest(q) { skyQuest(q, pos) }
            q.textKey == LanguageCourses.PENDING_KEY -> resolveQuest(q) {
                val course = LanguageCourses.forCountry(_state.value.country)
                if (course == null) RouteFacts.resolve(q, null) else languageQuest(q, course, taughtPhrases.toSet())
            }
            q.textKey == NatureSpotting.PENDING_KEY -> resolveQuest(q) { buildNature(q, pos) }
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
        if (q.textKey == WikiStories.TEXT_KEY && q.args.size >= 2) logDiary(DiaryKind.STORY, q.args[0], q.args[1])
        val s = _state.value
        val text = buildString {
            if (s.players > 1) append(app.getString(R.string.kids_turn, app.getString(R.string.kids_player, s.currentPlayer + 1))).append(' ')
            // Fuer die Kleinsten (die noch nicht lesen) liest Kilo bei Quizfragen auch die Antworten vor
            val band = kidBands.getOrNull(s.currentPlayer) ?: s.ageBand
            append(app.questSpeech(q, withOptions = band == AgeBand.MINI))
        }
        arbiter.offerQuest(text, System.currentTimeMillis(), tts?.isSpeaking == true || kiloClips.isPlaying)?.let(::speakQuest)
    }


    // ---------------------------------------------------------------- Aufgaben vorladen und live ergaenzen

    private suspend fun buildFact(q: Quest, pos: LatLon): Quest {
        val facts = runCatching {
            RouteFacts.parse(withContext(Dispatchers.IO) { fetchOverpass(RouteFacts.buildQuery(pos.lat, pos.lon)) })
        }.getOrDefault(emptyList())
        return RouteFacts.resolve(q, pickFresh(facts, { "fact:" + it.name }) { RouteFacts.pick(it, Random.Default) })
    }

    private suspend fun buildStory(q: Quest, pos: LatLon): Quest {
        val age = _state.value.ageBand
        val lang = Locale.getDefault().language
        var story: Story? = null
        // Erst Geraetesprache, dann Englisch als Rueckfall
        for (l in listOf(lang, "en").distinct()) {
            val list = runCatching { WikiStories.parse(fetchUrl(WikiStories.buildUrl(l, pos.lat, pos.lon, age)), age) }
                .getOrDefault(emptyList())
            story = pickFresh(list, { "story:" + it.title }) { WikiStories.pick(it, Random.Default) }
            if (story != null) break
        }
        return WikiStories.resolve(q, story)
    }

    private suspend fun buildNature(q: Quest, pos: LatLon): Quest {
        val lang = Locale.getDefault().language
        var picked: app.kiloquest.game.Species? = null
        for (l in listOf(lang, "en").distinct()) {
            val list = runCatching { NatureSpotting.parse(fetchUrl(NatureSpotting.buildUrl(pos.lat, pos.lon, l))) }
                .getOrDefault(emptyList())
            picked = pickFresh(list, { "nature:" + it.name }) { NatureSpotting.pick(it, Random.Default) }
            if (picked != null) break
        }
        return NatureSpotting.resolve(q, picked)
    }

    private fun publishPlan() {
        val adv = adventure ?: return
        _state.update { it.copy(plan = adv.upcoming(), questsTotal = adv.total) }
    }

    /**
     * Laedt Platzhalter-Aufgaben (Streckenfakt, Geschichte, Natur) vorab fuer den Ort, an dem sie ausgeloest werden.
     * Naechste zuerst; schlaegt ein Abruf fehl (offline), bleibt die Aufgabe ein Platzhalter und wird live geladen.
     * Himmel und Sprache hängen von Zeit und Land ab und bleiben bewusst live.
     */
    private fun startPreload() {
        preloadJob?.cancel()
        preloadJob = scope.launch {
            while (true) {
                val adv = adventure ?: return@launch
                val route = _state.value.route
                if (route == null) { delay(1_000); continue }
                val next = adv.upcoming().take(PRELOAD_WINDOW).firstOrNull { q ->
                    (q.textKey == RouteFacts.PENDING_KEY || q.textKey == WikiStories.PENDING_KEY || q.textKey == NatureSpotting.PENDING_KEY) &&
                        q.triggerMeters - questOffset in 0.0..route.lengthMeters && q.triggerMeters !in preloadFailed
                }
                if (next == null) { delay(PRELOAD_IDLE_MS); continue }
                val pos = route.line.points[route.line.indexAtMeters(next.triggerMeters - questOffset)]
                val resolved = when (next.textKey) {
                    RouteFacts.PENDING_KEY -> buildFact(next, pos)
                    WikiStories.PENDING_KEY -> buildStory(next, pos)
                    else -> buildNature(next, pos)
                }
                if (resolved.textKey == RouteFacts.FALLBACK_KEY) preloadFailed += next.triggerMeters
                else if (adv.replaceUpcoming(next, resolved)) publishPlan()
                delay(PRELOAD_GAP_MS)
            }
        }
    }

    /** Zusaetzliche Live-Aufgabe einplanen (siehe BonusPlanner); wird gleich danach regulaer ausgeloest. */
    private fun maybeAddBonus(along: Double, metersToManeuver: Double) {
        if (!_state.value.settings.liveQuests) return
        val adv = adventure ?: return
        val planner = bonusPlanner ?: return
        val q = planner.next(along, adv.active != null, adv.lastTriggerMeters, adv.upcoming().firstOrNull()?.triggerMeters, metersToManeuver) ?: return
        adv.insertBonus(q)
        publishPlan()
    }


    // ---------------------------------------------------------------- Passende Aufgaben, keine Wiederholungen

    /** Ort, Land und geschaetzte Tageszeit am Streckenpunkt [meters] (Ankunftszeit aus der Routendauer). */
    private fun contextAt(route: Route, meters: Double): QuestContext {
        val p = route.line.points[route.line.indexAtMeters(meters.coerceIn(0.0, route.lengthMeters))]
        val etaSec = System.currentTimeMillis() / 1000 + (route.durationSeconds * meters / route.lengthMeters.coerceAtLeast(1.0)).toLong()
        val country = countryTimeline?.countryAt(meters) ?: CountryGuess.guess(p.lat, p.lon)
        return QuestContext(p.lat, p.lon, country, Sun.isDark(p.lat, p.lon, etaSec))
    }

    /**
     * Beim Ausloesen mit dem echten Ort (aktuelles Land) und der echten Uhrzeit pruefen; passt eine feste Aufgabe nicht
     * (z. B. "Findet eine Kuh" bei Dunkelheit), wird sie durch eine passende ersetzt. Nutzung wird fuer spaetere Fahrten gezaehlt.
     */
    private fun contextualize(q: Quest, pos: LatLon): Quest {
        val id = q.templateId.removePrefix("bonus_")
        val t = QuestCatalog.default.firstOrNull { it.id == id } ?: return q
        val ctx = QuestContext(pos.lat, pos.lon, _state.value.country ?: CountryGuess.guess(pos.lat, pos.lon), Sun.isDark(pos.lat, pos.lon, System.currentTimeMillis() / 1000))
        var result = q
        if (!QuestFit.fits(t, ctx)) {
            val ageBand = _state.value.ageBand
            val pool = QuestCatalog.default.filter { ageBand in it.ageBands && it.type != QuestType.BREAK }
            val repl = QuestFit.choose(pool, ctx, loadUsage(), tripStatics, Random.Default)
            if (repl != null) {
                result = repl.toQuest(q.triggerMeters)
                if (!repl.dynamic) tripStatics.merge(repl.id, 1, Int::plus)
                adventure?.replaceActive(result)
            }
        }
        QuestCatalog.default.firstOrNull { it.id == result.templateId.removePrefix("bonus_") }?.takeIf { !it.dynamic }?.let { bumpUsage(it.id) }
        return result
    }

    private fun loadUsage(): Map<String, Int> = runCatching {
        val o = org.json.JSONObject(prefs.getString("quest_usage", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    private fun bumpUsage(id: String) {
        val m = loadUsage().toMutableMap()
        m.merge(id, 1, Int::plus)
        // Bei vielen Fahrten abklingen lassen, damit nichts dauerhaft ausgeschlossen bleibt
        if (m.values.sum() > 400) m.replaceAll { _, v -> v / 2 }
        prefs.edit().putString("quest_usage", org.json.JSONObject(m as Map<*, *>).toString()).apply()
    }

    /** Inhalte (Orte, Tiere, Fakten), die schon gestellt wurden; ueber Fahrten hinweg gemerkt, damit sich nichts wiederholt. */
    private val contentUsed: LinkedHashSet<String> by lazy {
        LinkedHashSet<String>().also { set ->
            runCatching { val a = org.json.JSONArray(prefs.getString("content_used", "[]")); for (i in 0 until a.length()) set += a.getString(i) }
        }
    }

    private fun <T> pickFresh(list: List<T>, key: (T) -> String, pick: (List<T>) -> T?): T? {
        val fresh = list.filter { key(it) !in contentUsed }
        val chosen = pick(fresh) ?: return null
        contentUsed += key(chosen)
        while (contentUsed.size > CONTENT_HISTORY) contentUsed.remove(contentUsed.first())
        prefs.edit().putString("content_used", org.json.JSONArray(contentUsed.toList()).toString()).apply()
        return chosen
    }

    // ---------------------------------------------------------------- Klo-Alarm

    /** Naechste Toilette in Fahrzeit suchen und sofort ansteuern; danach geht es zum alten Ziel weiter. */
    fun toiletAlarm() {
        val pos = _state.value.position
        if (pos == null) { _state.update { it.copy(error = R.string.error_no_position) }; return }
        if (BuildConfig.VALHALLA_URL.isBlank()) { _state.update { it.copy(error = R.string.error_no_server) }; return }
        _state.value.toilet?.let { t ->
            // Alarm laeuft schon: ein weiterer Tipp wiederholt nur die Ansage (kein neuer Start, kein Abbruch)
            when (t.phase) {
                ToiletPhase.SEARCHING -> return
                ToiletPhase.HEADING -> { speak(app.getString(R.string.toilet_found, t.label, app.getString(R.string.unit_min, ((t.seconds ?: 60.0) / 60).toInt().coerceAtLeast(1)))); return }
                ToiletPhase.ARRIVED -> { speak(app.getString(R.string.toilet_arrived)); return }
                ToiletPhase.NONE -> Unit
            }
        }
        toiletJob?.cancel()
        _state.update { it.copy(toilet = ToiletState(ToiletPhase.SEARCHING), toiletRequest = null, error = null) }
        speak(app.getString(R.string.toilet_searching))
        toiletJob = scope.launch {
            toiletOnlyClosed = false
            val choice = findToilet(pos)
            if (choice == null) {
                speak(app.getString(if (toiletOnlyClosed) R.string.toilet_none_open else R.string.toilet_none))
                _state.update { it.copy(toilet = ToiletState(ToiletPhase.NONE)) }
                delay(8_000)
                _state.update { if (it.toilet?.phase == ToiletPhase.NONE) it.copy(toilet = null) else it }
                return@launch
            }
            val label = toiletLabel(choice.stop) + (choice.closesAt?.let { " · " + app.getString(R.string.toilet_open_until, java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).format(it)) } ?: "")
            val dest = Place(label, choice.stop.position)
            runCatching { loadRoute(pos, dest.position, emptyList()) }.onSuccess { loaded ->
                val wasNavigating = _state.value.navigating
                // Nur zurueck zum alten Ziel, wenn vorher navigiert wurde und es eines gab
                val oldDest = if (wasNavigating) _state.value.destination else null
                if (toiletResume == null) toiletResume = oldDest
                val along = _state.value.guidance?.alongMeters ?: 0.0
                installRoute(loaded)
                if (adventure == null || !wasNavigating) newAdventure(loaded.route) else questOffset += along
                _state.update {
                    it.copy(
                        route = loaded.route, destination = dest, guidance = null, incidents = emptyList(), proposal = null,
                        breakProposal = null, breakTarget = null,
                        toilet = ToiletState(ToiletPhase.HEADING, label, loaded.route.durationSeconds, loaded.route.lengthMeters),
                    )
                }
                if (!wasNavigating) startNavigation()
                val minutes = (loaded.route.durationSeconds / 60).toInt().coerceAtLeast(1)
                speak(app.getString(R.string.toilet_found, label, app.getString(R.string.unit_min, minutes)))
                scope.launch { refreshTraffic() }
            }.onFailure {
                speak(app.getString(R.string.toilet_none))
                _state.update { it.copy(toilet = null) }
            }
        }
    }

    /**
     * Klo-Alarm: Wird navigiert, wird die naechste Toilette auf dem WEITEREN Reiseverlauf gesucht (nicht dahinter): in einem
     * Korridor um die Route, erst die naechsten 15 km, dann 40 km, dann 100 km, mit etwas breiterem Korridor. Unter den
     * besten Kandidaten entscheidet die echte Fahrzeit. Ohne Route oder wenn nichts voraus liegt: Umkreissuche um die Position.
     */
    private suspend fun findToilet(pos: LatLon): ToiletChoice? {
        val s = _state.value
        val route = s.route
        val along = s.guidance?.alongMeters
        if (route != null && s.navigating && along != null) findToiletAhead(route, along, pos, s.settings.toiletCorridorKm * 1000.0)?.let { return it }
        return findToiletAround(pos)
    }

    /** Autobahn-Rastplaetze mit bestaetigter Toilette fuer die Autobahnen auf dem weiteren Weg (je Autobahn 12 Stunden gemerkt). */
    private val restAreaCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<ToiletStop>>>()

    private suspend fun autobahnRestAreas(route: Route, along: Double): List<ToiletStop> {
        val names = route.maneuvers.filter { it.atMeters + it.lengthMeters >= along }.flatMap { it.streetNames }
        val roads = AutobahnApi.roadsFromNames(names).take(4)
        if (roads.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return kotlinx.coroutines.coroutineScope {
            roads.map { r ->
                async(Dispatchers.IO) {
                    restAreaCache[r]?.takeIf { now - it.first < 12 * 3_600_000L }?.second
                        ?: runCatching { AutobahnRestAreas.parse(fetchUrl(AutobahnRestAreas.url(r)), requireToilet = false) }.getOrNull()?.also { restAreaCache[r] = now to it }
                        ?: emptyList()
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun findToiletAhead(route: Route, along: Double, pos: LatLon, corridorMeters: Double): ToiletChoice? {
        // nur Plaetze auf unserer Fahrbahn mit Toilette (Gegenrichtung ist nicht erreichbar)
        val official = autobahnRestAreas(route, along).filter { StopFeature.TOILET in it.features && AutobahnRestAreas.onOurSide(route, it) }
        val tiers = listOf(15_000.0 to corridorMeters, 40_000.0 to corridorMeters * 1.5, 100_000.0 to corridorMeters * 2)
        for ((window, corridor) in tiers) {
            val w = minOf(window, route.lengthMeters - along)
            if (w < 300.0) break
            val osm = runCatching { ToiletFinder.parse(fetchOverpass(ToiletFinder.buildRouteQuery(route, along, along + w, corridor.toInt()))) }.getOrNull() ?: offlineToilets
            // Betreiberdaten (Autobahn) und OpenStreetMap zusammen; faellt Overpass aus, reichen die Autobahn-Daten allein
            val cands = ToiletFinder.merge(osm, official)
            val top = ToiletFinder.ahead(cands, route, along, w, corridor).take(8)
            if (top.isEmpty()) continue
            val stops = top.map { it.stop }
            val seconds = runCatching {
                ToiletFinder.parseMatrix(fetchValhalla("sources_to_targets", ToiletFinder.matrixRequest(pos, stops)), stops.size)
            }.getOrNull()
            val choice = ToiletFinder.choose(pos, stops, seconds, System.currentTimeMillis())
            if (choice == null) { toiletOnlyClosed = true; continue }
            return choice.copy(aheadMeters = top.firstOrNull { it.stop == choice.stop }?.aheadMeters)
        }
        return null
    }

    private suspend fun findToiletAround(pos: LatLon): ToiletChoice? {
        for (radius in listOf(6_000, 20_000)) {
            val cands = runCatching { ToiletFinder.parse(fetchOverpass(ToiletFinder.buildQuery(pos, radius))) }.getOrNull() ?: offlineToilets
            val near = ToiletFinder.nearest(cands, pos, 8)
            if (near.isEmpty()) continue
            val seconds = runCatching {
                ToiletFinder.parseMatrix(fetchValhalla("sources_to_targets", ToiletFinder.matrixRequest(pos, near)), near.size)
            }.getOrNull()
            val c = ToiletFinder.choose(pos, near, seconds, System.currentTimeMillis())
            if (c == null) toiletOnlyClosed = true else return c
        }
        return null
    }

    private fun toiletLabel(s: ToiletStop): String {
        val kind = app.getString(
            when (s.kind) {
                ToiletKind.TOILETS -> R.string.toilet_kind_toilets
                ToiletKind.REST_AREA -> R.string.toilet_kind_rest_area
                ToiletKind.SERVICES -> R.string.toilet_kind_services
                ToiletKind.FUEL -> R.string.toilet_kind_fuel
            },
        )
        val base = if (s.name.isNullOrBlank()) kind else "$kind ${s.name}"
        // Autobahn-Rastplaetze melden Spielplatz und Rollstuhl-WC: fuer Familien nuetzlich
        return if (StopFeature.PLAYGROUND in s.features) base + " (" + app.getString(R.string.break_playground) + ")" else base
    }

    /** "Weiter": zurueck zum alten Ziel (oder Navigation beenden, wenn es keines gab). Gilt auch als Abbrechen. */
    fun resumeAfterToilet() {
        val t = _state.value.toilet ?: return
        if (t.phase == ToiletPhase.SEARCHING) { toiletJob?.cancel(); _state.update { it.copy(toilet = null) }; return }
        val resume = toiletResume
        toiletResume = null
        if (t.phase == ToiletPhase.NONE || resume == null) { stopNavigation(); return }
        _state.update { it.copy(destination = resume, toilet = null) }
        speak(app.getString(R.string.toilet_resume))
        calculateRoute()
    }

    /** Ein Kind (Geraet [from], null = dieses Geraet) moechte zur Toilette: nur melden, der Beifahrer entscheidet. */
    fun toiletRequested(from: String?) {
        if (_state.value.toilet != null) return
        val name = from ?: app.getString(R.string.toilet_request_local)
        _state.update { it.copy(toiletRequest = name) }
        speakQuest(app.getString(R.string.toilet_request_spoken, name))
    }

    fun dismissToiletRequest() = _state.update { it.copy(toiletRequest = null) }
}
