package app.kiloquest.car

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.Surface
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import app.kiloquest.NavController
import app.kiloquest.nav.LatLon
import kotlin.math.cos
import kotlin.math.max

/**
 * Einfacher Canvas-Renderer fuer die Karten-Surface im Auto: Route + eigene Position.
 * Spaeter durch MapLibre (VirtualDisplay) ersetzbar; hier bewusst leichtgewichtig.
 */
class CarMapRenderer(private val nav: NavController) {
    private var surface: Surface? = null
    private var visible = Rect()

    private val bg = Paint().apply { color = Color.rgb(30, 34, 40) }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 136, 229); style = Paint.Style.STROKE; strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val posPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val posRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 136, 229); style = Paint.Style.FILL }

    val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(c: SurfaceContainer) { surface = c.surface; redraw() }
        override fun onSurfaceDestroyed(c: SurfaceContainer) { surface = null }
        override fun onVisibleAreaChanged(r: Rect) { visible = r; redraw() }
    }

    fun redraw() {
        val s = surface?.takeIf { it.isValid } ?: return
        val canvas: Canvas = runCatching { s.lockCanvas(null) }.getOrNull() ?: return
        try { draw(canvas) } finally { s.unlockCanvasAndPost(canvas) }
    }

    private fun draw(canvas: Canvas) {
        canvas.drawPaint(bg)
        val st = nav.state.value
        val line = st.route?.line?.points
        val pos = st.position
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val area = if (visible.isEmpty) Rect(0, 0, canvas.width, canvas.height) else visible
        val cx = area.exactCenterX()
        val cy = area.exactCenterY()

        val center: LatLon = pos ?: line?.firstOrNull() ?: return
        // Massstab: waehrend Navigation ca. 600 m Radius, sonst Route einpassen
        val metersPerDegLat = 111_195.0
        val metersPerDegLon = metersPerDegLat * cos(Math.toRadians(center.lat))
        var radiusM = 600.0
        if (!st.navigating && line != null) {
            radiusM = max(
                line.maxOf { Math.abs(it.lat - center.lat) * metersPerDegLat },
                line.maxOf { Math.abs(it.lon - center.lon) * metersPerDegLon },
            ).coerceAtLeast(300.0) * 1.1
        }
        val scale = (minOf(area.width(), area.height()) / 2f) / radiusM.toFloat()

        fun x(p: LatLon) = cx + ((p.lon - center.lon) * metersPerDegLon * scale).toFloat()
        fun y(p: LatLon) = cy - ((p.lat - center.lat) * metersPerDegLat * scale).toFloat()

        if (line != null && line.size >= 2) {
            val path = Path()
            path.moveTo(x(line[0]), y(line[0]))
            for (i in 1 until line.size) path.lineTo(x(line[i]), y(line[i]))
            canvas.drawPath(path, routePaint)
        }
        if (pos != null) {
            canvas.drawCircle(x(pos), y(pos), 22f, posPaint)
            canvas.drawCircle(x(pos), y(pos), 14f, posRing)
        }
    }
}
