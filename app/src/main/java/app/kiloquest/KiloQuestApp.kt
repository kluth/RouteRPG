package app.kiloquest

import android.app.Application
import org.maplibre.android.MapLibre

class KiloQuestApp : Application() {
    val nav: NavController by lazy { NavController(this) }
    /** Hauptgeraet: gekoppelte Geraete (Bluetooth). */
    val link: app.kiloquest.link.LinkController by lazy { app.kiloquest.link.LinkController(this, nav) }
    /** Dieses Geraet als Kinder-/Beifahrer-Geraet eines Hauptgeraets. */
    val linkClient: app.kiloquest.link.BtClient by lazy {
        app.kiloquest.link.BtClient(this, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate))
    }

    override fun onCreate() {
        super.onCreate()
        // Die Karte im Auto laeuft auch, wenn die Handy-Activity nie gestartet wurde
        MapLibre.getInstance(this)
    }
}
