package app.kiloquest.nav

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpokenPlanTest {
    private fun m(type: Int, street: String? = null, exit: Int? = null, at: Double = 0.0) =
        Maneuver(type, "Valhalla-Text", "Valhalla vorher", null, listOfNotNull(street), at, 100.0, exit)

    @Test fun distanceRoundsToFiftyBetween100And500() {
        assertEquals(500, SpokenPlan.roundDistance(480.0))
        assertEquals(300, SpokenPlan.roundDistance(310.0))
        assertEquals(100, SpokenPlan.roundDistance(70.0))
        assertEquals(500, SpokenPlan.roundDistance(900.0))
    }

    @Test fun germanTurnWithStreet() {
        val s = SpokenPlan.pre(m(15, "Hauptstraße"), 310.0, SpokenLang.DE)!!
        assertEquals("In 300 Metern links abbiegen auf die Hauptstraße.", s.text)
        assertEquals(listOf(Slot.Clip("nav_in_300m"), Slot.Clip("nav_left"), Slot.Clip("nav_onto_die"), Slot.Name("Hauptstraße")), s.slots)
    }

    @Test fun englishTurnWithStreet() {
        val s = SpokenPlan.pre(m(10, "Main Street"), 200.0, SpokenLang.EN)!!
        assertEquals("In 200 meters turn right onto Main Street.", s.text)
    }

    @Test fun nowVariantHasNoDistance() {
        assertEquals("Rechts abbiegen auf den Mühlenweg.", SpokenPlan.now(m(10, "Mühlenweg"), SpokenLang.DE)!!.text)
    }

    @Test fun unsureGermanArticleDropsTheName() {
        val s = SpokenPlan.pre(m(10, "Am Markt"), 150.0, SpokenLang.DE)!!
        assertEquals("In 150 Metern rechts abbiegen.", s.text)
        assertEquals(listOf(Slot.Clip("nav_in_150m"), Slot.Clip("nav_right")), s.slots)
    }

    @Test fun articlesBySuffix() {
        assertEquals(GermanArticle.DIE, germanArticle("Kölner Straße"))
        assertEquals(GermanArticle.DEN, germanArticle("Südring"))
        assertEquals(GermanArticle.DAS, germanArticle("Rheinufer"))
        assertNull(germanArticle("Zum Wald"))
        assertNull(germanArticle("Linde"))
    }

    @Test fun motorwayRefsUseDie() {
        assertEquals("In 500 Metern die Ausfahrt rechts nehmen auf die A 44.", SpokenPlan.pre(m(20, "A 44"), 500.0, SpokenLang.DE)!!.text)
    }

    @Test fun roundaboutNeedsKnownExitAndHasNoStreet() {
        val s = SpokenPlan.pre(m(26, "Ring", exit = 2), 200.0, SpokenLang.DE)!!
        assertEquals("In 200 Metern in den Kreisverkehr fahren und die zweite Ausfahrt nehmen.", s.text)
        assertNull(SpokenPlan.pre(m(26, null, exit = null), 200.0, SpokenLang.DE))
        assertNull(SpokenPlan.pre(m(26, null, exit = 9), 200.0, SpokenLang.DE))
    }

    @Test fun destinationAndDepartFallBackToValhalla() {
        assertNull(SpokenPlan.now(m(4), SpokenLang.DE))
        assertNull(SpokenPlan.pre(m(1), 300.0, SpokenLang.DE))
    }

    @Test fun overlongOrMultiNamesAreDropped() {
        val s = SpokenPlan.now(m(15, "Bundesstraße 3/Alte Poststraße"), SpokenLang.DE)!!
        assertEquals("Links abbiegen.", s.text)
    }

    @Test fun langFromTag() {
        assertEquals(SpokenLang.DE, SpokenLang.of("de-DE"))
        assertEquals(SpokenLang.EN, SpokenLang.of("EN"))
        assertNull(SpokenLang.of("fr-FR"))
        assertNull(SpokenLang.of(null))
    }

    @Test fun vocabMatchesRecordingFile() {
        val file = File("../tools/voice/nav_vocab.json")
        assertTrue(file.exists(), "tools/voice/nav_vocab.json fehlt")
        val json = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(NavVocab.ALL.keys, json.keys)
        for ((id, texts) in NavVocab.ALL) {
            val o = json.getValue(id).jsonObject
            assertEquals(texts.getValue(SpokenLang.DE), o.getValue("de").jsonPrimitive.content, "de $id")
            assertEquals(texts.getValue(SpokenLang.EN), o.getValue("en").jsonPrimitive.content, "en $id")
        }
    }

    @Test fun plannerUsesComposedOnlyWhenAsked() {
        val route = Route(Polyline(listOf(LatLon(0.0, 0.0), LatLon(0.0, 0.01))), listOf(m(15, "Hauptstraße", at = 0.0)), 1000.0, 100.0)
        val state = GuidanceState(0.0, 0.0, false, 0, 300.0, 1000.0, false)
        val legacy = AnnouncementPlanner(route, spokenLang = SpokenLang.DE).next(state)
        assertEquals("Valhalla vorher", legacy)
        val composed = AnnouncementPlanner(route, spokenLang = SpokenLang.DE).nextSpoken(state)
        assertNotNull(composed)
        assertEquals("In 300 Metern links abbiegen auf die Hauptstraße.", composed.text)
        assertTrue(composed.slots.isNotEmpty())
        // ohne Sprache: Text von Valhalla, keine Slots
        val plain = AnnouncementPlanner(route).nextSpoken(state)
        assertEquals(Spoken("Valhalla vorher"), plain)
    }
}

class DropMissingNamesTest {
    private val slots = listOf(Slot.Clip("nav_in_300m"), Slot.Clip("nav_left"), Slot.Clip("nav_onto_die"), Slot.Name("Hauptstraße"))

    @Test fun dropsNameAndArticleWhenNoClip() {
        assertEquals(listOf(Slot.Clip("nav_in_300m"), Slot.Clip("nav_left")), dropMissingNames(slots) { false })
    }

    @Test fun keepsEverythingWhenNameHasClip() {
        assertEquals(slots, dropMissingNames(slots) { it == "Hauptstraße" })
    }

    @Test fun otherClipsBeforeANameAreNotRemoved() {
        val s = listOf(Slot.Clip("nav_left"), Slot.Name("X"))
        assertEquals(listOf(Slot.Clip("nav_left")), dropMissingNames(s) { false })
    }

    @Test fun allVocabHasRecordings() {
        // Aufnahmen der Bausteine liegen in den Assets (Spec 016); fehlen sie, faellt die App auf die Geraete-Stimme zurueck
        val dir = java.io.File("../app/src/main/assets/kilo_voice")
        val missing = NavVocab.ALL.keys.flatMap { id -> listOf("de", "en").filter { !java.io.File(dir, "$it/$id.ogg").exists() }.map { "$it/$id" } }
        assertTrue(missing.isEmpty(), "Aufnahmen fehlen: $missing")
    }
}
