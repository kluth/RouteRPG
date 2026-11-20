package app.kiloquest.car

import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.Maneuver as CarManeuver
import androidx.core.graphics.drawable.IconCompat
import app.kiloquest.R
import app.kiloquest.nav.Maneuver
import app.kiloquest.nav.ManeuverKind

/** Uebersetzt ein Valhalla-Manoever in ein Android-Auto-Manoever (Typ, Symbol, Kreisverkehr-Ausfahrt). */
fun carManeuver(ctx: CarContext, m: Maneuver?): CarManeuver {
    val kind = m?.kind ?: ManeuverKind.UNKNOWN
    val (type, icon) = when (kind) {
        ManeuverKind.DEPART -> CarManeuver.TYPE_DEPART to R.drawable.ic_man_straight
        ManeuverKind.STRAIGHT -> CarManeuver.TYPE_STRAIGHT to R.drawable.ic_man_straight
        ManeuverKind.SLIGHT_LEFT -> CarManeuver.TYPE_TURN_SLIGHT_LEFT to R.drawable.ic_man_slight_left
        ManeuverKind.LEFT -> CarManeuver.TYPE_TURN_NORMAL_LEFT to R.drawable.ic_man_left
        ManeuverKind.SHARP_LEFT -> CarManeuver.TYPE_TURN_SHARP_LEFT to R.drawable.ic_man_sharp_left
        ManeuverKind.SLIGHT_RIGHT -> CarManeuver.TYPE_TURN_SLIGHT_RIGHT to R.drawable.ic_man_slight_right
        ManeuverKind.RIGHT -> CarManeuver.TYPE_TURN_NORMAL_RIGHT to R.drawable.ic_man_right
        ManeuverKind.SHARP_RIGHT -> CarManeuver.TYPE_TURN_SHARP_RIGHT to R.drawable.ic_man_sharp_right
        ManeuverKind.UTURN_LEFT -> CarManeuver.TYPE_U_TURN_LEFT to R.drawable.ic_man_uturn
        ManeuverKind.UTURN_RIGHT -> CarManeuver.TYPE_U_TURN_RIGHT to R.drawable.ic_man_uturn
        ManeuverKind.EXIT_LEFT -> CarManeuver.TYPE_OFF_RAMP_NORMAL_LEFT to R.drawable.ic_man_slight_left
        ManeuverKind.EXIT_RIGHT -> CarManeuver.TYPE_OFF_RAMP_NORMAL_RIGHT to R.drawable.ic_man_slight_right
        ManeuverKind.RAMP_LEFT -> CarManeuver.TYPE_ON_RAMP_NORMAL_LEFT to R.drawable.ic_man_slight_left
        ManeuverKind.RAMP_RIGHT -> CarManeuver.TYPE_ON_RAMP_NORMAL_RIGHT to R.drawable.ic_man_slight_right
        ManeuverKind.KEEP_LEFT -> CarManeuver.TYPE_KEEP_LEFT to R.drawable.ic_man_slight_left
        ManeuverKind.KEEP_RIGHT -> CarManeuver.TYPE_KEEP_RIGHT to R.drawable.ic_man_slight_right
        ManeuverKind.MERGE -> CarManeuver.TYPE_MERGE_SIDE_UNSPECIFIED to R.drawable.ic_man_straight
        // Rechtsverkehr (DE, DK, SE ...): Kreisverkehr gegen den Uhrzeigersinn
        ManeuverKind.ROUNDABOUT -> CarManeuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW to R.drawable.ic_man_roundabout
        ManeuverKind.FERRY -> CarManeuver.TYPE_FERRY_BOAT to R.drawable.ic_man_ferry
        ManeuverKind.DESTINATION -> CarManeuver.TYPE_DESTINATION to R.drawable.ic_man_destination
        ManeuverKind.DESTINATION_LEFT -> CarManeuver.TYPE_DESTINATION_LEFT to R.drawable.ic_man_destination
        ManeuverKind.DESTINATION_RIGHT -> CarManeuver.TYPE_DESTINATION_RIGHT to R.drawable.ic_man_destination
        ManeuverKind.UNKNOWN -> CarManeuver.TYPE_UNKNOWN to R.drawable.ic_man_straight
    }
    val b = CarManeuver.Builder(type).setIcon(carIcon(ctx, icon))
    if (kind == ManeuverKind.ROUNDABOUT) b.setRoundaboutExitNumber((m?.roundaboutExit ?: 1).coerceAtLeast(1))
    return b.build()
}

/** Gemeinsames Symbol fuer Handy und Auto. */
fun maneuverIconRes(kind: ManeuverKind): Int = when (kind) {
    ManeuverKind.SLIGHT_LEFT, ManeuverKind.EXIT_LEFT, ManeuverKind.RAMP_LEFT, ManeuverKind.KEEP_LEFT -> R.drawable.ic_man_slight_left
    ManeuverKind.LEFT -> R.drawable.ic_man_left
    ManeuverKind.SHARP_LEFT -> R.drawable.ic_man_sharp_left
    ManeuverKind.SLIGHT_RIGHT, ManeuverKind.EXIT_RIGHT, ManeuverKind.RAMP_RIGHT, ManeuverKind.KEEP_RIGHT -> R.drawable.ic_man_slight_right
    ManeuverKind.RIGHT -> R.drawable.ic_man_right
    ManeuverKind.SHARP_RIGHT -> R.drawable.ic_man_sharp_right
    ManeuverKind.UTURN_LEFT, ManeuverKind.UTURN_RIGHT -> R.drawable.ic_man_uturn
    ManeuverKind.ROUNDABOUT -> R.drawable.ic_man_roundabout
    ManeuverKind.FERRY -> R.drawable.ic_man_ferry
    ManeuverKind.DESTINATION, ManeuverKind.DESTINATION_LEFT, ManeuverKind.DESTINATION_RIGHT -> R.drawable.ic_man_destination
    else -> R.drawable.ic_man_straight
}

fun carIcon(ctx: CarContext, res: Int): CarIcon = CarIcon.Builder(IconCompat.createWithResource(ctx, res)).build()
