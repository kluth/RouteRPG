package app.kiloquest

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.kiloquest.game.Quest
import app.kiloquest.game.QuestKind
import app.kiloquest.game.QuestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QuestSpeechTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Application>()
    private val quiz = Quest("quiz_reindeer", "quest_quiz_reindeer", QuestType.QUIZ, 0.0, QuestKind.CHOICE, listOf("quest_quiz_reindeer_opt1", "quest_quiz_reindeer_opt2", "quest_quiz_reindeer_opt3"), 0)
    private val check = Quest("windmill", "quest_windmill", QuestType.SPOTTING, 0.0)

    @Test fun miniQuizReadsAllAnswersWithShapeAndColor() {
        val text = ctx.questSpeech(quiz, withOptions = true)
        assertTrue(text.startsWith(ctx.stringByKey("quest_quiz_reindeer")))
        assertTrue(text, "Blue circle: Reindeer" in text)
        assertTrue(text, "Orange square: Camel" in text)
        assertTrue(text, "Green triangle: Penguin" in text)
    }

    @Test fun olderChildrenAndCheckQuestsReadOnlyTheText() {
        assertEquals(ctx.stringByKey("quest_quiz_reindeer"), ctx.questSpeech(quiz, withOptions = false))
        assertEquals(ctx.stringByKey("quest_windmill"), ctx.questSpeech(check, withOptions = true))
    }
}
