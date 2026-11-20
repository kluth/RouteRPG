package app.kiloquest

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.kiloquest.link.toUiState as toUiStateFor
import app.kiloquest.ui.EtaBar
import app.kiloquest.ui.QuestsPill
import app.kiloquest.ui.LaneStrip
import app.kiloquest.ui.ManeuverCard
import app.kiloquest.ui.SpeedSign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
private const val STYLE_URL = MAP_STYLE_URL
private const val ROUTE_SOURCE = "route-source"
private const val ROUTE_LAYER = "route-layer"
private const val INCIDENT_SOURCE = "incident-source"
private const val INCIDENT_LAYER = "incident-layer"
private const val AVATAR_SOURCE = "avatar-source"
private const val AVATAR_LAYER = "avatar-layer"
private const val AVATAR_IMAGE = "avatar-image"
private const val AVATAR_IMAGE_FLIP = "avatar-image-flip"


class MainActivity : ComponentActivity() {
    private val vm: NavController by lazy { (application as KiloQuestApp).nav }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Unter Android 15 ist Edge-to-Edge Pflicht; die Oberflaeche haelt selbst Abstand zu den Systemleisten
        enableEdgeToEdge()
        MapLibre.getInstance(this)
        (application as KiloQuestApp).link.resumeIfPaired()
        setContent {
            // Benachrichtigungen (Android 13+): damit die Anzeige "Navigation laeuft" sichtbar ist; optional
            val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) vm.startLocationUpdates()
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            LaunchedEffect(Unit) {
                if (vm.hasLocationPermission()) vm.startLocationUpdates()
                else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            app.kiloquest.ui.KiloQuestTheme { AppScreen(vm) }
        }
    }
}

@Composable
private fun AppScreen(vm: NavController) {
    val state by vm.state.collectAsState()
    // Waehrend der Navigation bleibt das Display an
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(state.navigating) {
        view.keepScreenOn = state.navigating
        onDispose { view.keepScreenOn = false }
    }
    val win = app.kiloquest.ui.rememberWindowInfo()
    // Statusleiste (Benachrichtigungen, Uhr) und Ausschnitte/Kameraloch bleiben frei: nichts wird darunter gezeichnet
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .imePadding(),
    ) {
        MapScreen(
            route = state.route?.line?.points,
            position = state.position,
            follow = state.navigating,
            incidents = state.incidents.map { it.incident.position },
            avatar = state.avatar,
            bearing = state.bearing,
        )
        AppOverlays(state, vm)
    }
}

