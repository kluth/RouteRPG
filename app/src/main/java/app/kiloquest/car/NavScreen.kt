package app.kiloquest.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.lifecycle.lifecycleScope
import app.kiloquest.NavController
import app.kiloquest.R
import app.kiloquest.UiState
import app.kiloquest.nav.LaneGuidance
import app.kiloquest.nav.Maneuver
import java.util.TimeZone
import kotlinx.coroutines.launch

/**
 * Karte (Surface) + Abbiegeinfo mit Pfeil, Strassenname, naechstem Schritt und Ankunftszeit.
 * Nur erlaubte Templates, keine Spiel-UI (Ablenkungsregeln); Spiel laeuft auf dem Handy, im Auto nur Audio.
 */
class NavScreen(carContext: CarContext, private val nav: NavController, private val map: CarMapView) : Screen(carContext) {

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
            .setBackgroundColor(CarColor.SECONDARY)
            .setActionStrip(actionStrip(s))
            .setMapActionStrip(mapActionStrip())

        val route = s.route
        val g = s.guidance
        if (s.navigating && route != null && g != null) {
            val m = route.maneuvers.getOrNull(g.nextManeuverIndex)
            val next = route.maneuvers.getOrNull(g.nextManeuverIndex + 1)
            builder.setNavigationInfo(
                RoutingInfo.Builder()
                    .setCurrentStep(step(m, s.lanes.takeIf { s.settings.laneHints }), distance(g.metersToNextManeuver))
                    .apply { if (next != null && g.nextManeuverIndex + 1 < route.maneuvers.size) setNextStep(step(next)) }
                    .build(),
            )
            // Restdistanz und Ankunftszeit
            val fraction = if (route.lengthMeters > 0) g.remainingMeters / route.lengthMeters else 0.0
            val seconds = (route.durationSeconds * fraction).toLong()
            builder.setDestinationTravelEstimate(
                TravelEstimate.Builder(distance(g.remainingMeters), DateTimeWithZone.create(System.currentTimeMillis() + seconds * 1000, TimeZone.getDefault()))
                    .setRemainingTimeSeconds(seconds)
                    .build(),
            )
        }
        return builder.build()
    }

    private fun step(m: Maneuver?, lanes: LaneGuidance? = null): Step {
        val b = Step.Builder(m?.instruction.orEmpty()).setManeuver(carManeuver(carContext, m))
        m?.streetNames?.firstOrNull()?.let { b.setRoad(it) }
        // Offizielle Spuranzeige (Host zeichnet sie nativ); schlaegt etwas fehl, bleibt der Schritt ohne Spuren
        val extra = lanes?.takeIf { it.lanes.size in 2..8 }?.let { runCatching { CarLanes.carLanes(it) to CarLanes.image(it) }.getOrNull() }
        if (extra != null) { extra.first.forEach { b.addLane(it) }; b.setLanesImage(extra.second) }
        return b.build()
    }

    private fun distance(meters: Double): Distance =
        if (meters >= 1000) Distance.create(meters / 1000.0, Distance.UNIT_KILOMETERS)
        else Distance.create(meters.coerceAtLeast(0.0), Distance.UNIT_METERS)

    /** Aktionen oben: je nach Zustand Umfahrung, Pause, Stopp oder Start/Suche. */
    private fun actionStrip(s: UiState): ActionStrip {
        val actions = ActionStrip.Builder()
        fun add(title: Int, onClick: () -> Unit) = actions.addAction(
            Action.Builder().setTitle(carContext.getString(title)).setOnClickListener(onClick).build(),
        )
        when {
            s.proposal != null -> { add(R.string.reroute_accept) { nav.acceptReroute() }; add(R.string.reroute_dismiss) { nav.dismissReroute() } }
            s.breakProposal != null -> { add(R.string.break_accept) { nav.acceptBreak() }; add(R.string.break_later) { nav.laterBreak() } }
            s.navigating -> add(R.string.stop_navigation) { nav.stopNavigation() }
            else -> {
                if (s.route != null) add(R.string.start_navigation) { nav.startNavigation() }
                add(R.string.search_action) {
                    nav.clearResults()
                    screenManager.push(SearchScreen(carContext, nav))
                }
            }
        }
        return actions.build()
    }

    /** Karte: Verschieben (vom Auto bereitgestellt), Zoom, auf Position zentrieren. */
    private fun mapActionStrip(): ActionStrip {
        fun iconAction(res: Int, onClick: () -> Unit) =
            Action.Builder().setIcon(carIcon(carContext, res)).setOnClickListener(onClick).build()
        val b = ActionStrip.Builder().addAction(Action.PAN)
        // Waehrend der Fahrt keine Zoom-Tasten (Ablenkung); die Karte zoomt selbst nach Tempo.
        if (nav.state.value.navigating) {
            b.addAction(iconAction(R.drawable.ic_car_recenter) { map.recenter() })
        } else {
            b.addAction(iconAction(R.drawable.ic_car_zoom_in) { map.zoomIn() })
                .addAction(iconAction(R.drawable.ic_car_zoom_out) { map.zoomOut() })
                .addAction(iconAction(R.drawable.ic_car_recenter) { map.recenter() })
        }
        return b
            .build()
    }
}
