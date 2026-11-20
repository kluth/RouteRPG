package app.kiloquest.ui

import android.animation.ValueAnimator
import android.content.Context
import android.view.animation.LinearInterpolator
import app.kiloquest.game.Avatars
import app.kiloquest.game.ModelKind
import app.kiloquest.nav.LatLon
import app.kiloquest.nav.haversine
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Eigene Position auf einer MapLibre-Karte als animierte Figur in 3D-Optik:
 * - Fahrzeuge mit 3D-Modell ([Figures3D]) zeigen das passende von 36 Drehbildern der Fahrtrichtung, alle anderen Figuren
 *   sind Emoji ([Avatars.pose]); es gibt keinen Untergrund hinter der Figur,
 * - gleitet weich von Position zu Position und dreht sich weich in die neue Richtung,
 * - Fahrzeuge federn leicht (stärker bei Tempo), neigen sich in Kurven, Raumschiffe und Emoji schweben,
 * - ein Schatten liegt auf dem Boden (bei gekippter Karte eine flache Ellipse), ein Ring breitet sich aus,
 * - bei Fahrt bleibt eine Spur zurueck (Staub, Schaum, Rauch oder Flamme je nach Fahrzeug).
 * Laeuft auf dem Hauptthread (ValueAnimator); [release] beendet die Animationen. [onMoved] meldet jede Zwischenposition.
 */
class AvatarMarker(private val ctx: Context, private val prefix: String, private val onMoved: (LatLng) -> Unit = {}) {
    private val src = "$prefix-src"
    private val puffSrc = "$prefix-puff-src"
    private val puffId = "$prefix-puff"
    private val shadowId = "$prefix-shadow"
    private val ringId = "$prefix-ring"
    private val iconId = "$prefix-icon"
    private val imgPrefix = "$prefix-img"

    private var style: Style? = null
    private var source: GeoJsonSource? = null
    private var puffSource: GeoJsonSource? = null
    private var puffLayer: CircleLayer? = null
    private var shadow: CircleLayer? = null
    private var ring: CircleLayer? = null
    private var icon: SymbolLayer? = null

    private var emoji = Avatars.DEFAULT
    private var sheet: FigureSheet? = null   // 3D-Drehbilder, sonst Emoji
    private var walkFrame = 0
    private var walkPhase = 0f
    private var lastTickNs = 0L
    private var flier = false           // schwebt statt zu federn
    private var lat = 0.0
    private var lon = 0.0
    private var bearing: Float? = null
    private var hasPos = false
    private var lastImage: String? = null
    private var lastRotation = Float.NaN
    private var addedImages = emptyList<String>()

    // Tempo und Kurvenlage aus der Bewegung zwischen zwei Fixes
    private var lastFixAt = 0L
    private var speedMps = 0.0
    private var roll = 0f
    private var puffs = ArrayDeque<Puff>()
    private var lastPuffAt = 0L

    private class Puff(val lat: Double, val lon: Double, val born: Long)

    private var moveAnim: ValueAnimator? = null
    private var idleAnim: ValueAnimator? = null
    private var frame = 0

    /** Quellen und Ebenen einrichten (nach dem Laden des Stils). */
    fun install(style: Style, avatar: String) {
        release()
        this.style = style
        style.addSource(GeoJsonSource(src))
        style.addSource(GeoJsonSource(puffSrc))
        val pf = CircleLayer(puffId, puffSrc).withProperties(
            PropertyFactory.circleColor("#CFC6B4"), PropertyFactory.circleRadius(Expression.get("r")), PropertyFactory.circleOpacity(Expression.get("a")),
            PropertyFactory.circleBlur(0.6f), PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
        )
        val sh = CircleLayer(shadowId, src).withProperties(
            PropertyFactory.circleColor("#000000"), PropertyFactory.circleOpacity(0.25f), PropertyFactory.circleRadius(13f),
            PropertyFactory.circleBlur(0.9f), PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
        )
        val rg = CircleLayer(ringId, src).withProperties(
            PropertyFactory.circleColor("#0B57D0"), PropertyFactory.circleOpacity(0f), PropertyFactory.circleRadius(14f),
            PropertyFactory.circleStrokeColor("#0B57D0"), PropertyFactory.circleStrokeWidth(3f), PropertyFactory.circleStrokeOpacity(0.5f),
            PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
        )
        val ic = SymbolLayer(iconId, src).withProperties(
            PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
            PropertyFactory.iconTranslate(arrayOf(0f, -26f)),
        )
        style.addLayer(pf); style.addLayer(sh); style.addLayer(rg); style.addLayer(ic)
        source = style.getSourceAs(src); puffSource = style.getSourceAs(puffSrc)
        puffLayer = pf; shadow = sh; ring = rg; icon = ic
        setAvatar(avatar)
        pushPosition()
        startIdle()
    }

