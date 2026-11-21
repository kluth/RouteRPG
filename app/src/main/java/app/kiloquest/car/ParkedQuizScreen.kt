package app.kiloquest.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import app.kiloquest.NavController
import app.kiloquest.R
import app.kiloquest.UiState
import app.kiloquest.game.WildlifeQuestion
import app.kiloquest.game.WildlifeQuiz
import app.kiloquest.game.WildlifeSpecies
import app.kiloquest.stringByKey
import kotlinx.coroutines.launch

/** Gemessen mit Geschwindigkeit: unter dieser Grenze (km/h) gilt das Auto als stehend. Ohne Messwert gilt es als fahrend. */
internal const val PARKED_MAX_KMH = 3

internal fun UiState.isParkedForGames(): Boolean = settings.carParkedGames && navigating && (speedKmh?.let { it < PARKED_MAX_KMH } == true)

/**
 * Kleines Tierquiz fuer das Auto-Display, nur im Stand: erlaubtes ListTemplate, keine Eingabe, kein Distraction-Flag.
 * Sobald das Auto faehrt (oder die Messung fehlt), schliesst sich der Bildschirm von selbst. Fragen laufen offline (Rueckfall-Arten).
 */
class ParkedQuizScreen(carContext: CarContext, private val nav: NavController) : Screen(carContext) {
    private val questions: List<WildlifeQuestion> = WildlifeQuiz.generate(emptyList(), System.nanoTime(), 5)
    private var index = 0
    private var answered = -1

    init {
        lifecycleScope.launch {
            nav.state.collect { st ->
                if (!st.isParkedForGames()) {
                    CarToast.makeText(carContext, carContext.getString(R.string.car_games_stopped), CarToast.LENGTH_LONG).show()
                    finish()
                } else invalidate()
            }
        }
    }

    private fun name(s: WildlifeSpecies): String {
        val byKey = carContext.stringByKey(s.nameKey)
        return if (byKey != s.nameKey) byKey else s.displayName ?: s.scientificName
    }

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        if (index >= questions.size) {
            list.addItem(Row.Builder().setTitle(carContext.getString(R.string.wild_done)).build())
            return template(carContext.getString(R.string.car_games_title), list)
        }
        val q = questions[index]
        if (answered < 0) {
            q.options.forEachIndexed { i, sp -> list.addItem(Row.Builder().setTitle(name(sp)).setOnClickListener { answered = i; invalidate() }.build()) }
        } else {
            val text = if (q.isCorrect(answered)) carContext.getString(R.string.mini_great) else carContext.getString(R.string.kids_wrong, name(q.correct))
            list.addItem(Row.Builder().setTitle(text).build())
            list.addItem(Row.Builder().setTitle(carContext.getString(R.string.car_games_next)).setOnClickListener { index += 1; answered = -1; invalidate() }.build())
        }
        return template(carContext.stringByKey(q.textKey), list)
    }

    private fun template(title: String, list: ItemList.Builder): Template =
        ListTemplate.Builder().setTitle(title).setHeaderAction(Action.BACK).setSingleList(list.build()).build()
}
