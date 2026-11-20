package app.kiloquest.car

import android.app.Presentation
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import app.kiloquest.NavController
import app.kiloquest.R
import app.kiloquest.UiState
import app.kiloquest.nav.IncidentType
import app.kiloquest.nav.LatLon
import app.kiloquest.nav.SpeedProfile
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.ln
import kotlin.math.max

private const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
private const val LIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val ROUTE_AHEAD_METERS = 80_000.0

/**
 * Echte Karte (MapLibre) fuer das Auto-Display: Die Karte wird in einem Presentation-Fenster auf einem
 * VirtualDisplay gerendert, dessen Oberflaeche das Auto bereitstellt. Darueber liegen Tempolimit, Spuren und
 * Hinweise als Views. Schlaegt das Einrichten fehl, wird auf die einfache Zeichnung ([CarMapRenderer]) zurueckgegriffen.
 */
class CarMapView(private val carContext: CarContext, private val nav: NavController) {
    private var fallback: CarMapRenderer? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var root: FrameLayout? = null
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var styleReady = false
    private var visible = Rect()
    private var surfaceW = 0
    private var surfaceH = 0
    private var density = 1f
    private var following = true
    private var zoomOffset = 0.0

    // Ueberlagerungen
    private var speedSign: TextView? = null
    private var speedNow: TextView? = null
    private var lanes: LinearLayout? = null
    private var banner: TextView? = null

