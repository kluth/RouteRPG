package app.kiloquest.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.kiloquest.UiState
import app.kiloquest.game.Avatars
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val SRC_DONE = "kid-route-done"
private const val SRC_TODO = "kid-route-todo"
private const val SRC_DEST = "kid-dest"
private const val IMG_FLAG = "kid-flag-img"

/**
 * Echte Karte (OpenFreeMap-Kacheln, MapLibre) fuer Kinder: Route, Ziel-Flagge und die eigene Figur, die sich mit der
 * Fahrtrichtung dreht. Braucht Internet; schlaegt das Laden der Karte fehl, ruft sie [onFailed] auf (dann zeigt die
 * Oberflaeche die schematische Karte). Nord ist oben, die Figur zeigt die Fahrtrichtung.
 */
@Composable
fun KidTileMap(state: UiState, mode: MapMode, modifier: Modifier = Modifier, interactive: Boolean = false, onFailed: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark = isSystemInDarkTheme()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    val currentMode by androidx.compose.runtime.rememberUpdatedState(mode)
    // Animierte 3D-Figur; die Kamera faehrt in der Nah-Ansicht mit jeder weichen Zwischenposition mit
    val marker = remember { AvatarMarker(context, "kid") { ll -> if (currentMode == MapMode.FOLLOW) map?.moveCamera(CameraUpdateFactory.newLatLng(ll)) } }

    val mapView = remember {
        MapView(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            addOnDidFailLoadingMapListener { onFailed() }
            getMapAsync { m ->
                map = m
                m.uiSettings.isRotateGesturesEnabled = false
                m.uiSettings.isTiltGesturesEnabled = false
                m.setStyle(if (dark) DARK_STYLE else LIGHT_STYLE) { style ->
                    style.addSource(GeoJsonSource(SRC_DONE))
                    style.addSource(GeoJsonSource(SRC_TODO))
                    style.addSource(GeoJsonSource(SRC_DEST))
                    style.addLayer(LineLayer("kid-done-line", SRC_DONE).withProperties(PropertyFactory.lineColor("#9DB7E8"), PropertyFactory.lineWidth(7f), PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")))
                    style.addLayer(LineLayer("kid-todo-casing", SRC_TODO).withProperties(PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(11f), PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")))
                    style.addLayer(LineLayer("kid-todo-line", SRC_TODO).withProperties(PropertyFactory.lineColor("#0B57D0"), PropertyFactory.lineWidth(7f), PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")))
                    style.addLayer(SymbolLayer("kid-dest-layer", SRC_DEST).withProperties(PropertyFactory.iconImage(IMG_FLAG), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true), PropertyFactory.iconSize(0.5f), PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)))
                    marker.install(style, state.avatar)
                    style.addImage(IMG_FLAG, emojiBitmap("🏁", 128, disc = false))
                    styleReady = true
                }
            }
        }
    }

    // Gesten nur im Vollbild und in der Gesamtansicht (in der Naeh-Ansicht folgt die Karte dem Auto)
    LaunchedEffect(map, interactive, mode) {
        map?.uiSettings?.setAllGesturesEnabled(interactive && mode == MapMode.OVERVIEW)
    }

    // Figur wechseln
    LaunchedEffect(state.avatar, styleReady) {
        if (styleReady) marker.setAvatar(state.avatar)
    }

    // Position und Fahrtrichtung: gleitet weich, Figur dreht sich bzw. schaut in Fahrtrichtung
    LaunchedEffect(state.position, state.bearing, styleReady) {
        if (!styleReady) return@LaunchedEffect
        val p = state.position
        if (p == null) marker.clear() else marker.update(p.lat, p.lon, state.bearing)
    }

    // Route: zurueckgelegt (blass) und noch vor uns, dazu das Ziel
    LaunchedEffect(state.route, state.guidance?.alongMeters, state.destination, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val route = state.route
        val along = (state.guidance?.alongMeters ?: 0.0).coerceIn(0.0, route?.line?.lengthMeters ?: 0.0)
        m.getStyle { style: Style ->
            fun line(pts: List<app.kiloquest.nav.LatLon>): FeatureCollection =
                FeatureCollection.fromFeatures(
                    if (pts.size < 2) emptyList() else listOf(Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.lon, it.lat) }))),
                )
            if (route == null) {
                listOf(SRC_DONE, SRC_TODO, SRC_DEST).forEach { style.getSourceAs<GeoJsonSource>(it)?.setGeoJson(FeatureCollection.fromFeatures(emptyList())) }
            } else {
                style.getSourceAs<GeoJsonSource>(SRC_DONE)?.setGeoJson(line(route.line.slice(0.0, along, 600)))
                style.getSourceAs<GeoJsonSource>(SRC_TODO)?.setGeoJson(line(route.line.slice(along, route.line.lengthMeters, 600)))
                val d = state.destination?.position ?: route.line.points.last()
                style.getSourceAs<GeoJsonSource>(SRC_DEST)?.setGeoJson(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(d.lon, d.lat)))))
            }
        }
    }

    // Kamera: Naeh-Ansicht gekippt (3D, Gebaeude erheben sich), Gesamtansicht flach von oben mit der ganzen Route
    LaunchedEffect(mode, state.route, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val route = state.route
        val p = state.position
        if (mode == MapMode.OVERVIEW && route != null) {
            val pts = route.line.slice(0.0, route.line.lengthMeters, 300).map { LatLng(it.lat, it.lon) }
            m.moveCamera(CameraUpdateFactory.tiltTo(0.0))
            if (pts.size >= 2) m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(pts).build(), 80))
        } else if (p != null) {
            m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    org.maplibre.android.camera.CameraPosition.Builder().target(LatLng(p.lat, p.lon)).zoom(15.0).tilt(55.0).bearing(0.0).build(),
                ),
            )
        }
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
            marker.release()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
