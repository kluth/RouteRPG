package app.kiloquest.ui

import android.content.Context
import app.kiloquest.NavController
import app.kiloquest.R
import app.kiloquest.UiState
import app.kiloquest.nav.HintCandidate
import app.kiloquest.nav.HintDisplay
import app.kiloquest.nav.HintKind
import app.kiloquest.nav.HintQueue
import app.kiloquest.nav.IncidentType
import app.kiloquest.nav.RouteIncident
import app.kiloquest.nav.RestStop

/** Quelle eines Hinweises; wird erst zum Beschriften aufgeloest. */
sealed interface HintSource {
    data class Incident(val ri: RouteIncident) : HintSource
    data class Weather(val name: String) : HintSource
    data class Break(val stop: RestStop) : HintSource
}

/** Sammelt alle Kandidaten aus dem Zustand und waehlt den einen sichtbaren (siehe [HintQueue]). */
fun selectHint(st: UiState, nav: NavController): HintDisplay? {
    if (!st.navigating) return null
    val along = st.guidance?.alongMeters ?: 0.0
    val list = buildList {
        for (ri in st.incidents) {
            val i = ri.incident
            if (!(i.blocked || (i.delayMinutes ?: 0) >= 5)) continue
            val kind = when {
                i.blocked || i.type == IncidentType.CLOSURE -> HintKind.CLOSURE
                i.type == IncidentType.ACCIDENT || i.type == IncidentType.HAZARD -> HintKind.ACCIDENT
                (i.delayMinutes ?: 0) >= 15 -> HintKind.JAM_HEAVY
                else -> HintKind.JAM_LIGHT
            }
            add(HintCandidate(kind, ri.aheadMeters, HintSource.Incident(ri)))
        }
        st.weather?.hazardsAhead?.forEach { (h, at) ->
            add(HintCandidate(HintKind.WEATHER, at - along, HintSource.Weather(nav.hazardName(h))))
        }
        st.breakTarget?.let { add(HintCandidate(HintKind.BREAK, st.breakAheadMeters, HintSource.Break(it))) }
    }
    return HintQueue.select(list, st.guidance?.metersToNextManeuver)
}

fun hintText(ctx: Context, hint: HintDisplay): String {
    val dist = distanceText(ctx, hint.top.aheadMeters)
    return when (val src = hint.top.ref as HintSource) {
        is HintSource.Incident -> ctx.getString(
            when (src.ri.incident.type) {
                IncidentType.JAM -> R.string.hint_jam
                IncidentType.ACCIDENT -> R.string.hint_accident
                IncidentType.CLOSURE -> R.string.hint_closure
                IncidentType.HAZARD -> R.string.hint_hazard
            }.let { if (src.ri.incident.blocked) R.string.hint_closure else it },
            dist,
        )
        is HintSource.Weather -> ctx.getString(R.string.weather_hazard_line, src.name, dist)
        is HintSource.Break -> ctx.getString(R.string.hint_break, dist)
    }
}

fun distanceText(ctx: Context, meters: Double): String =
    if (meters >= 1000) ctx.getString(R.string.unit_km, meters / 1000.0)
    else ctx.getString(R.string.unit_m, (meters.coerceAtLeast(0.0) / 10).toInt() * 10)
