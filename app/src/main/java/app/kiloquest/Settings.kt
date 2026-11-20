package app.kiloquest

import android.content.SharedPreferences

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Einstellungen der App (alles bleibt auf dem Geraet). Altersstufe, Spielerzahl, Figur, Kinderkarte, Sprachantworten,
 * Reiseuebelkeit-Modus und einfache Auto-Karte haben eigene Felder im [UiState], weil andere Teile der App sie direkt nutzen.
 */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Abbiege-, Spur- und Verkehrsansagen. */
    val naviVoice: Boolean = true,
    /** Kilo liest Aufgaben, Geschichten und Hinweise vor. */
    val kiloVoice: Boolean = true,
    val keepScreenOn: Boolean = true,
    val showSpeedLimit: Boolean = true,
    val laneHints: Boolean = true,
    val detourSuggestions: Boolean = true,
    val weatherHints: Boolean = true,
    val breakSuggestions: Boolean = true,
    /** Zusaetzliche Aufgaben, die unterwegs aus dem aktuellen Ort erzeugt werden. */
    val liveQuests: Boolean = true,
    /** Klo-Alarm: so weit (km) darf ein Halt seitlich von der Route liegen. */
    val toiletCorridorKm: Int = 2,
    /** Mehrtagesreise: so viele Stunden Fahrzeit pro Tag. */
    val dailyDriveHours: Int = 8,
)

internal object SettingsStore {
    private const val P = "set_"

    fun load(p: SharedPreferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = runCatching { ThemeMode.valueOf(p.getString(P + "theme", null) ?: "") }.getOrDefault(d.theme),
            naviVoice = p.getBoolean(P + "navi_voice", d.naviVoice),
            kiloVoice = p.getBoolean(P + "kilo_voice", d.kiloVoice),
            keepScreenOn = p.getBoolean(P + "keep_screen_on", d.keepScreenOn),
            showSpeedLimit = p.getBoolean(P + "speed_limit", d.showSpeedLimit),
            laneHints = p.getBoolean(P + "lane_hints", d.laneHints),
            detourSuggestions = p.getBoolean(P + "detours", d.detourSuggestions),
            weatherHints = p.getBoolean(P + "weather", d.weatherHints),
            breakSuggestions = p.getBoolean(P + "breaks", d.breakSuggestions),
            liveQuests = p.getBoolean(P + "live_quests", d.liveQuests),
            toiletCorridorKm = p.getInt(P + "toilet_corridor", d.toiletCorridorKm).takeIf { it in setOf(1, 2, 4) } ?: d.toiletCorridorKm,
            dailyDriveHours = p.getInt(P + "daily_hours", d.dailyDriveHours).takeIf { it in setOf(4, 6, 8, 10) } ?: d.dailyDriveHours,
        )
    }

    fun save(p: SharedPreferences, s: AppSettings) {
        p.edit()
            .putString(P + "theme", s.theme.name)
            .putBoolean(P + "navi_voice", s.naviVoice)
            .putBoolean(P + "kilo_voice", s.kiloVoice)
            .putBoolean(P + "keep_screen_on", s.keepScreenOn)
            .putBoolean(P + "speed_limit", s.showSpeedLimit)
            .putBoolean(P + "lane_hints", s.laneHints)
            .putBoolean(P + "detours", s.detourSuggestions)
            .putBoolean(P + "weather", s.weatherHints)
            .putBoolean(P + "breaks", s.breakSuggestions)
            .putBoolean(P + "live_quests", s.liveQuests)
            .putInt(P + "toilet_corridor", s.toiletCorridorKm)
            .putInt(P + "daily_hours", s.dailyDriveHours)
            .apply()
    }
}
