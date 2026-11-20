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
import app.kiloquest.game.Sticker
import app.kiloquest.game.StickerBook
import app.kiloquest.nav.SpeechArbiter
import kotlin.random.Random
import kotlinx.coroutines.withContext
import app.kiloquest.nav.AnnouncementPlanner
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
)

/** Gemeinsamer Navigationszustand fuer Handy-UI und Android-Auto (lebt im Application-Prozess). */
class NavController(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences("kq", Context.MODE_PRIVATE)
    private val generator = QuestGenerator(QuestCatalog.default)
    private var adventure: AdventureSession? = null

    private val arbiter = SpeechArbiter()
    private val book = StickerBook(prefs.getStringSet("album", emptySet()).orEmpty())

    private val _state = MutableStateFlow(
        UiState(
            ageBand = runCatching { AgeBand.valueOf(prefs.getString("age", null) ?: "EXPLORER") }.getOrDefault(AgeBand.EXPLORER),
            players = prefs.getInt("players", 1).coerceIn(1, 4),
            album = book.collected.toSet(),
            totalPoints = prefs.getInt("total_points", 0),
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
        _state.update { it.copy(destination = place, results = emptyList()) }
        calculateRoute()
    }

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
            runCatching {
                val req = ValhallaApi.buildRouteRequest(from, dest.position, Locale.getDefault().toLanguageTag())
                ValhallaApi.parseRoute(fetchRoute(req))
            }.onSuccess { route ->
                engine = GuidanceEngine(route)
                planner = AnnouncementPlanner(route)
                // Abenteuer nur beim ersten Berechnen anlegen, nicht bei Neuberechnung unterwegs
                if (adventure == null || !_state.value.navigating) newAdventure(route)
                _state.update { it.copy(route = route, busy = false, guidance = null) }
            }.onFailure {
                _state.update { it.copy(busy = false, error = R.string.error_route) }
            }
        }
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
    }

    fun stopNavigation() {
        _state.update {
            it.copy(navigating = false, route = null, destination = null, guidance = null, quest = null, feedback = null, kidsScreen = false)
        }
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
        adventure?.onProgress(g.alongMeters)?.let { q -> onQuestTriggered(q, pos) }
        arbiter.poll(System.currentTimeMillis(), tts?.isSpeaking == true)?.let(::speakQuest)
        val now = System.currentTimeMillis()
        if (g.isOffRoute && now - lastReroute > 15_000) {
            lastReroute = now
            speak(app.getString(R.string.rerouting))
            calculateRoute()
        }
    }

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
        if (q.type == QuestType.ROUTE_FACT && q.textKey == RouteFacts.PENDING_KEY) {
            // Streckenfakt: Umgebung per Overpass abfragen, bei Fehler Rueckfall-Aufgabe
            scope.launch {
                val facts = runCatching {
                    RouteFacts.parse(withContext(Dispatchers.IO) { fetchOverpass(RouteFacts.buildQuery(pos.lat, pos.lon)) })
                }.getOrDefault(emptyList())
                val resolved = RouteFacts.resolve(q, RouteFacts.pick(facts, Random.Default))
                if (adventure?.active?.triggerMeters == q.triggerMeters) {
                    adventure?.replaceActive(resolved)
                    _state.update { it.copy(quest = resolved) }
                    announceQuest(resolved)
                }
            }
        } else {
            announceQuest(q)
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
