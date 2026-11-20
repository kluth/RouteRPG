package app.kiloquest

import app.kiloquest.game.Quest
import app.kiloquest.game.QuestKind
import app.kiloquest.game.QuestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackQuestsTest {
    private val q = Quest("t", "key", QuestType.QUIZ, 5000.0, QuestKind.CHOICE, listOf("a", "b"), 1, 25, listOf("Köln"))

    @Test fun roundTrip() {
        assertEquals(q, PackQuests.fromPack(PackQuests.toPack(q)))
    }

    @Test fun offsetIsSubtracted() {
        assertEquals(3000.0, PackQuests.toPack(q, 2000.0).trigger, 0.0)
    }

    @Test fun unknownEnumNamesGiveNull() {
        assertNull(PackQuests.fromPack(PackQuests.toPack(q).copy(type = "UFO")))
        assertNull(PackQuests.fromPack(PackQuests.toPack(q).copy(kind = "UFO")))
    }
}
