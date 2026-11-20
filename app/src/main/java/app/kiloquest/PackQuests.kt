package app.kiloquest

import app.kiloquest.game.Quest
import app.kiloquest.game.QuestKind
import app.kiloquest.game.QuestType
import app.kiloquest.nav.PackQuest

/** Umwandlung der Aufgaben fuer das Offline-Paket. Unbekannte Enum-Namen (neuere Version) ergeben null statt Absturz. */
internal object PackQuests {
    fun toPack(q: Quest, offsetMeters: Double = 0.0) = PackQuest(
        q.templateId, q.textKey, q.type.name, q.triggerMeters - offsetMeters, q.kind.name, q.optionKeys, q.correctIndex, q.points, q.args,
    )

    fun fromPack(p: PackQuest): Quest? {
        val type = runCatching { QuestType.valueOf(p.type) }.getOrNull() ?: return null
        val kind = runCatching { QuestKind.valueOf(p.kind) }.getOrNull() ?: return null
        return Quest(p.templateId, p.textKey, type, p.trigger, kind, p.options, p.correct, p.points, p.args)
    }
}
