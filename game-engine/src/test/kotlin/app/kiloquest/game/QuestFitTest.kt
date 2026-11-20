package app.kiloquest.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuestFitTest {
    private val day = QuestContext(55.0, 10.0, "DK", dark = false)
    private val night = day.copy(dark = true)
    private fun tpl(id: String, countries: Set<String> = emptySet(), time: TimeNeed = TimeNeed.ANY, minLat: Double? = null, key: String = "quest_$id") =
        QuestTemplate(id, QuestType.SPOTTING, setOf(AgeBand.EXPLORER), key, countries = countries, time = time, minLat = minLat)

    @Test fun fitsRespectsCountryTimeAndLatitude() {
        assertTrue(QuestFit.fits(tpl("a", countries = setOf("DK")), day))
        assertFalse(QuestFit.fits(tpl("a", countries = setOf("SE")), day))
        assertFalse(QuestFit.fits(tpl("a", countries = setOf("DK")), day.copy(country = null)))
        assertFalse(QuestFit.fits(tpl("a", time = TimeNeed.DAY), night))
        assertFalse(QuestFit.fits(tpl("a", time = TimeNeed.NIGHT), day))
        assertFalse(QuestFit.fits(tpl("a", minLat = 62.0), day))
        assertTrue(QuestFit.fits(tpl("a", minLat = 62.0), day.copy(lat = 67.0)))
    }

    @Test fun neverPicksTemplatesThatDoNotFit() {
        val pool = listOf(tpl("dk", countries = setOf("DK")), tpl("se", countries = setOf("SE")), tpl("n", time = TimeNeed.NIGHT), tpl("any"))
        val rng = Random(7)
        repeat(200) {
            val t = assertNotNull(QuestFit.choose(pool, day, emptyMap(), emptyMap(), rng, maxStaticPerTrip = 1000))
            assertTrue(t.id == "dk" || t.id == "any", t.id)
        }
    }

    @Test fun staticOnlyOncePerTripButDynamicUnlimited() {
        val pool = listOf(tpl("s"), tpl("live", key = "x_pending"))
        val used = mapOf("s" to 1)
        repeat(50) { assertEquals("live", QuestFit.choose(pool, day, emptyMap(), used, Random(it))!!.id) }
    }

    @Test fun generatorHonoursContextAndDoesNotRepeatStatics() {
        val gen = QuestGenerator(QuestCatalog.default)
        val quests = gen.generate(
            routeLengthMeters = 300_000.0, ageBand = AgeBand.EXPLORER, seed = 3,
            contextAt = { m -> QuestContext(55.0, 10.0, "DK", dark = m > 150_000) },
        )
        val byId = QuestCatalog.default.associateBy { it.id }
        val statics = quests.map { byId.getValue(it.templateId) }.filter { !it.dynamic }
        assertEquals(statics.size, statics.map { it.id }.toSet().size, "static quests must not repeat within a trip")
        for (q in quests) {
            val t = byId.getValue(q.templateId)
            assertTrue(t.countries.isEmpty() || "DK" in t.countries, t.id)
            if (q.triggerMeters <= 150_000) assertTrue(t.time != TimeNeed.NIGHT, t.id) else assertTrue(t.time != TimeNeed.DAY, t.id)
        }
        assertTrue(quests.size > statics.size, "long routes are filled with live quests")
    }

    @Test fun countryGuessIsConservative() {
        assertEquals("DK", CountryGuess.guess(55.68, 12.4))
        assertEquals("SE", CountryGuess.guess(59.33, 18.07))
        assertEquals("DE", CountryGuess.guess(53.55, 9.99))
        assertEquals(null, CountryGuess.guess(51.43, 6.76)) // Duisburg: Grenznaehe, lieber unbekannt
    }
}
