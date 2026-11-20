package app.kiloquest.car

import android.content.Intent
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.kiloquest.KiloQuestApp
import kotlinx.coroutines.launch

class KiloQuestSession : Session() {
    override fun onNewIntent(intent: Intent) {
        // Gemini/Assistant im Auto: Navigation starten, suchen, Aktionen
        intent.dataString?.let { (carContext.applicationContext as KiloQuestApp).nav.handleIntent(intent.action, it) }
    }

    override fun onCreateScreen(intent: Intent): Screen {
        val nav = (carContext.applicationContext as KiloQuestApp).nav
        intent.dataString?.let { nav.handleIntent(intent.action, it) }
        // Position auch ohne Handy-Activity verfolgen
        if (nav.hasLocationPermission()) nav.startLocationUpdates()
        val map = CarMapView(carContext, nav)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(map.surfaceCallback)
            }
        })
        lifecycleScope.launch { nav.state.collect { map.update() } }
        return NavScreen(carContext, nav, map)
    }
}
