package app.kiloquest.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioGamesTest {
    @Test fun wordChainFollowsLastLetter() {
        val g = WordChain(Lang.DE, 1)
        val start = g.start()
        val need = g.requiredLetter!!
        assertEquals(AnswerMatcher.normalize(start).last(), need)
        val say = listOf("$need" + "uto") // beginnt mit dem Pflichtbuchstaben
        val r = g.play(say)
        assertTrue(r is TurnResult.Accepted || r is TurnResult.KiloConcedes)
        if (r is TurnResult.Accepted) {
            assertEquals(AnswerMatcher.normalize(r.kiloWord!!)[0], AnswerMatcher.normalize(say[0]).last())
            assertEquals(AnswerMatcher.normalize(r.kiloWord!!).last(), g.requiredLetter)
        }
    }

    @Test fun wordChainRejectsWrongLetterAndRepeats() {
        val g = WordChain(Lang.EN, 7)
        g.start()
        val need = g.requiredLetter!!
        val wrong = if (need == 'z') "apple" else "zzz"
        assertEquals(TurnResult.Rejected(TurnResult.Reason.WRONG_LETTER), g.play(listOf(wrong)))
        assertEquals(TurnResult.Rejected(TurnResult.Reason.NOT_HEARD), g.play(emptyList()))
        val w = need + "ix"
        val first = g.play(listOf(w))
        if (first is TurnResult.Accepted) {
            // dasselbe Wort noch einmal: Kilos Wort endet evtl. anders, also nur pruefen, wenn der Buchstabe gleich bleibt
            if (g.requiredLetter == w[0]) assertEquals(TurnResult.Rejected(TurnResult.Reason.ALREADY_USED), g.play(listOf(w)))
        }
    }

    @Test fun wordChainIsDeterministic() {
        assertEquals(WordChain(Lang.DE, 5).start(), WordChain(Lang.DE, 5).start())
    }

    @Test fun wordChainNeverStartsOnUnfriendlyLetter() {
        repeat(50) { s -> val g = WordChain(Lang.DE, s.toLong()); g.start(); assertNotNull(g.hint()) }
    }

    @Test fun kiloConcedesOnRareLetter() {
        val g = WordChain(Lang.EN, 3)
        g.start()
        val need = g.requiredLetter!!
        val r = g.play(listOf(need + "xx")) // endet auf x: kein Kilo-Wort
        assertTrue(r is TurnResult.KiloConcedes)
    }

    @Test fun letterGameAcceptsMatchingStart() {
        val g = LetterGame(Lang.DE, 2)
        val r = g.next()
        val w = r.letter.lowercaseChar() + "ala"
        assertTrue(g.answer(listOf(w)) is TurnResult.Accepted)
        assertEquals(TurnResult.Rejected(TurnResult.Reason.ALREADY_USED), g.answer(listOf(w)))
        val other = if (r.letter == 'B') 'a' else 'b'
        assertEquals(TurnResult.Rejected(TurnResult.Reason.WRONG_LETTER), g.answer(listOf("${other}ala")))
        assertTrue(r.categoryKey in LetterGame.CATEGORIES)
    }

    @Test fun letterGameHandlesUmlautsAndEszett() {
        val g = LetterGame(Lang.DE, 2)
        g.next()
        assertTrue(g.answer(listOf("")) is TurnResult.Rejected)
    }

    @Test fun rhymeGroupsAndSuffix() {
        val g = RhymeGame(Lang.EN, 4)
        val base = g.next()
        val mate = g.hint()!!
        assertTrue(g.check(listOf(mate)))
        assertFalse(g.check(listOf(base)))
        assertFalse(g.check(listOf("banana")))
        assertTrue(g.check(listOf("something $mate")))
    }

    @Test fun rhymeGermanUmlautNormalisation() {
        val g = RhymeGame(Lang.DE, 9)
        repeat(12) { g.next(); assertTrue(g.check(listOf(g.hint()!!))) }
    }

    @Test fun storyRelayHasStartNextsAndEnd() {
        val s = StoryRelay(11, 4)
        assertEquals(6, s.steps.size)
        assertTrue(s.steps.first().startsWith("audio_story_start_"))
        assertEquals("audio_story_end", s.steps.last())
        assertEquals(s.steps, StoryRelay(11, 4).steps)
        assertEquals(4, s.steps.drop(1).dropLast(1).distinct().size)
    }

    @Test fun wordListsHaveNoDuplicatesAndFriendlyCoverage() {
        for (l in Lang.entries) {
            val w = WordLists.of(l)
            assertEquals(w.size, w.map { AnswerMatcher.normalize(it) }.toSet().size)
        }
        assertEquals(Lang.DE, Lang.of("de-DE")); assertEquals(Lang.EN, Lang.of("fr")); assertEquals(Lang.EN, Lang.of(null))
    }
}

