package app.kiloquest

import android.content.SharedPreferences
import app.kiloquest.nav.PowerSaverMode

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
    /** Routenwuensche: Mautstrassen, Autobahnen, Faehren meiden (gilt fuer jede neue Route). */
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
    /** Freisprechen: Befehle mit der Anrede "Kilo" waehrend der Fahrt (Erkennung nur auf dem Geraet, nichts wird gespeichert). */
    val voiceCommands: Boolean = false,
    /** Roadtrip-Fotostory: das Beifahrer-Geraet wird gelegentlich um ein Foto mit beiden Kameras gebeten. */
    val photoPrompts: Boolean = true,
    /** Fotos zusaetzlich in die Galerie kopieren (Pictures/KiloQuest). */
    val photosToGallery: Boolean = false,
    /** Sprachansage "Tempolimit N" bei dauerhafter Ueberschreitung (gilt ueberall, betrifft nur das eigene Tempo). */
    val overspeedWarning: Boolean = true,
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
    /** Benachrichtigungen vor Reisebeginn und vor Check-ins. */
    val tripReminders: Boolean = true,
    /** Akku-Sparmodus: automatisch bei wenig Akku oder Energiesparmodus, immer oder nie. */
    val powerSaver: PowerSaverMode = PowerSaverMode.AUTO,
    /** Spracherkennung auch ohne Offline-Paket ueber den normalen Dienst des Geraets (Audio kann an dessen Server gehen). Nur nach Warnhinweis. */
    val cloudSpeech: Boolean = false,
    /** Benachrichtigungs-Icon wechselt je nach Anlass, Tageszeit und Ort; aus = immer Pin mit Stern in der Farbe des gewaehlten App-Icons. */
    val dynamicNotifIcons: Boolean = true,
    /** Ruhige Ansicht fuer Kinder: kein Huepfen, Konfetti oder bewegte Figuren. */
    val calmView: Boolean = false,
    /** Abbiegeansagen aus Bausteinen bauen (Spec 016); mit aufgenommenen Clips, sobald vorhanden. Standard an (nur mit aufgenommenen Bausteinen klingt es nach Kilo, sonst Geraete-Stimme). */
    val composedNavVoice: Boolean = true,
    /** Strassennamen fuer Kilos Navi-Stimme vorab vom eigenen Stimmen-Proxy holen (sendet Namen der Route; nur nach Einwilligung). */
    val cloudVoice: Boolean = false,
    /** Auto-Display: kleines Tierquiz nur bei Stillstand (< 3 km/h gemessen) und laufender Navigation; Standard aus. */
    val carParkedGames: Boolean = false,
    /** Eigene Namen der Kinder (bis zu 4, nur auf diesem Geraet gespeichert, nie im Export); leer = "Spieler N". */
    val playerNames: List<String> = emptyList(),
) {
    /** Name des Spielers [index] (0-basiert) oder null, wenn keiner vergeben ist. */
    fun nameOf(index: Int): String? = playerNames.getOrNull(index)?.takeIf { it.isNotBlank() }

    companion object {
        const val MAX_NAME = 16
        fun cleanNames(raw: List<String>): List<String> {
            val cleaned = raw.take(4).map { it.filter { c -> !c.isISOControl() }.trim().take(MAX_NAME) }
            return if (cleaned.all { it.isBlank() }) emptyList() else cleaned
        }
    }
}

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
            avoidTolls = p.getBoolean(P + "avoid_tolls", d.avoidTolls),
            avoidHighways = p.getBoolean(P + "avoid_highways", d.avoidHighways),
            avoidFerries = p.getBoolean(P + "avoid_ferries", d.avoidFerries),
            voiceCommands = p.getBoolean(P + "voice_commands", d.voiceCommands),
            photoPrompts = p.getBoolean(P + "photo_prompts", d.photoPrompts),
            photosToGallery = p.getBoolean(P + "photos_gallery", d.photosToGallery),
            overspeedWarning = p.getBoolean(P + "overspeed_warning", d.overspeedWarning),
            laneHints = p.getBoolean(P + "lane_hints", d.laneHints),
            detourSuggestions = p.getBoolean(P + "detours", d.detourSuggestions),
            weatherHints = p.getBoolean(P + "weather", d.weatherHints),
            breakSuggestions = p.getBoolean(P + "breaks", d.breakSuggestions),
            liveQuests = p.getBoolean(P + "live_quests", d.liveQuests),
            toiletCorridorKm = p.getInt(P + "toilet_corridor", d.toiletCorridorKm).takeIf { it in setOf(1, 2, 4) } ?: d.toiletCorridorKm,
            dailyDriveHours = p.getInt(P + "daily_hours", d.dailyDriveHours).takeIf { it in setOf(4, 6, 8, 10) } ?: d.dailyDriveHours,
            tripReminders = p.getBoolean(P + "trip_reminders", d.tripReminders),
            cloudSpeech = p.getBoolean(P + "cloud_speech", d.cloudSpeech),
            dynamicNotifIcons = p.getBoolean(P + "dyn_notif_icons", d.dynamicNotifIcons),
            calmView = p.getBoolean(P + "calm_view", d.calmView),
            composedNavVoice = p.getBoolean(P + "composed_nav", d.composedNavVoice),
            cloudVoice = p.getBoolean(P + "cloud_voice", d.cloudVoice),
            carParkedGames = p.getBoolean(P + "car_parked_games", d.carParkedGames),
            playerNames = AppSettings.cleanNames((p.getString(P + "player_names", null) ?: "").split('\n')),
            powerSaver = runCatching { PowerSaverMode.valueOf(p.getString(P + "power_saver", null) ?: "") }.getOrDefault(d.powerSaver),
        )
    }

    fun save(p: SharedPreferences, s: AppSettings) {
        p.edit()
            .putString(P + "theme", s.theme.name)
            .putBoolean(P + "navi_voice", s.naviVoice)
            .putBoolean(P + "kilo_voice", s.kiloVoice)
            .putBoolean(P + "keep_screen_on", s.keepScreenOn)
            .putBoolean(P + "speed_limit", s.showSpeedLimit)
            .putBoolean(P + "avoid_tolls", s.avoidTolls)
            .putBoolean(P + "avoid_highways", s.avoidHighways)
            .putBoolean(P + "avoid_ferries", s.avoidFerries)
            .putBoolean(P + "voice_commands", s.voiceCommands)
            .putBoolean(P + "photo_prompts", s.photoPrompts)
            .putBoolean(P + "photos_gallery", s.photosToGallery)
            .putBoolean(P + "overspeed_warning", s.overspeedWarning)
            .putBoolean(P + "lane_hints", s.laneHints)
            .putBoolean(P + "detours", s.detourSuggestions)
            .putBoolean(P + "weather", s.weatherHints)
            .putBoolean(P + "breaks", s.breakSuggestions)
            .putBoolean(P + "live_quests", s.liveQuests)
            .putInt(P + "toilet_corridor", s.toiletCorridorKm)
            .putInt(P + "daily_hours", s.dailyDriveHours)
            .putBoolean(P + "trip_reminders", s.tripReminders)
            .putString(P + "power_saver", s.powerSaver.name)
            .putBoolean(P + "cloud_speech", s.cloudSpeech)
            .putBoolean(P + "dyn_notif_icons", s.dynamicNotifIcons)
            .putBoolean(P + "calm_view", s.calmView)
            .putBoolean(P + "composed_nav", s.composedNavVoice)
            .putBoolean(P + "cloud_voice", s.cloudVoice)
            .putBoolean(P + "car_parked_games", s.carParkedGames)
            .putString(P + "player_names", AppSettings.cleanNames(s.playerNames).joinToString("\n"))
            .apply()
    }
}
