package app.kiloquest.link

import app.kiloquest.UiState
import app.kiloquest.QuestFeedback
import app.kiloquest.game.AgeBand
import app.kiloquest.game.Quest
import app.kiloquest.game.QuestKind
import app.kiloquest.game.QuestType
import app.kiloquest.game.StickerBook

fun Quest.toDto() = QuestDto(templateId, textKey, type.name, kind.name, optionKeys, args, points, triggerMeters)

/** Auf dem Geraet: ohne richtige Antwort (die kommt erst im Feedback). */
fun QuestDto.toQuest() = Quest(
    templateId = templateId, textKey = textKey,
    type = runCatching { QuestType.valueOf(type) }.getOrDefault(QuestType.SPOTTING),
    triggerMeters = triggerMeters,
    kind = runCatching { QuestKind.valueOf(kind) }.getOrDefault(QuestKind.CHECK),
    optionKeys = optionKeys, correctIndex = -1, points = points, args = args,
)

/** Zustand fuer ein gekoppeltes Geraet. [stopText] bereitet den Pausenort in der Sprache des Hauptgeraets auf. */
fun UiState.toSnapshot(role: Role, yourPlayer: Int, seq: Long, stopText: (app.kiloquest.nav.RestStop) -> String): Snapshot = Snapshot(
    seq = seq,
    navigating = navigating,
    curvy = curvy,
    quest = quest?.toDto(),
    feedback = feedback?.let { FeedbackDto(it.correct, it.points, it.correctOptionKey) },
    newStickerId = newSticker?.id,
    points = points, stickers = stickers, questsDone = questsDone, questsTotal = questsTotal,
    scores = scores, currentPlayer = currentPlayer, yourPlayer = yourPlayer,
    album = album.toList(), totalPoints = totalPoints,
    plan = plan.map { it.toDto() },
    reroute = if (role == Role.COPILOT) proposal?.let {
        RerouteDto(it.savedMinutes, it.blocked, it.incident.incident.type.name, it.incident.incident.road)
    } else null,
    breakProposal = if (role == Role.COPILOT) breakProposal?.let { BreakDto(it.aheadMeters, stopText(it.stop)) } else null,
    toilet = toilet?.let { ToiletDto(it.phase.name, it.label, it.seconds, it.meters) },
    toiletRequest = if (role == Role.COPILOT) toiletRequest else null,
)

/** Auf dem gekoppelten Geraet: Zustand fuer die vorhandene Kinderansicht; [ownBand] ist das eigene Alter. */
fun Snapshot.toUiState(ownBand: AgeBand, ownAvatar: String = app.kiloquest.game.Avatars.DEFAULT, ownRealMap: Boolean = true, routeDto: RouteDto? = null, posDto: PosDto? = null): UiState = UiState(
    avatar = ownAvatar,
    realMap = ownRealMap,
    // Karte der Kinder: Route nur als Linie (keine Manoever), Position und Fortschritt vom Hauptgeraet
    route = routeDto?.takeIf { posDto == null || posDto.routeId == 0L || posDto.routeId == it.id }?.let { r ->
        app.kiloquest.nav.Route(app.kiloquest.nav.Polyline(app.kiloquest.nav.PolylineCodec.decode(r.encoded)), emptyList(), r.lengthMeters, 0.0)
    },
    destination = routeDto?.takeIf { it.destLat != null && it.destLon != null }?.let { app.kiloquest.Place("", app.kiloquest.nav.LatLon(it.destLat!!, it.destLon!!)) },
    position = posDto?.let { app.kiloquest.nav.LatLon(it.lat, it.lon) },
    bearing = posDto?.bearing,
    guidance = posDto?.let { app.kiloquest.nav.GuidanceState(it.alongMeters, 0.0, false, 0, 0.0, (routeDto?.lengthMeters ?: 0.0) - it.alongMeters, false) },
    remote = true,
    navigating = navigating,
    curvy = curvy,
    quest = quest?.toQuest(),
    feedback = feedback?.let { QuestFeedback(it.correct, it.points, it.correctOptionKey) },
    newSticker = newStickerId?.let { id -> StickerBook.ALL.firstOrNull { it.id == id } },
    points = points, stickers = stickers, questsDone = questsDone, questsTotal = questsTotal,
    scores = scores.ifEmpty { listOf(0) }, players = scores.size.coerceAtLeast(1), currentPlayer = currentPlayer,
    album = album.toSet(), totalPoints = totalPoints,
    plan = plan.map { it.toQuest() },
    ageBand = ownBand,
    kidsScreen = true,
    toilet = toilet?.let { t -> app.kiloquest.ToiletState(runCatching { app.kiloquest.ToiletPhase.valueOf(t.phase) }.getOrDefault(app.kiloquest.ToiletPhase.HEADING), t.label, t.seconds, t.meters) },
)

fun parseBand(s: String): AgeBand = runCatching { AgeBand.valueOf(s) }.getOrDefault(AgeBand.EXPLORER)

/** Fahrtstatus fuer das Beifahrer-Geraet; null, wenn keine Route da ist. */
fun UiState.toDriveDto(): DriveDto? {
    val r = route ?: return null
    val g = guidance
    val total = r.lengthMeters
    val remaining = g?.remainingMeters ?: total
    val seconds = if (total > 0.0) r.durationSeconds * (remaining / total) else 0.0
    val next = g?.let { r.maneuvers.getOrNull(it.nextManeuverIndex) }
    return DriveDto(
        destination = destination?.name.orEmpty(), totalMeters = total, remainingMeters = remaining, remainingSeconds = seconds,
        speedKmh = speedKmh, limitKmh = speedLimit, country = country,
        nextInstruction = next?.instruction, nextMeters = if (next != null) g.metersToNextManeuver else null,
    )
}

/** Reise-Extras fuer Beifahrer-Geraete (nur Schluessel und Zahlen; die Texte baut das Geraet in seiner Sprache). */
fun UiState.toExtrasDto(): ExtrasDto = ExtrasDto(
    tolls = tolls.map { it.country },
    remote = remoteStretches.take(6).map { RemoteDto((it.startMeters / 1000).toInt(), (it.endMeters / 1000).toInt()) },
    goalCollected = teamGoal?.collected ?: 0,
    goalTarget = teamGoal?.target ?: 0,
    nextBreakMin = nextBreakMin?.takeIf { navigating },
    powerSaving = powerSaving,
    destTempC = weather?.destination?.tempC?.let { Math.round(it).toInt() },
    destCondition = weather?.destination?.condition?.name,
    spots = spots.map { SpotDto(it.kind.name, it.name, it.alongMeters - (guidance?.alongMeters ?: 0.0), it.detourMinutes, it.open.name) },
    spotsLoading = spotsLoading,
    spotsSearched = spotsSearched,
    stopoverLabel = stopover?.label,
    stopoverArrived = stopover?.phase == app.kiloquest.StopoverPhase.ARRIVED,
    stopoverStory = stopover?.story?.let { (it.title + ". " + it.text).take(700) },
    game = audioGame,
    seriesNext = seriesNext,
    seriesHeard = seriesHeard,
    emergency = emergency?.let { e ->
        EmergencyDto(e.number, e.location, e.hospitals.map { HospitalDto(it.name, Math.round(it.distanceMeters / 1000.0).toInt()) }, e.loading, country)
    },
)
