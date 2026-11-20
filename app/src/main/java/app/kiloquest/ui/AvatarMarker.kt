package app.kiloquest.ui

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import app.kiloquest.game.Avatars
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.PI
import kotlin.math.sin

/**
 * Eigene Position auf einer MapLibre-Karte als animierte Figur in 3D-Optik:
 * - gleitet weich von Position zu Position (statt zu springen) und dreht sich/schaut in Fahrtrichtung ([Avatars.pose]),
 * - schwebt leicht auf und ab ueber einem Schatten, der auf dem Boden liegt (bei gekippter Karte als flache Ellipse),
 * - ein Ring breitet sich am Boden aus und blendet aus.
 * Laeuft auf dem Hauptthread (ValueAnimator); [release] beendet die Animationen. [onMoved] meldet jede weiche Zwischenposition
 * (z. B. damit die Kamera mitfaehrt).
 */
class AvatarMarker(private val prefix: String, private val onMoved: (LatLng) -> Unit = {}) {
    private val src = "$prefix-src"
    private val shadowId = "$prefix-shadow"
    private val ringId = "$prefix-ring"
    private val iconId = "$prefix-icon"
    private val imgNormal = "$prefix-img"
    private val imgFlip = "$prefix-img-flip"

    private var style: Style? = null
    private var source: GeoJsonSource? = null
    private var shadow: CircleLayer? = null
    private var ring: CircleLayer? = null
    private var icon: SymbolLayer? = null

    private var emoji = Avatars.DEFAULT
    private var lat = 0.0
    private var lon = 0.0
    private var bearing: Float? = null
    private var hasPos = false
    private var lastImage: String? = null
    private var lastRotation = Float.NaN

    private var moveAnim: ValueAnimator? = null
    private var idleAnim: ValueAnimator? = null
    private var frame = 0

    /** Quellen und Ebenen einrichten (nach dem Laden des Stils). */
    fun install(style: Style, avatar: String) {
        release()
        this.style = style
        style.addSource(GeoJsonSource(src))
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
            PropertyFactory.iconImage(imgNormal), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(0.55f), PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconTranslate(arrayOf(0f, -28f)),
        )
        style.addLayer(sh); style.addLayer(rg); style.addLayer(ic)
        source = style.getSourceAs(src); shadow = sh; ring = rg; icon = ic
        setAvatar(avatar)
        pushPosition()
        startIdle()
    }

    fun setAvatar(newEmoji: String) {
        emoji = newEmoji
        val st = style ?: return
        runCatching { st.removeImage(imgNormal); st.removeImage(imgFlip) }
        st.addImage(imgNormal, emojiBitmap(newEmoji, 128))
        st.addImage(imgFlip, emojiBitmap(newEmoji, 128, mirrored = true))
        lastImage = null; lastRotation = Float.NaN
        applyPose()
    }

    /** Neue Position und Richtung: gleitet in 0,9 s dorthin (das passt zu einem GPS-Fix pro Sekunde). */
    fun update(newLat: Double, newLon: Double, newBearing: Float?) {
        if (!hasPos) {
            lat = newLat; lon = newLon; bearing = newBearing; hasPos = true
            pushPosition(); applyPose(); onMoved(LatLng(lat, lon))
            return
        }
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
    }

    private fun pushPosition() {
        if (!hasPos) return
        source?.setGeoJson(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat)))))
    }

    private fun applyPose() {
        val ic = icon ?: return
        val pose = Avatars.pose(emoji, bearing)
        val image = if (pose.mirrored) imgFlip else imgNormal
        if (image == lastImage && pose.rotationDegrees == lastRotation) return
        lastImage = image; lastRotation = pose.rotationDegrees
        ic.setProperties(PropertyFactory.iconImage(image), PropertyFactory.iconRotate(pose.rotationDegrees))
    }

    /** Dauerhafte Animation: Schweben und sich ausbreitender Ring (etwa 30 Bilder pro Sekunde reichen). */
    private fun startIdle() {
        idleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                if (++frame % 2 != 0) return@addUpdateListener
                val t = a.animatedValue as Float
                val bob = sin(t * 2 * PI).toFloat() // -1..1
                ring?.setProperties(PropertyFactory.circleRadius(14f + 26f * t), PropertyFactory.circleStrokeOpacity(0.55f * (1f - t)))
                // Je hoeher die Figur schwebt, desto kleiner und blasser der Schatten
                shadow?.setProperties(PropertyFactory.circleRadius(13f - 1.5f * bob), PropertyFactory.circleOpacity(0.25f - 0.04f * bob))
                icon?.setProperties(PropertyFactory.iconTranslate(arrayOf(0f, -28f - 3f * bob)))
            }
            start()
        }
    }

    fun release() {
        moveAnim?.cancel(); moveAnim = null
        idleAnim?.cancel(); idleAnim = null
        style = null; source = null; shadow = null; ring = null; icon = null
        lastImage = null
    }
}
