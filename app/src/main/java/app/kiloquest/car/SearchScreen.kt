package app.kiloquest.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import app.kiloquest.NavController
import app.kiloquest.R
import kotlinx.coroutines.launch

/**
 * Zielsuche im Auto (SearchTemplate): Tastatur nur im Stand/laut Host-Regeln, sonst Eingabe per Sprache,
 * wenn der Host sie anbietet. Ohne Eingabe erscheinen die letzten Ziele.
 */
class SearchScreen(carContext: CarContext, private val nav: NavController) : Screen(carContext) {
    private var typed = ""

    init {
        lifecycleScope.launch { nav.state.collect { invalidate() } }
    }

    override fun onGetTemplate(): Template {
        val s = nav.state.value
        val builder = SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
                override fun onSearchTextChanged(searchText: String) { typed = searchText }
                override fun onSearchSubmitted(searchText: String) { nav.search(searchText) }
            },
        )
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.search_hint))
            .setShowKeyboardByDefault(true)

        if (s.busy) {
            builder.setLoading(true)
        } else {
            val items = when {
                s.results.isNotEmpty() -> s.results
                typed.isBlank() -> s.recents
                else -> emptyList()
            }
            val list = ItemList.Builder()
            if (items.isEmpty()) {
                list.setNoItemsMessage(carContext.getString(R.string.error_no_results))
            } else {
                for (place in items.take(MAX_ITEMS)) {
                    list.addItem(
                        Row.Builder()
                            .setTitle(place.name)
                            .setOnClickListener {
                                nav.selectPlace(place)
                                screenManager.pop()
                            }
                            .build(),
                    )
                }
            }
            builder.setItemList(list.build())
        }
        return builder.build()
    }

    private companion object { const val MAX_ITEMS = 6 }
}