    fun setAvatar(newEmoji: String) {
        emoji = newEmoji
        val st = style ?: return
        runCatching { addedImages.forEach { st.removeImage(it) } }
        val sh = Figures3D.sheet(ctx, newEmoji)
        sheet = sh
        val names = ArrayList<String>()
        if (sh != null) {
            val map = HashMap<String, android.graphics.Bitmap>()
            sh.all().forEachIndexed { h, row -> row.forEachIndexed { w, b -> val n = "$imgPrefix-f$h-$w"; map[n] = b; names += n } }
            st.addImages(map)
        } else {
            st.addImage("$imgPrefix-e", emojiBitmap(newEmoji, 128)); names += "$imgPrefix-e"
            st.addImage("$imgPrefix-e-flip", emojiBitmap(newEmoji, 128, mirrored = true)); names += "$imgPrefix-e-flip"
        }
        addedImages = names
        val id = Avatars.modelOf(newEmoji)
        flier = id == "rocket" || id == "ufo" || (sh == null && Avatars.orientOf(newEmoji) != app.kiloquest.game.Orient.SIDE_LEFT)
        // Fahrzeug-Bilder sind 192 px, Gehfiguren 128 px: gleiche Bildschirmgroesse
        icon?.setProperties(PropertyFactory.iconSize(when { sh == null -> 0.7f; sh.model.kind == ModelKind.WALKER -> 0.65f; else -> 0.5f }))
        puffLayer?.setProperties(PropertyFactory.circleColor(puffColor(newEmoji)))
        lastImage = null; lastRotation = Float.NaN
        applyPose()
    }

    /** Staub, Schaum, Rauch oder Flamme je nach Fahrzeug; Tiere und Personen hinterlassen nur Staub. */
    private fun puffColor(e: String): String = when (Avatars.modelOf(e)) {
        "sailboat", "speedboat", "liner", "rowboat", "cargoship", "galleon" -> "#FFFFFF"
        "rocket", "ufo" -> "#FFC247"
        "train_bullet", "train_city", "train_city_b" -> "#E8E8E8"
        else -> if (Avatars.orientOf(e) == app.kiloquest.game.Orient.POINTER) "#FFFFFF" else "#CFC6B4"
    }

