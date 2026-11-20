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
import androidx.car.app.CarToast
import androidx.lifecycle.lifecycleScope
import app.kiloquest.NavController
import app.kiloquest.R
import kotlinx.coroutines.launch

/** Karte (Surface) + Abbiegeinfo. Nur erlaubte Templates, keine Spiel-UI (Ablenkungsregeln). */
class NavScreen(carContext: CarContext, private val nav: NavController) : Screen(carContext) {

    private var lastToastError: Int? = null

    init {
        lifecycleScope.launch {
            nav.state.collect { st ->
                // Fehler (z. B. keine GPS-Position) auch im Auto sichtbar machen
                val err = st.error
                if (err != null && err != lastToastError) CarToast.makeText(carContext, carContext.getString(err), CarToast.LENGTH_LONG).show()
                lastToastError = err
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val s = nav.state.value
        val builder = NavigationTemplate.Builder()
            .setBackgroundColor(androidx.car.app.model.CarColor.SECONDARY)

        val actions = ActionStrip.Builder()
        val proposal = s.proposal
        if (proposal != null) {
            actions.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.reroute_accept))
                    .setOnClickListener { nav.acceptReroute() }
                    .build(),
            )
            actions.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.reroute_dismiss))
                    .setOnClickListener { nav.dismissReroute() }
                    .build(),
            )
        } else if (s.breakProposal != null) {
            actions.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.break_accept))
                    .setOnClickListener { nav.acceptBreak() }
                    .build(),
            )
            actions.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.break_later))
                    .setOnClickListener { nav.laterBreak() }
                    .build(),
            )
        } else if (s.navigating) {
            actions.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.stop_navigation))
                    .setOnClickListener { nav.stopNavigation() }
                    .build(),
            )
        } else {
            if (s.route != null) {
                actions.addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.start_navigation))
                        .setOnClickListener { nav.startNavigation() }
                        .build(),
                )
            }
            actions.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.search_action))
                    .setOnClickListener {
                        nav.clearResults()
                        screenManager.push(SearchScreen(carContext, nav))
                    }
                    .build(),
            )
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
