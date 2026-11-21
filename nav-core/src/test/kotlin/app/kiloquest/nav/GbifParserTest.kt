package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GbifParserTest {
    private val sample = """
    {"offset":0,"limit":3,"endOfRecords":true,"count":4,"results":[
      {"key":1,"species":"Vulpes vulpes","scientificName":"Vulpes vulpes (Linnaeus, 1758)","vernacularName":"Red Fox","license":"http://creativecommons.org/licenses/by/4.0/","decimalLatitude":51.43},
      {"key":2,"species":"Sciurus vulgaris","license":"CC0_1_0","extra":{"a":1}},
      {"key":3,"scientificName":"Aves","license":"CC0_1_0"},
      {"key":4,"species":"Pica pica","vernacularName":"  ","license":"CC_BY_4_0"}
    ]}
    """.trimIndent()

    @Test fun `parses results and skips entries without species`() {
        val r = GbifParser.parse(sample)
        assertEquals(listOf("Vulpes vulpes", "Sciurus vulgaris", "Pica pica"), r.map { it.species })
        assertEquals("Red Fox", r[0].vernacularName)
        assertNull(r[1].vernacularName)
        assertNull(r[2].vernacularName)
        assertEquals("CC0_1_0", r[1].license)
    }

    @Test fun `broken or empty body gives empty list`() {
        assertTrue(GbifParser.parse("nope").isEmpty())
        assertTrue(GbifParser.parse("{}").isEmpty())
    }

    @Test fun `url uses rounded position and free licenses only`() {
        val u = GbifParser.buildUrl(51.4318, 6.7625, limit = 20)
        assertTrue(u.startsWith("https://api.gbif.org/v1/occurrence/search?"))
        assertTrue("decimalLatitude=51.3,51.5" in u, u)
        assertTrue("decimalLongitude=6.7,6.9" in u, u)
        assertTrue("license=CC0_1_0&license=CC_BY_4_0" in u)
        assertTrue(u.endsWith("limit=20"))
        assertTrue("51.4318" !in u)
    }
}
