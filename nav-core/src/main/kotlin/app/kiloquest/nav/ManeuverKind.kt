package app.kiloquest.nav

/** Vereinfachte Manoeverart fuer Pfeile und Android-Auto-Symbole (aus Valhallas Manoever-Typen). */
enum class ManeuverKind {
    DEPART, STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT,
    UTURN_LEFT, UTURN_RIGHT, EXIT_LEFT, EXIT_RIGHT, RAMP_LEFT, RAMP_RIGHT, KEEP_LEFT, KEEP_RIGHT,
    MERGE, ROUNDABOUT, FERRY, DESTINATION, DESTINATION_LEFT, DESTINATION_RIGHT, UNKNOWN;

    companion object {
        /** Valhalla DirectionsLeg_Maneuver_Type. */
        fun fromValhalla(type: Int): ManeuverKind = when (type) {
            1, 2, 3 -> DEPART
            4 -> DESTINATION
            5 -> DESTINATION_RIGHT
            6 -> DESTINATION_LEFT
            7, 8, 17, 22 -> STRAIGHT
            9 -> SLIGHT_RIGHT
            10 -> RIGHT
            11 -> SHARP_RIGHT
            12 -> UTURN_RIGHT
            13 -> UTURN_LEFT
            14 -> SHARP_LEFT
            15 -> LEFT
            16 -> SLIGHT_LEFT
            18 -> RAMP_RIGHT
            19 -> RAMP_LEFT
            20 -> EXIT_RIGHT
            21 -> EXIT_LEFT
            23 -> KEEP_RIGHT
            24 -> KEEP_LEFT
            25, 37, 38 -> MERGE
            26, 27 -> ROUNDABOUT
            28, 29 -> FERRY
            else -> UNKNOWN
        }
    }
}
