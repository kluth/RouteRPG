package app.kiloquest.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.Lane as CarLane
import androidx.car.app.navigation.model.LaneDirection
import androidx.core.graphics.drawable.IconCompat
import app.kiloquest.nav.Lane
import app.kiloquest.nav.LaneGuidance

/**
 * Spuranzeige mit den **offiziellen** Auto-Elementen (`Lane`/`LaneDirection` plus Spurbild), die der Host nativ und gut lesbar zeigt.
 * Ersetzt nicht die Sprachansage. Das Spurbild zeichnen wir selbst (der Host verlangt eines, sobald Spuren gesetzt sind).
 */
object CarLanes {
    /** Richtung der Spur laut OSRM-Angabe als Auto-Form (Rechtsverkehr: Wenden nach links). */
    fun shapeOf(indication: String): Int = when (indication) {
        "left" -> LaneDirection.SHAPE_NORMAL_LEFT
        "slight left" -> LaneDirection.SHAPE_SLIGHT_LEFT
        "sharp left" -> LaneDirection.SHAPE_SHARP_LEFT
        "straight" -> LaneDirection.SHAPE_STRAIGHT
        "right" -> LaneDirection.SHAPE_NORMAL_RIGHT
        "slight right" -> LaneDirection.SHAPE_SLIGHT_RIGHT
        "sharp right" -> LaneDirection.SHAPE_SHARP_RIGHT
        "uturn" -> LaneDirection.SHAPE_U_TURN_LEFT
        else -> LaneDirection.SHAPE_UNKNOWN
    }

    fun carLanes(g: LaneGuidance): List<CarLane> = g.lanes.map { l ->
        val b = CarLane.Builder()
        val inds = l.indications.ifEmpty { listOf("none") }
        inds.forEachIndexed { i, ind -> b.addDirection(LaneDirection.create(shapeOf(ind), l.valid && i == 0)) }
        b.build()
    }

    /** Spurbild: je Spur ein Pfeil, gueltige Spuren hell, andere grau, durch dunkle Trennlinien getrennt. */
    fun image(g: LaneGuidance): CarIcon {
        val laneW = 96; val h = 96
        val bmp = Bitmap.createBitmap(laneW * g.lanes.size, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(32, 33, 36))
        val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 64f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        val off = Paint(on).apply { color = Color.rgb(120, 124, 128); typeface = Typeface.DEFAULT }
        val sep = Paint().apply { color = Color.rgb(90, 94, 98); strokeWidth = 3f }
        g.lanes.forEachIndexed { i, l: Lane ->
            val cx = i * laneW + laneW / 2f
            c.drawText(l.symbol, cx, h / 2f + 22f, if (l.valid) on else off)
            if (i > 0) c.drawLine(i * laneW.toFloat(), 8f, i * laneW.toFloat(), h - 8f, sep)
        }
        return CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
    }
}
