package app.kiloquest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.PathParser
import app.kiloquest.nav.Glyph
import app.kiloquest.nav.IconContext
import app.kiloquest.nav.IconPalette
import app.kiloquest.nav.IconSpec
import app.kiloquest.nav.NotifEvent
import app.kiloquest.nav.NotificationIcons
import java.time.LocalDateTime

/**
 * Benachrichtigungs-Icons: kleines einfarbiges Statusleisten-Symbol (je Anlass ein anderes Zeichen) und grosses farbiges Icon aus dem App-Icon
 * (Farbwelt je nach Tageszeit, Ort und Anlass). Die Regeln stehen in `NotificationIcons` (nav-core, getestet); gezeichnet wird mit der
 * Geometrie des App-Icons ([IconGeometry], erzeugt von tools/icon/build_icon.py).
 */
object NotifIcons {
    private val cache = object : android.util.LruCache<String, Bitmap>(12) {}

    fun smallRes(g: Glyph): Int = when (g) {
        Glyph.STAR -> R.drawable.ic_stat_star
        Glyph.ARROW -> R.drawable.ic_stat_arrow
        Glyph.PAUSE -> R.drawable.ic_stat_pause
        Glyph.WC -> R.drawable.ic_stat_wc
        Glyph.CAMERA -> R.drawable.ic_stat_camera
        Glyph.CALENDAR -> R.drawable.ic_stat_calendar
        Glyph.WARNING -> R.drawable.ic_stat_warning
        Glyph.CROSS -> R.drawable.ic_stat_cross
        Glyph.BOOK -> R.drawable.ic_stat_book
        Glyph.FLAG -> R.drawable.ic_stat_flag
        Glyph.MOON -> R.drawable.ic_stat_moon
        Glyph.SNOW -> R.drawable.ic_stat_snow
        Glyph.SUN -> R.drawable.ic_stat_sun
    }

    /** Palette des gewaehlten App-Icons (gilt, wenn "dynamische Icons" aus sind). */
    fun appIconPalette(ctx: Context): IconPalette = when (AppIcon.current(ctx)) {
        AppIcon.Variant.DEFAULT -> IconPalette.DAY
        AppIcon.Variant.NIGHT -> IconPalette.NIGHT
        AppIcon.Variant.SUNSET -> IconPalette.DUSK
        AppIcon.Variant.FOREST -> IconPalette.FOREST
        AppIcon.Variant.MONO -> IconPalette.MONO
    }

    fun spec(ctx: Context, event: NotifEvent, lat: Double?, dynamic: Boolean, now: LocalDateTime = LocalDateTime.now()): IconSpec =
        NotificationIcons.pick(event, IconContext(now, lat, appIconPalette(ctx)), dynamic)

    /** Grosses Icon (rund, `px` x `px`); Ergebnisse werden zwischengespeichert. */
    fun large(spec: IconSpec, px: Int = 192): Bitmap {
        val key = "${spec.glyph}-${spec.palette}-$px"
        cache.get(key)?.let { return it }
        return render(spec, px).also { cache.put(key, it) }
    }

    private fun render(spec: IconSpec, px: Int): Bitmap {
        val pal = IconGeometry.PALETTES.getValue(spec.palette.name)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // sichtbarer Bereich 18..90 des 108er-Rasters
        c.scale(px / 72f, px / 72f)
        c.translate(-18f, -18f)
        val clip = Path().apply { addCircle(54f, 54f, 36f, Path.Direction.CW) }
        c.clipPath(clip)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 108f, 108f, pal.bg1, pal.bg2, Shader.TileMode.CLAMP)
        c.drawRect(-10f, -10f, 118f, 118f, p)
        val glowA = (pal.glow * 255).toInt()
        p.shader = RadialGradient(30f, 24f, 62f, (glowA shl 24) or 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(-10f, -10f, 118f, 118f, p)
        p.shader = null
        // Strasse
        val road = PathParser.createPathFromPathData(IconGeometry.ROAD)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.color = 0x26FFFFFF.toInt(); p.strokeWidth = 13f; c.drawPath(road, p)
        p.color = 0x61FFFFFF.toInt(); p.strokeWidth = 1.3f; p.pathEffect = DashPathEffect(floatArrayOf(3.2f, 3.6f), 0f); c.drawPath(road, p)
        p.pathEffect = null
        // Schatten, Pin, Glanz
        p.style = Paint.Style.FILL; p.color = 0x3D000000.toInt(); c.drawOval(RectF(42f, 81.9f, 66f, 87.3f), p)
        p.shader = LinearGradient(54f, 25f, 54f, 83f, pal.pin1, pal.pin2, Shader.TileMode.CLAMP)
        c.drawPath(PathParser.createPathFromPathData(IconGeometry.PIN), p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.color = 0x8CFFFFFF.toInt(); p.strokeWidth = 1.7f
        c.drawPath(PathParser.createPathFromPathData("M40.6,36.5 A15.4,15.4 0 0 1 51,29.4"), p)
        // dunkler Kreis und Zeichen
        p.style = Paint.Style.FILL; p.color = pal.navy
        c.drawCircle(IconGeometry.CIRCLE_CX, IconGeometry.CIRCLE_CY, IconGeometry.CIRCLE_R, p)
        val glyph = PathParser.createPathFromPathData(IconGeometry.GLYPHS.getValue(spec.glyph.id)).apply { fillType = Path.FillType.EVEN_ODD }
        p.color = pal.glyph; c.drawPath(glyph, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.8f; p.strokeJoin = Paint.Join.ROUND; c.drawPath(glyph, p)
        return bmp
    }
}