class TeamGoalTest {
    @Test fun targetScalesWithDistance() {
        assertEquals(100, TeamGoals.targetFor(10.0))
        assertEquals(1400, TeamGoals.targetFor(2800.0))
        assertEquals(1500, TeamGoals.targetFor(9000.0))
    }

    @Test fun youngerKidsCountMoreAndPointsAreCapped() {
        assertEquals(15, TeamGoals.contribution(10, AgeBand.MINI))
        assertEquals(12, TeamGoals.contribution(10, AgeBand.EXPLORER))
        assertEquals(10, TeamGoals.contribution(10, AgeBand.PRO))
        assertEquals(15, TeamGoals.contribution(100, AgeBand.PRO))
        assertEquals(0, TeamGoals.contribution(-5, AgeBand.PRO))
    }

    @Test fun milestonesFireOnceInOrder() {
        var g = TeamGoal(100)
        val hits = ArrayList<Int>()
        repeat(12) { val u = TeamGoals.add(g, 10, AgeBand.PRO); g = u.goal; u.milestone?.let(hits::add) }
        assertEquals(listOf(25, 50, 75, 100), hits)
        assertTrue(g.reached)
        assertEquals(1.0, g.progress)
        assertNull(TeamGoals.add(g, 10, AgeBand.PRO).milestone)
    }

    @Test fun bigJumpGivesOnlyTheHighestMilestoneOnce() {
        val u = TeamGoals.add(TeamGoal(10), 15, AgeBand.PRO)
        assertEquals(100, u.milestone)
        assertEquals(4, u.goal.milestones)
    }

    @Test fun encodeDecode() {
        val g = TeamGoal(200, 50, 1)
        assertEquals(g, TeamGoals.decode(TeamGoals.encode(g)))
        assertNull(TeamGoals.decode("x")); assertNull(TeamGoals.decode("0:1:0")); assertNull(TeamGoals.decode("10:1:9")); assertNull(TeamGoals.decode(null))
    }
}

class FrustrationGuardTest {
    private val W = FrustrationGuard.Outcome.WRONG
    private val S = FrustrationGuard.Outcome.SKIPPED
    private val R = FrustrationGuard.Outcome.RIGHT
    private val D = FrustrationGuard.Outcome.DONE

    @Test fun threeFailsGiveEasier() {
        val g = FrustrationGuard()
        assertEquals(FrustrationGuard.Adjust.NONE, g.record(W))
        assertEquals(FrustrationGuard.Adjust.NONE, g.record(S))
        assertEquals(FrustrationGuard.Adjust.EASIER, g.record(W))
    }

    @Test fun fourRightGiveHarder() {
        val g = FrustrationGuard()
        repeat(3) { assertEquals(FrustrationGuard.Adjust.NONE, g.record(R)) }
        assertEquals(FrustrationGuard.Adjust.HARDER, g.record(R))
    }

    @Test fun doneInBetweenBreaksTheFailStreak() {
        val g = FrustrationGuard()
        g.record(W); g.record(D)
        assertEquals(FrustrationGuard.Adjust.NONE, g.record(W))
    }

    @Test fun cooldownPreventsRapidChanges() {
        val g = FrustrationGuard()
        g.record(W); g.record(W)
        assertEquals(FrustrationGuard.Adjust.EASIER, g.record(W))
        assertEquals(FrustrationGuard.Adjust.NONE, g.record(W))
        assertEquals(FrustrationGuard.Adjust.NONE, g.record(W))
    }

    @Test fun ageStepsClamp() {
        assertEquals(AgeBand.EXPLORER, FrustrationGuard.harder(AgeBand.MINI))
        assertEquals(AgeBand.PRO, FrustrationGuard.harder(AgeBand.PRO))
        assertEquals(AgeBand.MINI, FrustrationGuard.easier(AgeBand.MINI))
        assertEquals(AgeBand.EXPLORER, FrustrationGuard.easier(AgeBand.PRO))
    }
}

class KiloSeriesTest {
    @Test fun oneEpisodePerDay() {
        assertEquals(1, KiloSeries.available(null, 100))
        assertEquals(1, KiloSeries.available(100, 100))
        assertEquals(3, KiloSeries.available(100, 102))
        assertEquals(6, KiloSeries.available(100, 200))
        assertEquals(1, KiloSeries.available(100, 90)) // Uhr zurueckgestellt
    }

    @Test fun nextUnheard() {
        assertEquals(1, KiloSeries.next(emptySet(), 3))
        assertEquals(2, KiloSeries.next(setOf(1), 3))
        assertNull(KiloSeries.next(setOf(1, 2, 3), 3))
        assertEquals("series_ep4", KiloSeries.key(4))
    }

    @Test fun persistence() {
        assertEquals(setOf(1, 3), KiloSeries.decode(KiloSeries.encode(setOf(3, 1))))
        assertEquals(emptySet(), KiloSeries.decode("a,9,0"))
        assertEquals(emptySet(), KiloSeries.decode(null))
    }
}
