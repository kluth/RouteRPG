package app.kiloquest

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.kiloquest.nav.IncidentType
import app.kiloquest.nav.LatLon
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** OpenFreeMap: freie Vektorkacheln ohne API-Key (nur fuer Entwicklung/privat; spaeter Offline-PMTiles). */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val ROUTE_SOURCE = "route-source"
private const val ROUTE_LAYER = "route-layer"
private const val INCIDENT_SOURCE = "incident-source"
private const val INCIDENT_LAYER = "incident-layer"

class MainActivity : ComponentActivity() {
    private val vm: NavController by lazy { (application as KiloQuestApp).nav }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) vm.startLocationUpdates()
            }
            LaunchedEffect(Unit) {
                if (vm.hasLocationPermission()) vm.startLocationUpdates()
                else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            MaterialTheme { AppScreen(vm) }
        }
    }
}

@Composable
private fun AppScreen(vm: NavController) {
    val state by vm.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        MapScreen(
            route = state.route?.line?.points,
            position = state.position,
            follow = state.navigating,
            incidents = state.incidents.map { it.incident.position },
        )

        Column(Modifier.align(Alignment.TopCenter).padding(12.dp)) {
            if (!state.navigating) SearchPanel(state, vm)
            state.proposal?.let { ProposalCard(it, vm) }
            if (state.proposal == null) IncidentBanner(state)
            state.error?.let {
                Surface(tonalElevation = 4.dp, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(it), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (state.kidsScreen) KidsScreen(state, vm)

        val route = state.route
        if (route != null && !state.kidsScreen) {
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 24.dp, start = 12.dp, end = 12.dp),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val g = state.guidance
                    val meters = g?.remainingMeters ?: route.lengthMeters
                    val summary = if (g != null) {
                        stringResource(R.string.remaining_summary, formatDistance(meters))
                    } else {
                        stringResource(R.string.route_summary, formatDistance(meters), formatDuration(route.durationSeconds))
                    }
                    Text(summary, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.showKids(true) }) { Text(stringResource(R.string.kids_open)) }
                        if (state.navigating) {
                            Button(onClick = vm::stopNavigation) { Text(stringResource(R.string.stop_navigation)) }
                        } else {
                            Button(onClick = vm::startNavigation) { Text(stringResource(R.string.start_navigation)) }
                        }
                    }
                }
            }
        } else if (!state.kidsScreen) {
            Text(
                text = stringResource(R.string.map_attribution),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}

@Composable
private fun incidentName(t: IncidentType): String = stringResource(
    when (t) {
        IncidentType.JAM -> R.string.incident_jam
        IncidentType.ACCIDENT -> R.string.incident_accident
        IncidentType.CLOSURE -> R.string.incident_closure
        IncidentType.HAZARD -> R.string.incident_hazard
    },
)

/** Naechste relevante Verkehrsmeldung voraus (nur Anzeige). */
@Composable
private fun IncidentBanner(state: UiState) {
    val ri = state.incidents.firstOrNull { it.aheadMeters >= -1.0 && (it.incident.blocked || (it.incident.delayMinutes ?: 0) >= 5) } ?: return
    Surface(tonalElevation = 4.dp, modifier = Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.traffic_banner, incidentName(ri.incident.type), formatDistance(ri.aheadMeters.coerceAtLeast(0.0)), ri.incident.road),
            Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ProposalCard(p: RerouteProposal, vm: NavController) {
    Surface(tonalElevation = 8.dp, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (p.blocked) R.string.reroute_blocked else R.string.reroute_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                incidentName(p.incident.incident.type) + " · " + p.incident.incident.road +
                    if (p.savedMinutes > 0) " · " + stringResource(R.string.reroute_saves, p.savedMinutes) else "",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::acceptReroute) { Text(stringResource(R.string.reroute_accept)) }
                androidx.compose.material3.OutlinedButton(onClick = vm::dismissReroute) { Text(stringResource(R.string.reroute_dismiss)) }
            }
        }
    }
}

@Composable
private fun SearchPanel(state: UiState, vm: NavController) {
    var query by remember { mutableStateOf("") }
    Surface(tonalElevation = 6.dp) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { vm.search(query) }, modifier = Modifier.padding(start = 8.dp), enabled = !state.busy) {
                    Text(stringResource(R.string.search_action))
                }
            }
            state.results.forEach { place ->
                Text(
                    place.name,
                    modifier = Modifier.fillMaxWidth().clickable { vm.selectPlace(place) }.padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun formatDistance(meters: Double): String =
    if (meters >= 1000) stringResource(R.string.unit_km, meters / 1000.0)
    else stringResource(R.string.unit_m, meters.toInt())

@Composable
private fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes >= 60) stringResource(R.string.unit_h_min, minutes / 60, minutes % 60)
    else stringResource(R.string.unit_min, minutes)
}

@Composable
private fun MapScreen(route: List<LatLon>?, position: LatLon?, follow: Boolean, incidents: List<LatLon>) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { m ->
                map = m
                m.setStyle(STYLE_URL) { style ->
                    style.addSource(GeoJsonSource(ROUTE_SOURCE))
                    style.addLayer(
                        LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                            PropertyFactory.lineColor("#1E88E5"),
                            PropertyFactory.lineWidth(6f),
                            PropertyFactory.lineCap("round"),
                            PropertyFactory.lineJoin("round"),
                        ),
                    )
                    style.addSource(GeoJsonSource(INCIDENT_SOURCE))
                    style.addLayer(
                        CircleLayer(INCIDENT_LAYER, INCIDENT_SOURCE).withProperties(
                            PropertyFactory.circleColor("#D32F2F"),
                            PropertyFactory.circleRadius(9f),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(2f),
                        ),
                    )
                    styleReady = true
                }
                m.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(51.1657, 10.4515)) // Mitte Deutschlands
                    .zoom(5.5)
                    .build()
            }
        }
    }

    // Route zeichnen/loeschen
    LaunchedEffect(route, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        m.getStyle { style: Style ->
            val src = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: return@getStyle
            if (route == null) {
                src.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(emptyList()))
            } else {
                src.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(route.map { Point.fromLngLat(it.lon, it.lat) })))
            }
        }
    }

    // Verkehrsmeldungen als rote Punkte
    LaunchedEffect(incidents, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        m.getStyle { style: Style ->
            val src = style.getSourceAs<GeoJsonSource>(INCIDENT_SOURCE) ?: return@getStyle
            src.setGeoJson(
                org.maplibre.geojson.FeatureCollection.fromFeatures(
                    incidents.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) },
                ),
            )
        }
    }

    // Kamera folgt der Position waehrend der Navigation
    LaunchedEffect(position, follow) {
        val m = map ?: return@LaunchedEffect
        val p = position ?: return@LaunchedEffect
        if (follow) m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), 16.0))
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.onCreate(null)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}
