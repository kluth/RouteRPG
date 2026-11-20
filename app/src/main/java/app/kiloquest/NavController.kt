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

data class UiState(
    val results: List<Place> = emptyList(),
    val destination: Place? = null,
    val route: Route? = null,
    val navigating: Boolean = false,
    val guidance: GuidanceState? = null,
    val position: LatLon? = null,
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
)

/** Gemeinsamer Navigationszustand fuer Handy-UI und Android-Auto (lebt im Application-Prozess). */
class NavController(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(UiState())
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
                _state.update { it.copy(route = route, busy = false, guidance = null) }
            }.onFailure {
                _state.update { it.copy(busy = false, error = R.string.error_route) }
            }
        }
    }

    fun startNavigation() {
        if (_state.value.route == null) return
        _state.update { it.copy(navigating = true) }
        _state.value.route?.maneuvers?.firstOrNull()?.instruction?.let(::speak)
    }

    fun stopNavigation() {
        _state.update { it.copy(navigating = false, route = null, destination = null, guidance = null) }
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
        val now = System.currentTimeMillis()
        if (g.isOffRoute && now - lastReroute > 15_000) {
            lastReroute = now
            speak(app.getString(R.string.rerouting))
            calculateRoute()
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "kq-${System.nanoTime()}")
    }

}
