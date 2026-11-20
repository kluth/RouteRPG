package app.kiloquest

import android.app.Application
import org.maplibre.android.MapLibre

class KiloQuestApp : Application() {
    val nav: NavController by lazy { NavController(this) }

    override fun onCreate() {
        super.onCreate()
        // Die Karte im Auto laeuft auch, wenn die Handy-Activity nie gestartet wurde
        MapLibre.getInstance(this)
    }
}
