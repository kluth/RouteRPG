package app.kiloquest.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.AppManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.kiloquest.KiloQuestApp
import kotlinx.coroutines.launch

class KiloQuestSession : Session() {
    private var renderer: CarMapRenderer? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val nav = (carContext.applicationContext as KiloQuestApp).nav
        // Position auch ohne Handy-Activity verfolgen
        if (nav.hasLocationPermission()) nav.startLocationUpdates()
        val r = CarMapRenderer(nav)
        renderer = r

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(r.surfaceCallback)
            }
        })
        lifecycle.coroutineScopeLaunch { nav.state.collect { r.redraw() } }
        return NavScreen(carContext, nav)
    }

    private fun androidx.lifecycle.Lifecycle.coroutineScopeLaunch(block: suspend () -> Unit) {
        (this@KiloQuestSession as androidx.lifecycle.LifecycleOwner).lifecycleScope.launch { block() }
    }
}
