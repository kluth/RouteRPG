package app.kiloquest.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.lifecycle.lifecycleScope
import app.kiloquest.NavController
import app.kiloquest.R
import kotlinx.coroutines.launch

/** Karte (Surface) + Abbiegeinfo. Nur erlaubte Templates, keine Spiel-UI (Ablenkungsregeln). */
class NavScreen(carContext: CarContext, private val nav: NavController) : Screen(carContext) {

    init {
        lifecycleScope.launch { nav.state.collect { invalidate() } }
    }

    override fun onGetTemplate(): Template {
        val s = nav.state.value
        val builder = NavigationTemplate.Builder()
            .setBackgroundColor(androidx.car.app.model.CarColor.SECONDARY)

        val actions = ActionStrip.Builder()
        if (s.navigating) {
            actions.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.stop_navigation))
                    .setOnClickListener { nav.stopNavigation() }
                    .build(),
            )
        } else {
            actions.addAction(Action.Builder().setTitle(carContext.getString(R.string.app_name)).build())
        }
        builder.setActionStrip(actions.build())

        val route = s.route
        val g = s.guidance
        if (s.navigating && route != null && g != null) {
            val m = route.maneuvers.getOrNull(g.nextManeuverIndex)
            val step = Step.Builder(m?.instruction.orEmpty())
                .setManeuver(Maneuver.Builder(Maneuver.TYPE_STRAIGHT).build())
                .build()
            val dist = if (g.metersToNextManeuver >= 1000) {
                Distance.create(g.metersToNextManeuver / 1000.0, Distance.UNIT_KILOMETERS)
            } else {
                Distance.create(g.metersToNextManeuver, Distance.UNIT_METERS)
            }
            builder.setNavigationInfo(RoutingInfo.Builder().setCurrentStep(step, dist).build())
        }
        return builder.build()
    }
}
