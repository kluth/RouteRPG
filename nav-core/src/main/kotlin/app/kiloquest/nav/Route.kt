package app.kiloquest.nav

data class Maneuver(
    val type: Int,
    val instruction: String,
    val verbalPre: String?,
    val verbalPost: String?,
    val streetNames: List<String>,
    /** Distanz vom Routenstart bis zum Manoever in Metern. */
    val atMeters: Double,
    val lengthMeters: Double,
)

data class Route(
    val line: Polyline,
    val maneuvers: List<Maneuver>,
    val lengthMeters: Double,
    val durationSeconds: Double,
)
