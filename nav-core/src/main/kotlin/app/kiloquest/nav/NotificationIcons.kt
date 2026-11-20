package app.kiloquest.nav

import java.time.DayOfWeek
import java.time.LocalDateTime

/** Anlass einer Benachrichtigung. */
enum class NotifEvent { TRIP_RUNNING, LINK_ONLY, BREAK, STOPOVER, TOILET, PHOTO, REMINDER, DEPARTURE, ARRIVED, WARNING, EMERGENCY, GOAL, STORY }

/** Zeichen im Pin (klein: weiss ausgeschnitten, gross: gold auf dunklem Kreis). */
enum class Glyph(val id: String) {
    STAR("star"), ARROW("arrow"), PAUSE("pause"), WC("wc"), CAMERA("camera"), CALENDAR("calendar"), WARNING("warning"), CROSS("cross"),
    BOOK("book"), FLAG("flag"), MOON("moon"), SNOW("snow"), SUN("sun"),
}

/** Farbwelt des grossen Icons (passend zu den App-Icon-Varianten, dazu ALERT fuer Warnungen). */
enum class IconPalette { DAY, DUSK, NIGHT, FOREST, MONO, ALERT }

data class IconSpec(val glyph: Glyph, val palette: IconPalette)

/** Alles, woraus das Icon abgeleitet wird. [lat] null = Ort unbekannt. */
data class IconContext(val time: LocalDateTime, val lat: Double? = null, val appIcon: IconPalette = IconPalette.DAY)

/**
 * Dynamisches Benachrichtigungs-Icon: Das kleine Statusleisten-Symbol (einfarbig, so verlangt es Android) zeigt je nach Anlass ein anderes Zeichen im Pin,
 * das grosse farbige Icon zusaetzlich die Farbwelt je nach Tageszeit, Ort und Anlass. Ohne Dynamik gilt immer Pin + Stern in der gewaehlten App-Icon-Farbe.
 *
 * Regeln (Reihenfolge zaehlt):
 *  - Farbe: Warnung/Notfall -> ALERT; Nacht (22-5 Uhr) -> NIGHT; Daemmerung (5-7 und 18-22 Uhr) -> DUSK; tagsüber noerdlich von 60 Grad (Norden, Natur) -> FOREST; sonst DAY.
 *  - Zeichen: nach Anlass (Pause, Klo, Foto, ...). Beim laufenden Navi: nachts Mond, im Winter (Nov-Feb) im Norden (ab 60 Grad) Schneeflocke,
 *    an Weihnachten und Silvester Stern, sonst Pfeil. Normale Erinnerungen am Wochenende bleiben beim Kalender, am Abreisetag eine Fahne.
 */
object NotificationIcons {
    fun isNight(h: Int) = h >= 22 || h < 5
    fun isDusk(h: Int) = h in 5..6 || h in 18..21

    fun pick(event: NotifEvent, c: IconContext, dynamic: Boolean = true): IconSpec {
        if (!dynamic) return IconSpec(if (event == NotifEvent.TRIP_RUNNING || event == NotifEvent.LINK_ONLY) Glyph.STAR else eventGlyph(event) ?: Glyph.STAR, c.appIcon)
        val h = c.time.hour
        val north = (c.lat ?: 0.0) >= 60.0
        val palette = when {
            event == NotifEvent.WARNING || event == NotifEvent.EMERGENCY -> IconPalette.ALERT
            isNight(h) -> IconPalette.NIGHT
            isDusk(h) -> IconPalette.DUSK
            north -> IconPalette.FOREST
            else -> IconPalette.DAY
        }
        val glyph = eventGlyph(event) ?: when {
            festive(c.time) -> Glyph.STAR
            isNight(h) -> Glyph.MOON
            north && c.time.monthValue !in 3..10 -> Glyph.SNOW
            event == NotifEvent.LINK_ONLY -> Glyph.STAR
            else -> Glyph.ARROW
        }
        return IconSpec(glyph, palette)
    }

    /** Weihnachten (24.-26.12.) und Silvester/Neujahr (31.12., 1.1.). */
    fun festive(t: LocalDateTime) = (t.monthValue == 12 && t.dayOfMonth in 24..26) || (t.monthValue == 12 && t.dayOfMonth == 31) || (t.monthValue == 1 && t.dayOfMonth == 1)

    private fun eventGlyph(e: NotifEvent): Glyph? = when (e) {
        NotifEvent.BREAK, NotifEvent.STOPOVER -> Glyph.PAUSE
        NotifEvent.TOILET -> Glyph.WC
        NotifEvent.PHOTO -> Glyph.CAMERA
        NotifEvent.REMINDER -> Glyph.CALENDAR
        NotifEvent.DEPARTURE, NotifEvent.ARRIVED -> Glyph.FLAG
        NotifEvent.WARNING -> Glyph.WARNING
        NotifEvent.EMERGENCY -> Glyph.CROSS
        NotifEvent.GOAL -> Glyph.STAR
        NotifEvent.STORY -> Glyph.BOOK
        NotifEvent.TRIP_RUNNING, NotifEvent.LINK_ONLY -> null
    }

    /** Wochenende, fuer Texte/Erweiterungen (z. B. fruehere Erinnerung); ohne Einfluss auf das Icon. */
    fun isWeekend(t: LocalDateTime) = t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY
}