    /** Neue Position und Richtung: gleitet in 0,9 s dorthin (das passt zu einem GPS-Fix pro Sekunde). */
    fun update(newLat: Double, newLon: Double, newBearing: Float?) {
        val now = System.currentTimeMillis()
        if (!hasPos) {
            lat = newLat; lon = newLon; bearing = newBearing; hasPos = true; lastFixAt = now
            pushPosition(); applyPose(); onMoved(LatLng(lat, lon))
            return
        }
        // Tempo und Drehung aus dem Abstand zum vorigen Fix
        val dt = ((now - lastFixAt) / 1000.0).coerceAtLeast(0.2)
        speedMps = haversine(LatLon(lat, lon), LatLon(newLat, newLon)) / dt
        val turn = if (bearing != null && newBearing != null) (((newBearing - bearing!!) % 360f + 540f) % 360f - 180f) / dt.toFloat() else 0f
        roll = (turn * 0.5f).coerceIn(-12f, 12f)
        lastFixAt = now
        moveAnim?.cancel()
        val fromLat = lat; val fromLon = lon; val fromB = bearing
        val bearingDelta = if (fromB != null && newBearing != null) ((newBearing - fromB) % 360f + 540f) % 360f - 180f else 0f
        moveAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                lat = fromLat + (newLat - fromLat) * t
                lon = fromLon + (newLon - fromLon) * t
                bearing = if (fromB != null && newBearing != null) ((fromB + bearingDelta * t) % 360f + 360f) % 360f else newBearing ?: fromB
                pushPosition(); applyPose(); onMoved(LatLng(lat, lon))
            }
            start()
        }
    }

    fun clear() {
        hasPos = false
        source?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        puffs.clear()
    }

    private fun pushPosition() {
        if (!hasPos) return
        source?.setGeoJson(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat)))))
    }

    private fun applyPose() {
        val ic = icon ?: return
        val image: String
        val rotation: Float
        val sh = sheet
        if (sh != null) {
            image = "$imgPrefix-f${sh.heading(bearing)}-${walkFrame.mod(sh.frames)}"
            // Neigung in Kurven nur bei Fahrzeugen; Gehfiguren bleiben aufrecht
            rotation = if (sh.model.kind == ModelKind.VEHICLE) roll else 0f
        } else {
            val pose = Avatars.pose(emoji, bearing)
            image = if (pose.mirrored) "$imgPrefix-e-flip" else "$imgPrefix-e"
            rotation = pose.rotationDegrees
        }
        if (image == lastImage && rotation == lastRotation) return
        lastImage = image; lastRotation = rotation
        ic.setProperties(PropertyFactory.iconImage(image), PropertyFactory.iconRotate(rotation))
    }

    /** Dauerhafte Animation: Federn bzw. Schweben, Ring und Spur (etwa 30 Bilder pro Sekunde reichen). */
    private fun startIdle() {
        idleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                if (++frame % 2 != 0) return@addUpdateListener
                val t = a.animatedValue as Float
                val now = System.currentTimeMillis()
                val moving = speedMps > 1.0 && hasPos
                // Gehzyklus: schneller bei hoeherem Tempo, im Stand das erste Bild
                val sh = sheet
                if (sh != null && sh.frames > 1) {
                    val nowNs = System.nanoTime()
                    val dtS = if (lastTickNs == 0L) 0f else (nowNs - lastTickNs) / 1e9f
                    lastTickNs = nowNs
                    if (moving) {
                        val cycle = (0.95f - speedMps.toFloat() * 0.035f).coerceIn(0.3f, 0.95f)
                        walkPhase = (walkPhase + dtS / cycle) % 1f
                        walkFrame = (walkPhase * sh.frames).toInt() % sh.frames
                    } else { walkFrame = 0; walkPhase = 0f }
                    applyPose()
                }
                // Federn (schnell, klein) fuer Bodenfahrzeuge; Schweben (langsam, gross) fuer Raumschiffe und Emoji
                val walkerFig = sheet?.model?.kind == ModelKind.WALKER
                val bob = if (flier) 3.5f * sin(t * 2 * PI).toFloat()
                else if (walkerFig) 0.3f * sin(t * 2 * PI).toFloat()
                else (if (moving) min(1.8f, 0.5f + speedMps.toFloat() * 0.04f) else 0.35f) * sin(t * 2 * PI * 5).toFloat()
                ring?.setProperties(PropertyFactory.circleRadius(14f + 26f * t), PropertyFactory.circleStrokeOpacity(0.55f * (1f - t)))
                shadow?.setProperties(PropertyFactory.circleRadius(13f - 1.2f * bob), PropertyFactory.circleOpacity(0.25f - 0.03f * bob))
                icon?.setProperties(PropertyFactory.iconTranslate(arrayOf(0f, -26f - bob)))
                // Spur: alle ~110 ms ein Puff an der aktuellen Position, aelter als 0,9 s verschwindet
                if (moving && now - lastPuffAt > 110) { puffs.addLast(Puff(lat, lon, now)); lastPuffAt = now }
                while (puffs.isNotEmpty() && now - puffs.first().born > 900) puffs.removeFirst()
                if (puffs.isNotEmpty() || frame % 10 == 0) {
                    val feats = puffs.map { p ->
                        val age = ((now - p.born) / 900f).coerceIn(0f, 1f)
                        Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).also {
                            it.addNumberProperty("r", 3f + 10f * age)
                            it.addNumberProperty("a", 0.45f * (1f - age) * (1f - age))
                        }
                    }
                    puffSource?.setGeoJson(FeatureCollection.fromFeatures(feats))
                }
                // Tempo klingt ab, wenn keine neuen Fixes kommen (Stillstand)
                if (now - lastFixAt > 2500) { speedMps = 0.0; roll = 0f }
            }
            start()
        }
    }

    fun release() {
        moveAnim?.cancel(); moveAnim = null
        idleAnim?.cancel(); idleAnim = null
        style = null; source = null; puffSource = null; puffLayer = null; shadow = null; ring = null; icon = null
        lastImage = null; addedImages = emptyList(); puffs.clear(); sheet = null; lastTickNs = 0L
    }
}