    val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            create(surfaceContainer)
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            fallback?.surfaceCallback?.onSurfaceDestroyed(surfaceContainer)
            destroy()
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            visible = visibleArea
            fallback?.surfaceCallback?.onVisibleAreaChanged(visibleArea)
            layoutHud()
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            val m = map ?: return
            following = false
            // Kartenmitte um die Fingerbewegung (in Pixeln) verschieben
            val proj = m.projection
            val center = m.cameraPosition.target ?: return
            val px = proj.toScreenLocation(center)
            m.moveCamera(CameraUpdateFactory.newLatLng(proj.fromScreenLocation(android.graphics.PointF(px.x + distanceX, px.y + distanceY))))
        }

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
            val m = map ?: return
            following = false
            m.moveCamera(CameraUpdateFactory.zoomBy(ln(scaleFactor.toDouble()) / ln(2.0)))
        }
    }

    // ------------------------------------------------------------------ Aktionen aus der Kartenleiste

    fun zoomIn() { zoomOffset += 1.0; recenterCamera() }
    fun zoomOut() { zoomOffset -= 1.0; recenterCamera() }
    fun recenter() { following = true; zoomOffset = 0.0; recenterCamera() }
    private fun recenterCamera() = update() // Aktionen aus der Kartenleiste laufen auf dem Hauptthread

    // ------------------------------------------------------------------ Aufbau

    private fun create(c: SurfaceContainer) {
        surfaceW = c.width
        surfaceH = c.height
        density = c.dpi / 160f
        if (nav.state.value.carSimpleMap) {
            fallback = CarMapRenderer(nav).also { it.surfaceCallback.onSurfaceAvailable(c) }
            return
        }
        try {
            val dm = carContext.getSystemService(DisplayManager::class.java)
            val vd = dm.createVirtualDisplay(
                "kiloquest-car-map", c.width, c.height, c.dpi, c.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            )
            virtualDisplay = vd
            val pres = Presentation(carContext, vd.display)
            presentation = pres
            buildViews(pres)
            pres.setContentView(root!!, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            pres.show()
            mapView!!.apply {
                onCreate(null)
                onStart()
                onResume()
                getMapAsync { m ->
                    map = m
                    loadStyle(m)
                }
            }
            layoutHud()
        } catch (t: Throwable) {
            destroy()
            fallback = CarMapRenderer(nav).also { it.surfaceCallback.onSurfaceAvailable(c) }
        }
    }

    private fun dp(v: Int) = (v * density).toInt()

    private fun buildViews(pres: Presentation) {
        val ctx = pres.context
        val frame = FrameLayout(ctx)
        val mv = MapView(ctx).apply {
            isFocusable = false
            isFocusableInTouchMode = false
        }
        frame.addView(mv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        speedSign = TextView(ctx).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 26f
            visibility = ViewGroup.GONE
        }
        speedNow = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(170, 20, 20, 20))
            setPadding(dp(8), dp(2), dp(8), dp(2))
            textSize = 15f
            visibility = ViewGroup.GONE
        }
        lanes = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = ViewGroup.GONE
        }
        banner = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 30, 30, 30))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            textSize = 17f
            visibility = ViewGroup.GONE
        }
        frame.addView(speedSign, FrameLayout.LayoutParams(dp(72), dp(72)))
        frame.addView(speedNow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(lanes, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(banner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root = frame
        mapView = mv
    }

    /** Ueberlagerungen im sichtbaren Bereich (das Auto verdeckt Raender mit eigenen Elementen) platzieren. */
    private fun layoutHud() {
        val r = root ?: return
        val left = visible.left + dp(12)
        val top = visible.top + dp(12)
        (speedSign?.layoutParams as? FrameLayout.LayoutParams)?.apply {
            gravity = Gravity.TOP or Gravity.START; leftMargin = left; topMargin = top + dp(44)
        }
        (speedNow?.layoutParams as? FrameLayout.LayoutParams)?.apply {
            gravity = Gravity.TOP or Gravity.START; leftMargin = left; topMargin = top + dp(44) + dp(76)
        }
        (lanes?.layoutParams as? FrameLayout.LayoutParams)?.apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = if (surfaceH > 0 && !visible.isEmpty) (surfaceH - visible.bottom) + dp(12) else dp(12)
        }
        (banner?.layoutParams as? FrameLayout.LayoutParams)?.apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = top
        }
        r.requestLayout()
    }

    private fun loadStyle(m: MapLibreMap) {
        val url = if (carContext.isDarkMode) DARK_STYLE_URL else LIGHT_STYLE_URL
        val dark = carContext.isDarkMode
        m.setStyle(url) { style ->
            style.addSource(GeoJsonSource("route"))
            style.addLayer(
                LineLayer("route-casing", "route").withProperties(
                    PropertyFactory.lineColor(if (dark) "#0D47A1" else "#0B3D91"), PropertyFactory.lineWidth(13f),
                    PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
                ),
            )
            style.addLayer(
                LineLayer("route-line", "route").withProperties(
                    PropertyFactory.lineColor("#4FC3F7"), PropertyFactory.lineWidth(8f),
                    PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
                ),
            )
            addCircles(style, "incidents", "#D32F2F", 10f)
            addCircles(style, "dest", "#FB8C00", 12f)
            addCircles(style, "break", "#43A047", 12f)
            style.addSource(GeoJsonSource("pos"))
            style.addLayer(CircleLayer("pos-outer", "pos").withProperties(PropertyFactory.circleColor("#FFFFFF"), PropertyFactory.circleRadius(13f)))
            style.addLayer(CircleLayer("pos-inner", "pos").withProperties(PropertyFactory.circleColor("#1E88E5"), PropertyFactory.circleRadius(9f)))
            styleReady = true
            update()
        }
    }

    private fun addCircles(style: Style, id: String, color: String, radius: Float) {
        style.addSource(GeoJsonSource(id))
        style.addLayer(
            CircleLayer("$id-layer", id).withProperties(
                PropertyFactory.circleColor(color), PropertyFactory.circleRadius(radius),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2.5f),
            ),
        )
    }

    // ------------------------------------------------------------------ Aktualisierung

    /** Bei jeder Zustandsaenderung aufrufen (Hauptthread). */
    fun update() {
        fallback?.redraw()
        val m = map ?: return
        if (!styleReady) return
        val st = nav.state.value
        m.getStyle { style -> updateSources(style, st) }
        updateCamera(m, st)
        updateHud(st)
    }

    private fun updateSources(style: Style, st: UiState) {
        fun pointsOf(vararg p: LatLon?) = FeatureCollection.fromFeatures(p.filterNotNull().map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })
        val route = st.route
        val g = st.guidance
        val line = when {
            route == null -> emptyList()
            st.navigating && g != null -> route.line.slice(g.alongMeters - 500.0, g.alongMeters + ROUTE_AHEAD_METERS, 1500)
            else -> route.line.slice(0.0, route.line.lengthMeters, 2000)
        }
        style.getSourceAs<GeoJsonSource>("route")?.setGeoJson(
            FeatureCollection.fromFeatures(
                if (line.size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(line.map { Point.fromLngLat(it.lon, it.lat) })))
                else emptyList(),
            ),
        )
        val along = g?.alongMeters ?: 0.0
        style.getSourceAs<GeoJsonSource>("incidents")?.setGeoJson(
            FeatureCollection.fromFeatures(
                st.incidents.filter { it.aheadMeters in -500.0..ROUTE_AHEAD_METERS }
                    .map { Feature.fromGeometry(Point.fromLngLat(it.incident.position.lon, it.incident.position.lat)) },
            ),
        )
        style.getSourceAs<GeoJsonSource>("dest")?.setGeoJson(pointsOf(if (route != null) st.destination?.position else null))
        style.getSourceAs<GeoJsonSource>("break")?.setGeoJson(pointsOf(st.breakTarget?.position))
        style.getSourceAs<GeoJsonSource>("pos")?.setGeoJson(pointsOf(st.position))
        // along bleibt fuer spaetere Erweiterungen (z. B. Entfernungsfilter) verfuegbar
        check(along >= 0.0)
    }

    private fun zoomForSpeed(kmh: Int?): Double = when {
        kmh == null || kmh < 30 -> 17.0
        kmh < 60 -> 16.0
        kmh < 100 -> 15.0
        else -> 14.0
    }

    private fun updateCamera(m: MapLibreMap, st: UiState) {
        if (!following) return
        val pos = st.position
        val route = st.route
        if (st.navigating && pos != null) {
            // Eigene Position etwas unter die Mitte setzen, damit mehr Strecke voraus sichtbar ist
            m.setPadding(0, (surfaceH * 0.30).toInt(), 0, 0)
            val cp = CameraPosition.Builder()
                .target(LatLng(pos.lat, pos.lon))
                .zoom((zoomForSpeed(st.speedKmh) + zoomOffset).coerceIn(5.0, 19.0))
                .bearing((st.bearing ?: 0f).toDouble())
                .tilt(45.0)
                .build()
            m.animateCamera(CameraUpdateFactory.newCameraPosition(cp), 900)
        } else if (route != null) {
            m.setPadding(0, 0, 0, 0)
            val pts = route.line.slice(0.0, route.line.lengthMeters, 300)
            val b = LatLngBounds.Builder().includes(pts.map { LatLng(it.lat, it.lon) }).build()
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(b, dp(60)), 900)
        } else if (pos != null) {
            m.setPadding(0, 0, 0, 0)
            m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(LatLng(pos.lat, pos.lon)).zoom(14.0 + zoomOffset).tilt(0.0).bearing(0.0).build(),
                ),
                900,
            )
        }
    }

    private fun updateHud(st: UiState) {
        // Tempolimit-Schild
        val limit = st.speedLimit
        val sign = speedSign
        if (sign != null) {
            if (st.navigating && limit != null) {
                val unlimited = limit >= SpeedProfile.UNLIMITED
                sign.text = if (unlimited) "∞" else limit.toString()
                sign.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (st.overspeed) Color.rgb(255, 205, 210) else Color.WHITE)
                    setStroke(dp(6), if (unlimited) Color.GRAY else Color.rgb(211, 47, 47))
                }
                sign.visibility = ViewGroup.VISIBLE
            } else sign.visibility = ViewGroup.GONE
        }
        speedNow?.apply {
            val kmh = st.speedKmh
            if (st.navigating && kmh != null) {
                text = carContext.getString(R.string.speed_kmh, kmh)
                setTextColor(if (st.overspeed) Color.rgb(255, 138, 128) else Color.WHITE)
                visibility = ViewGroup.VISIBLE
            } else visibility = ViewGroup.GONE
        }
        // Spurpfeile
        val ln = lanes
        if (ln != null) {
            val g = st.lanes
            if (st.navigating && g != null) {
                ln.removeAllViews()
                for (lane in g.lanes) {
                    ln.addView(
                        TextView(ln.context).apply {
                            text = lane.symbol
                            textSize = 30f
                            gravity = Gravity.CENTER
                            setTextColor(if (lane.valid) Color.WHITE else Color.argb(170, 170, 170, 170))
                            typeface = if (lane.valid) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                            setBackgroundColor(if (lane.valid) Color.argb(220, 21, 101, 192) else Color.argb(170, 40, 40, 40))
                            setPadding(dp(12), dp(6), dp(12), dp(6))
                        },
                        LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(4) },
                    )
                }
                ln.visibility = ViewGroup.VISIBLE
            } else ln.visibility = ViewGroup.GONE
        }
        // Wichtigster Hinweis oben
        banner?.apply {
            val msg = bannerText(st)
            if (msg != null) { text = msg; visibility = ViewGroup.VISIBLE } else visibility = ViewGroup.GONE
        }
    }

    private fun distance(meters: Double): String =
        if (meters >= 1000) carContext.getString(R.string.unit_km, meters / 1000.0) else carContext.getString(R.string.unit_m, meters.toInt().coerceAtLeast(0))

    /** Erste zutreffende Meldung: Verkehr voraus, Wetter-Gefahr, angenommene Pause. */
    private fun bannerText(st: UiState): String? {
        if (!st.navigating) return null
        val ri = st.incidents.firstOrNull { it.aheadMeters >= -1.0 && (it.incident.blocked || (it.incident.delayMinutes ?: 0) >= 5) }
        if (ri != null) {
            val type = carContext.getString(
                when (ri.incident.type) {
                    IncidentType.JAM -> R.string.incident_jam
                    IncidentType.ACCIDENT -> R.string.incident_accident
                    IncidentType.CLOSURE -> R.string.incident_closure
                    IncidentType.HAZARD -> R.string.incident_hazard
                },
            )
            return carContext.getString(R.string.traffic_banner, type, distance(ri.aheadMeters), ri.incident.road)
        }
        val along = st.guidance?.alongMeters ?: 0.0
        val hazard = st.weather?.hazardsAhead?.firstOrNull { it.second - along in 0.0..150_000.0 }
        if (hazard != null) {
            return carContext.getString(R.string.weather_hazard_line, nav.hazardName(hazard.first), distance(hazard.second - along))
        }
        val target = st.breakTarget
        if (target != null) return carContext.getString(R.string.break_banner, distance(max(0.0, st.breakAheadMeters)), nav.stopText(target))
        return null
    }

    // ------------------------------------------------------------------ Abbau

    private fun destroy() {
        runCatching {
            mapView?.apply { onPause(); onStop(); onDestroy() }
            presentation?.dismiss()
            virtualDisplay?.release()
        }
        map = null
        styleReady = false
        mapView = null
        root = null
        presentation = null
        virtualDisplay = null
        speedSign = null; speedNow = null; lanes = null; banner = null
    }
}
