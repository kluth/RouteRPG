package app.kiloquest

import android.content.Context
import app.kiloquest.game.AudioGameType
import app.kiloquest.game.KiloPraise
import app.kiloquest.game.Lang
import app.kiloquest.game.LetterGame
import app.kiloquest.game.RhymeGame
import app.kiloquest.game.StoryRelay
import app.kiloquest.game.TurnResult
import app.kiloquest.game.WordChain
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Fuehrt ein Audio-Spiel (ohne Bildschirm) als Gespraech: Kilo spricht, hoert zu (nur auf dem Geraet), wertet aus und antwortet.
 * Die Regeln stecken in `:game-engine`; hier gibt es nur Ablauf und Texte. Kilo schimpft nie: bei Fehlern gibt es einen Hinweis.
 * Alles laeuft ueber [Io], damit Navigationsansagen immer Vorrang haben.
 */
class AudioGameRunner(private val ctx: Context, private val io: Io) {
    interface Io {
        /** Spricht [text] und kehrt zurueck, wenn die Ausgabe fertig ist (wartet vorher auf Ruhe nach Navigationsansagen). */
        suspend fun say(text: String)
        /** Hoert einmal zu; leer bei Stille/Fehler. */
        suspend fun listen(): List<String>
        /** Punkte fuer das Teamziel. */
        fun reward(points: Int)
        /** Spracherkennung vorhanden? Ohne sie geht nur die Geschichte (mit festen Pausen). */
        val canListen: Boolean
    }

    private var praiseNo = 0
    private fun str(id: Int, vararg a: Any) = ctx.getString(id, *a)
    private fun up(c: Char) = c.uppercaseChar().toString()

    suspend fun run(type: AudioGameType, seed: Long = System.nanoTime()) {
        val lang = Lang.of(java.util.Locale.getDefault().language)
        when (type) {
            AudioGameType.WORD_CHAIN -> wordChain(lang, seed)
            AudioGameType.LETTER_CATEGORY -> letters(lang, seed)
            AudioGameType.RHYME -> rhyme(lang, seed)
            AudioGameType.STORY_RELAY -> story(seed)
        }
    }

    private suspend fun praise() {
        praiseNo = KiloPraise.pick(true, praiseNo, Random.Default)
        io.say(ctx.stringByKey(KiloPraise.key(true, praiseNo)))
    }

    private suspend fun wordChain(lang: Lang, seed: Long) {
        val g = WordChain(lang, seed)
        io.say(str(R.string.game_chain_intro))
        val first = g.start()
        io.say(str(R.string.game_chain_first, first, up(g.requiredLetter!!)))
        var silent = 0; var wrong = 0; var turns = 0
        while (turns < MAX_TURNS && silent < 3) {
            val heard = io.listen()
            when (val r = g.play(heard)) {
                is TurnResult.Accepted -> {
                    silent = 0; wrong = 0; turns++; io.reward(3)
                    io.say(str(R.string.game_chain_kilo, r.kiloWord!!, up(r.nextLetter!!)))
                }
                is TurnResult.KiloConcedes -> { io.reward(8); io.say(str(R.string.game_chain_concede)); return }
                is TurnResult.Rejected -> when (r.reason) {
                    TurnResult.Reason.NOT_HEARD -> { silent++; if (silent < 3) io.say(str(R.string.game_nothing)) }
                    TurnResult.Reason.WRONG_LETTER -> {
                        silent = 0; wrong++
                        if (wrong >= 2) { g.hint()?.let { io.say(str(R.string.game_chain_hint, it)) }; wrong = 0 }
                        else io.say(str(R.string.game_chain_wrong, up(g.requiredLetter!!)))
                    }
                    TurnResult.Reason.ALREADY_USED -> { silent = 0; io.say(str(R.string.game_chain_used, up(g.requiredLetter!!))) }
                }
            }
        }
        finish(silent >= 3)
    }

    private suspend fun letters(lang: Lang, seed: Long) {
        val g = LetterGame(lang, seed)
        io.say(str(R.string.game_letter_intro))
        var silent = 0
        repeat(ROUNDS) {
            val r = g.next()
            val cat = str(categoryString(r.categoryKey))
            io.say(str(R.string.game_letter_ask, cat, up(r.letter)))
            var tries = 0
            while (tries < 3 && silent < 3) {
                tries++
                when (val res = g.answer(io.listen())) {
                    is TurnResult.Accepted -> { silent = 0; io.reward(4); praise(); tries = 99 }
                    is TurnResult.Rejected -> when (res.reason) {
                        TurnResult.Reason.NOT_HEARD -> { silent++; if (silent < 3) io.say(str(R.string.game_nothing)) }
                        TurnResult.Reason.WRONG_LETTER -> { silent = 0; io.say(str(R.string.game_letter_wrong, up(r.letter))) }
                        TurnResult.Reason.ALREADY_USED -> { silent = 0; io.say(str(R.string.game_letter_used)) }
                    }
                    else -> Unit
                }
            }
            if (silent >= 3) { finish(true); return }
        }
        finish(false)
    }

    private fun categoryString(key: String) = when (key) {
        "animal" -> R.string.game_cat_animal
        "food" -> R.string.game_cat_food
        "place" -> R.string.game_cat_place
        else -> R.string.game_cat_thing
    }

    private suspend fun rhyme(lang: Lang, seed: Long) {
        val g = RhymeGame(lang, seed)
        io.say(str(R.string.game_rhyme_intro))
        var silent = 0
        repeat(ROUNDS) {
            io.say(str(R.string.game_rhyme_ask, g.next()))
            var tries = 0
            while (tries < 2 && silent < 3) {
                tries++
                val heard = io.listen()
                when {
                    heard.isEmpty() -> { silent++; if (silent < 3) io.say(str(R.string.game_nothing)) }
                    g.check(heard) -> { silent = 0; io.reward(4); praise(); tries = 99 }
                    else -> { silent = 0; if (tries >= 2) g.hint()?.let { io.say(str(R.string.game_rhyme_hint, it)) } else io.say(str(R.string.game_rhyme_again)) }
                }
            }
            if (silent >= 3) { finish(true); return }
        }
        finish(false)
    }

    private suspend fun story(seed: Long) {
        val s = StoryRelay(seed)
        io.say(str(R.string.game_story_intro))
        var silent = 0
        for ((i, key) in s.steps.withIndex()) {
            io.say(ctx.stringByKey(key))
            if (i == s.steps.lastIndex) break
            io.say(str(R.string.game_story_prompt))
            // Wer erzaehlt, wird nicht ausgewertet: jede Aeusserung zaehlt, ohne Erkennung gibt es eine feste Pause
            if (io.canListen) { if (io.listen().isEmpty()) silent++ else { silent = 0; io.reward(3) } } else delay(PAUSE_MS)
            if (silent >= 2) { finish(true); return }
        }
        io.reward(6)
        finish(false)
    }

    private suspend fun finish(gaveUp: Boolean) = io.say(str(if (gaveUp) R.string.game_giveup else R.string.game_end))

    companion object { const val MAX_TURNS = 12; const val ROUNDS = 5; const val PAUSE_MS = 9_000L }
}
