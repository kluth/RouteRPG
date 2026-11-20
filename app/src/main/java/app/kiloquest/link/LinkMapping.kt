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
)

/** Auf dem gekoppelten Geraet: Zustand fuer die vorhandene Kinderansicht; [ownBand] ist das eigene Alter. */
fun Snapshot.toUiState(ownBand: AgeBand): UiState = UiState(
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
)

fun parseBand(s: String): AgeBand = runCatching { AgeBand.valueOf(s) }.getOrDefault(AgeBand.EXPLORER)
