package app.kiloquest

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.kiloquest.game.AgeBand
import app.kiloquest.game.Quest
import app.kiloquest.game.QuestKind
import app.kiloquest.game.QuestType
import app.kiloquest.nav.GuidanceState
import app.kiloquest.nav.Lane
import app.kiloquest.nav.LaneGuidance
import app.kiloquest.nav.LatLon
import app.kiloquest.nav.Maneuver
import app.kiloquest.nav.Polyline
import app.kiloquest.nav.Route
import app.kiloquest.ui.KiloQuestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Layout-Pruefung ohne Geraet: rendert die Oberflaeche in verschiedenen Fenstergroessen und schreibt PNGs nach
 * `app/build/layout-shots/` (nur Ansicht, keine Pruefung der Pixel). Karte wird durch eine graue Flaeche ersetzt.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class LayoutShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val route = Route(
        Polyline(listOf(LatLon(51.43, 6.76), LatLon(51.44, 6.77), LatLon(51.5, 6.9))),
        listOf(Maneuver(10, "Rechts abbiegen", null, null, listOf("Hauptstraße"), 800.0, 100.0)),
        lengthMeters = 143_000.0, durationSeconds = 6720.0,
    )
    private val quest = Quest("quiz", "quest_quiz_reindeer", QuestType.QUIZ, 0.0, QuestKind.CHOICE, listOf("quest_quiz_reindeer_opt1", "quest_quiz_reindeer_opt2", "quest_quiz_reindeer_opt3"), 0)

    private val driving = UiState(
        route = route, navigating = true, speedLimit = 80, speedKmh = 92, overspeed = true,
        guidance = GuidanceState(0.0, 0.0, false, 0, 800.0, 143_000.0, false),
        lanes = LaneGuidance(800.0, listOf(Lane(listOf("straight"), false, false), Lane(listOf("right"), true, true), Lane(listOf("right"), true, true))),
    )
    private val preview = UiState(route = route, navigating = false)
    private val kids = UiState(quest = quest, route = route, kidsScreen = true, ageBand = AgeBand.EXPLORER, points = 40, stickers = 1, questsDone = 3, questsTotal = 25, album = setOf("fox", "owl"), plan = listOf(quest, quest.copy(textKey = "quest_windmill", kind = QuestKind.CHECK, optionKeys = emptyList())))

    private fun shot(name: String, w: Int, h: Int, dark: Boolean = false, state: () -> UiState, screen: Boolean = false) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val fake = object : KidsActions {
            override fun answerQuest(choice: Int?) {}
            override fun skipQuest() {}
            override fun dismissFeedback() {}
            override fun showKids(show: Boolean) {}
        }
        val vm by lazy { NavController(app) }
        rule.setContent {
            KiloQuestTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF2B3036) else Color(0xFFE5E1D6))) {
                    if (screen) KidsScreen(state(), fake) else AppOverlays(state(), vm)
                }
            }
        }
        rule.waitForIdle()
        // Direkt zeichnen statt PixelCopy (in Robolectric nicht verfuegbar)
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(dm.widthPixels, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(dm.heightPixels, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val bmp = android.graphics.Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val dir = File("build/layout-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Config(qualifiers = "w360dp-h780dp-port-notnight-xxhdpi")
    @Test fun driveSmallPortrait() = shot("drive-360x780", 360, 780, state = { driving })
    @Config(qualifiers = "w360dp-h780dp-port-night-xxhdpi")
    @Test fun driveSmallPortraitDark() = shot("drive-360x780-dark", 360, 780, dark = true, state = { driving })
    @Config(qualifiers = "w780dp-h360dp-land-notnight-xxhdpi")
    @Test fun driveLandscapePhone() = shot("drive-780x360", 780, 360, state = { driving })
    @Config(qualifiers = "w1000dp-h640dp-land-notnight-xxhdpi")
    @Test fun driveTablet() = shot("drive-1000x640", 1000, 640, state = { driving })
    @Config(qualifiers = "w390dp-h844dp-port-notnight-xxhdpi")
    @Test fun previewPortrait() = shot("preview-390x844", 390, 844, state = { preview })
    @Config(qualifiers = "w1000dp-h640dp-land-notnight-xxhdpi")
    @Test fun previewTablet() = shot("preview-1000x640", 1000, 640, state = { preview })
    @Config(qualifiers = "w390dp-h844dp-port-notnight-xxhdpi")
    @Test fun searchPortrait() = shot("search-390x844", 390, 844, state = { UiState() })
    @Config(qualifiers = "w390dp-h844dp-port-notnight-xxhdpi")
    @Test fun kidsPortrait() = shot("kids-390x844", 390, 844, state = { kids }, screen = true)
    @Config(qualifiers = "w1000dp-h640dp-land-notnight-xxhdpi")
    @Test fun kidsTablet() = shot("kids-1000x640", 1000, 640, state = { kids }, screen = true)
}