/** Alles ueber der Karte; getrennt von der Karte, damit sich das Layout ohne MapView pruefen laesst. */
@Composable
internal fun BoxScope.AppOverlays(state: UiState, vm: NavController) {
    val win = app.kiloquest.ui.rememberWindowInfo()
    val overlays = !state.kidsScreen && !state.tripScreen && !state.riddleScreen && !state.parentScreen
    // Obere Spalte: Suche/Vorschlaege (vor der Fahrt) bzw. Hinweise unter der Abbiege-Karte (waehrend der Fahrt).
    // Auf breiten Fenstern bleibt sie schmal und links, statt ueber die ganze Breite zu laufen.
    // Querformat waehrend der Fahrt: neben der Abbiege-Karte statt darunter (sonst bleibt zu wenig Platz)
    val sideBySide = state.navigating && win.landscape
    Column(
        Modifier
            .align(Alignment.TopStart)
            .then(
                when {
                    sideBySide -> Modifier.fillMaxWidth().padding(start = 308.dp, end = 176.dp)
                    win.wide -> Modifier.widthIn(max = if (state.navigating) 380.dp else 560.dp)
                    else -> Modifier.fillMaxWidth()
                },
            )
            .padding(top = if (state.navigating && !sideBySide) 100.dp else 12.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
    ) {
        app.kiloquest.ui.ToiletRequestCard(state.toiletRequest, vm::toiletAlarm, vm::dismissToiletRequest, Modifier.padding(bottom = 8.dp))
        app.kiloquest.ui.ToiletStatusCard(state.toilet, vm::resumeAfterToilet, Modifier.padding(bottom = 8.dp))
        if (!state.navigating) SearchPanel(state, vm)
        state.proposal?.let { ProposalCard(it, vm) }
        if (state.proposal == null) BreakCard(state, vm)
        if (state.route != null && !state.kidsScreen) WeatherCard(state, vm)
        if (state.proposal == null) HintBanner(state, vm)
        state.error?.let {
            app.kiloquest.ui.KiloCard(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                Text(stringResource(it))
            }
        }
    }

    if (state.navigating && overlays) {
        val g = state.guidance
        val route = state.route
        if (g != null && route != null) {
            ManeuverCard(
                route.maneuvers.getOrNull(g.nextManeuverIndex),
                g.metersToNextManeuver,
                Modifier.align(Alignment.TopStart).padding(12.dp).then(if (win.wide) Modifier.widthIn(max = 380.dp) else Modifier.fillMaxWidth()),
            )
        }
        // Quests-Knopf und Klo-Alarm: schmal und hoch in der Mitte rechts, sonst oben rechts
        androidx.compose.foundation.layout.Column(
            Modifier.align(if (!win.wide && !win.landscape) Alignment.CenterEnd else Alignment.TopEnd).padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuestsPill({ vm.showKids(true) }, onLongClick = { vm.showParent(true) })
            app.kiloquest.ui.ToiletAlarmButton(vm::toiletAlarm, active = state.toilet?.phase.let { it != null && it != ToiletPhase.NONE })
        }
        state.lanes?.let { LaneStrip(it, Modifier.align(Alignment.BottomCenter).padding(bottom = if (win.shortHeight) 76.dp else 100.dp)) }
        SpeedSign(
            limit = state.speedLimit,
            speedKmh = state.speedKmh,
            overspeed = state.overspeed,
            // Unten links ueber der Fahrleiste: kollidiert weder mit den Karten oben noch mit den Spuren in der Mitte
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = if (win.shortHeight) 76.dp else 100.dp),
        )
    }

    if (state.kidsScreen) KidsScreen(state, vm)
    if (state.tripScreen) TripScreen(state, vm)
    if (state.riddleScreen) RiddleScreen(state, vm)
    if (state.parentScreen) ParentScreen(state, vm)
    // Gekoppeltes Geraet: Zustand vom Hauptgeraet
    val linkApp = LocalContext.current.applicationContext as? KiloQuestApp
    if (linkApp != null) {
        val client by linkApp.linkClient.state.collectAsState()
        if (client.status == app.kiloquest.link.ClientStatus.CONNECTED && !state.parentScreen) {
            val snap = client.snapshot
            if (client.role == app.kiloquest.link.Role.COPILOT) {
                app.kiloquest.link.CopilotRemoteScreen(snap) { vm.showParent(true) }
            } else if (snap != null) {
                KidsScreen(snap.toUiStateFor(state.ageBand, state.avatar, state.realMap, client.route, client.pos), app.kiloquest.link.RemoteActions(linkApp.linkClient, vm))
            }
        }
    }

    val route = state.route
    if (route != null && overlays) {
        if (state.navigating) {
            val g = state.guidance
            val remaining = g?.remainingMeters ?: route.lengthMeters
            val seconds = route.durationSeconds * (remaining / route.lengthMeters.coerceAtLeast(1.0))
            EtaBar(
                remaining, seconds, vm::stopNavigation,
                Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth().padding(bottom = if (win.shortHeight) 8.dp else 24.dp, start = 12.dp, end = 12.dp),
            )
        } else {
            app.kiloquest.ui.RoutePreviewSheet(
                distanceMeters = route.lengthMeters,
                durationSeconds = route.durationSeconds,
                onStart = vm::startNavigation,
                onQuests = { vm.showKids(true) },
                onQuestsLong = { vm.showParent(true) },
                floating = win.wide,
                modifier = if (win.wide) Modifier.align(Alignment.BottomStart).widthIn(max = 460.dp).fillMaxWidth().padding(12.dp)
                else Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                extra = { PackageLine(state.pkg, vm) },
            )
        }
    } else if (overlays) {
        Text(
            text = stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
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

/** Genau ein Hinweis (wichtigster zuerst, "+N" fuer weitere); kurz vor Manoevern still. */
@Composable
private fun HintBanner(state: UiState, vm: NavController) {
    val ctx = LocalContext.current
    val hint = app.kiloquest.ui.selectHint(state, vm) ?: return
    val colors = app.kiloquest.ui.KiloTheme.colors
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = colors.hintContainer,
        contentColor = colors.onHintContainer,
        shadowElevation = 4.dp,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_hint_warning), contentDescription = null)
            Text(app.kiloquest.ui.hintText(ctx, hint), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (hint.more > 0) Text(stringResource(R.string.hint_more, hint.more), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun conditionEmoji(c: app.kiloquest.nav.WeatherCondition): String = when (c) {
    app.kiloquest.nav.WeatherCondition.CLEAR -> "☀️"
    app.kiloquest.nav.WeatherCondition.CLOUDY -> "⛅"
    app.kiloquest.nav.WeatherCondition.FOG -> "🌫️"
    app.kiloquest.nav.WeatherCondition.DRIZZLE, app.kiloquest.nav.WeatherCondition.SHOWERS -> "🌦️"
    app.kiloquest.nav.WeatherCondition.RAIN -> "🌧️"
    app.kiloquest.nav.WeatherCondition.SNOW -> "❄️"
    app.kiloquest.nav.WeatherCondition.STORM -> "⛈️"
    app.kiloquest.nav.WeatherCondition.UNKNOWN -> ""
}

/** Wetter am Ziel, Gefahren auf der Strecke, Ankunft im Dunkeln. */
@Composable
private fun WeatherCard(state: UiState, vm: NavController) {
    val w = state.weather ?: return
    val dest = w.destination
    val along = state.guidance?.alongMeters ?: 0.0
    app.kiloquest.ui.KiloCard(Modifier.padding(top = 8.dp)) {
        run {
            if (dest?.tempC != null) {
                Text(
                    stringResource(R.string.weather_dest, conditionEmoji(dest.condition), vm.conditionName(dest.condition), dest.tempC!!.toInt()),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            w.hazardsAhead.take(2).forEach { (hazard, at) ->
                Text(
                    stringResource(R.string.weather_hazard_line, vm.hazardName(hazard), formatDistance((at - along).coerceAtLeast(0.0))),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (w.arrivesInDark == true) Text(stringResource(R.string.weather_dark))
        }
    }
}

/** Offline-Reise-Paket: Knopf mit Groessenschaetzung, Fortschritt, Bereit-Hinweis. */
@Composable
internal fun PackageLine(pkg: PackageState, vm: NavController) {
    when (pkg.phase) {
        PackagePhase.IDLE -> androidx.compose.material3.TextButton(onClick = vm::downloadPackage) {
            Text(stringResource(R.string.pkg_button, pkg.estimatedMb))
        }
        PackagePhase.MAP, PackagePhase.LIMITS, PackagePhase.COUNTRIES -> Text(
            stringResource(
                R.string.pkg_progress,
                stringResource(
                    when (pkg.phase) {
                        PackagePhase.MAP -> R.string.pkg_phase_map
                        PackagePhase.LIMITS -> R.string.pkg_phase_limits
                        else -> R.string.pkg_phase_countries
                    },
                ),
                (pkg.progress * 100).toInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        PackagePhase.READY -> Text(stringResource(R.string.pkg_ready), style = MaterialTheme.typography.bodySmall)
        PackagePhase.ERROR -> androidx.compose.material3.TextButton(onClick = vm::downloadPackage) {
            Text(stringResource(R.string.pkg_error), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BreakCard(state: UiState, vm: NavController) {
    val ctx = LocalContext.current
    val proposal = state.breakProposal
    val target = state.breakTarget
    if (proposal != null) {
        app.kiloquest.ui.KiloCard(Modifier.padding(top = 8.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
            run {
                Text(stringResource(R.string.break_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.break_banner, formatDistance(proposal.aheadMeters), vm.stopText(proposal.stop)))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::acceptBreak) { Text(stringResource(R.string.break_accept)) }
                    androidx.compose.material3.OutlinedButton(onClick = vm::laterBreak) { Text(stringResource(R.string.break_later)) }
                }
            }
        }
    } else if (target != null) {
        app.kiloquest.ui.KiloCard(Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.break_banner, formatDistance(state.breakAheadMeters.coerceAtLeast(0.0)), vm.stopText(target)), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun ProposalCard(p: RerouteProposal, vm: NavController) {
    app.kiloquest.ui.KiloCard(Modifier.padding(top = 8.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        run {
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
    app.kiloquest.ui.KiloCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.search(query) }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { vm.search(query) }, enabled = !state.busy, modifier = Modifier.heightIn(min = 52.dp)) {
                Text(stringResource(R.string.search_action))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilledTonalButton(onClick = { vm.showTrip(true) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.trip_open), maxLines = 1)
            }
            androidx.compose.material3.FilledTonalButton(onClick = { vm.showParent(true) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.parent_open), maxLines = 1)
            }
        }
        val list = if (state.results.isEmpty() && query.isBlank()) state.recents else state.results
        list.forEachIndexed { i, place ->
            if (i > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clickable { vm.selectPlace(place) }.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_place), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(place.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
private fun MapScreen(route: List<LatLon>?, position: LatLon?, follow: Boolean, incidents: List<LatLon>, avatar: String, bearing: Float?) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            // Die Karte darf den Fokus nicht an sich ziehen, sonst laesst sich das Suchfeld nicht aktivieren
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
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
                    // Eigene Position als gewaehlte Figur (Bild wird unten gesetzt)
                    style.addSource(GeoJsonSource(AVATAR_SOURCE))
                    style.addLayer(
                        org.maplibre.android.style.layers.SymbolLayer(AVATAR_LAYER, AVATAR_SOURCE).withProperties(
                            PropertyFactory.iconImage(AVATAR_IMAGE),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconSize(0.5f),
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

    // Figur als Bild (Emoji der Emoji-Schrift) und Position
    LaunchedEffect(avatar, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        m.getStyle { style: Style ->
            runCatching { style.removeImage(AVATAR_IMAGE); style.removeImage(AVATAR_IMAGE_FLIP) }
            style.addImage(AVATAR_IMAGE, app.kiloquest.ui.emojiBitmap(avatar, 128))
            style.addImage(AVATAR_IMAGE_FLIP, app.kiloquest.ui.emojiBitmap(avatar, 128, mirrored = true))
        }
    }
    // Die Figur dreht sich mit der Fahrtrichtung (Flugzeug/Rakete) bzw. schaut in Fahrtrichtung (Fahrzeuge in Seitenansicht)
    LaunchedEffect(avatar, bearing, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val pose = app.kiloquest.game.Avatars.pose(avatar, bearing)
        m.getStyle { style: Style ->
            style.getLayerAs<org.maplibre.android.style.layers.SymbolLayer>(AVATAR_LAYER)?.setProperties(
                PropertyFactory.iconImage(if (pose.mirrored) AVATAR_IMAGE_FLIP else AVATAR_IMAGE),
                PropertyFactory.iconRotate(pose.rotationDegrees),
                PropertyFactory.iconRotationAlignment(org.maplibre.android.style.layers.Property.ICON_ROTATION_ALIGNMENT_MAP),
            )
        }
    }
    LaunchedEffect(position, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        m.getStyle { style: Style ->
            val src = style.getSourceAs<GeoJsonSource>(AVATAR_SOURCE) ?: return@getStyle
            if (position == null) src.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(emptyList()))
            else src.setGeoJson(Feature.fromGeometry(Point.fromLngLat(position.lon, position.lat)))
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
