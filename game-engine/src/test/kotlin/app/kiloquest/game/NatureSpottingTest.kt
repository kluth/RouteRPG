package app.kiloquest.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NatureSpottingTest {
    private fun res(n: String) = javaClass.classLoader.getResource(n)!!.readText()

    @Test
    fun `builds species counts url`() {
        val u = NatureSpotting.buildUrl(67.8558, 20.2253, "de")
        assertTrue(u.startsWith("https://api.inaturalist.org/v1/observations/species_counts?"))
        assertTrue("lat=67.8558" in u && "locale=de" in u && "quality_grade=research" in u && "Aves%2CMammalia" in u)
    }

    @Test
    fun `parses real inaturalist response from Kiruna`() {
        val list = NatureSpotting.parse(res("inaturalist_species_counts_kiruna_de.json"))
        assertTrue(list.size >= 5)
        val names = list.map { it.name }
        assertTrue("Ren" in names && "Elch" in names, "names=$names")
        assertEquals(SpeciesGroup.MAMMAL, list.first { it.name == "Elch" }.group)
        assertEquals(SpeciesGroup.BIRD, list.first { it.name == "Elster" }.group)
    }

    @Test
    fun `entries without common name are skipped`() {
        val body = """{"results":[{"count":5,"taxon":{"name":"Xus yus","iconic_taxon_name":"Aves"}},
          {"count":3,"taxon":{"name":"Pica pica","preferred_common_name":"Elster","iconic_taxon_name":"Aves"}},
          {"count":9,"taxon":{"name":"Fungus","preferred_common_name":"Pilz","iconic_taxon_name":"Fungi"}}]}"""
        assertEquals(listOf("Elster"), NatureSpotting.parse(body).map { it.name })
    }

    @Test
    fun `pick is weighted and deterministic with a seed`() {
        val a = Species("A", "a", SpeciesGroup.BIRD, 1)
        val b = Species("B", "b", SpeciesGroup.BIRD, 1000)
        val picks = (1..50).map { NatureSpotting.pick(listOf(a, b), Random(it)) }
        assertTrue(picks.count { it == b } > 40)
        assertEquals(NatureSpotting.pick(listOf(a, b), Random(7)), NatureSpotting.pick(listOf(a, b), Random(7)))
        assertNull(NatureSpotting.pick(emptyList(), Random(1)))
    }

    @Test
    fun `resolve uses group specific text or falls back`() {
        val ph = Quest("nature", NatureSpotting.PENDING_KEY, QuestType.NATURE, 5000.0)
        val bird = NatureSpotting.resolve(ph, Species("Elster", "Pica pica", SpeciesGroup.BIRD, 11))
        assertEquals(NatureSpotting.BIRD_KEY, bird.textKey)
        assertEquals(listOf("Elster"), bird.args)
        assertEquals(NatureSpotting.MAMMAL_KEY, NatureSpotting.resolve(ph, Species("Elch", "Alces alces", SpeciesGroup.MAMMAL, 17)).textKey)
        assertEquals(RouteFacts.FALLBACK_KEY, NatureSpotting.resolve(ph, null).textKey)
    }
}
