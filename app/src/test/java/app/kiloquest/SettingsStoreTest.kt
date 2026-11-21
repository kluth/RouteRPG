package app.kiloquest

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsStoreTest {
    private val prefs get() = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("test", Context.MODE_PRIVATE)

    @Test fun defaultsWhenNothingSaved() {
        assertEquals(AppSettings(), SettingsStore.load(prefs))
    }

    @Test fun roundTrip() {
        val s = AppSettings(
            theme = ThemeMode.DARK, naviVoice = false, kiloVoice = false, keepScreenOn = false, showSpeedLimit = false,
            laneHints = false, detourSuggestions = false, weatherHints = false, breakSuggestions = false, liveQuests = false, toiletCorridorKm = 4, powerSaver = app.kiloquest.nav.PowerSaverMode.ON, cloudSpeech = true, dynamicNotifIcons = false, calmView = true,
        )
        SettingsStore.save(prefs, s)
        assertEquals(s, SettingsStore.load(prefs))
    }

    @Test fun unknownPowerSaverFallsBackToAuto() {
        prefs.edit().putString("set_power_saver", "TURBO").apply()
        assertEquals(app.kiloquest.nav.PowerSaverMode.AUTO, SettingsStore.load(prefs).powerSaver)
    }

    @Test fun cloudSpeechIsOffByDefault() {
        assertEquals(false, SettingsStore.load(prefs).cloudSpeech)
    }

    @Test fun unknownThemeFallsBackToSystem() {
        prefs.edit().putString("set_theme", "NEON").apply()
        assertEquals(ThemeMode.SYSTEM, SettingsStore.load(prefs).theme)
    }

    @Test fun invalidCorridorFallsBackToDefault() {
        prefs.edit().putInt("set_toilet_corridor", 7).apply()
        assertEquals(2, SettingsStore.load(prefs).toiletCorridorKm)
    }
}
