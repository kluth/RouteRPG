package app.kiloquest.game

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KiloVoiceTest {
    private val assets = File("../app/src/main/assets/kilo_voice")
    private val res = File("../app/src/main/res")

    @Test fun hashIsStableAndIgnoresOuterWhitespace() {
        assertEquals("1d8da6bbd4a7".length, VoiceIndex.hash("Super gemacht!").length)
        assertEquals(VoiceIndex.hash("Super gemacht!"), VoiceIndex.hash("  Super gemacht!\n"))
        assertTrue(VoiceIndex.hash("a") != VoiceIndex.hash("b"))
    }

    @Test fun parseAndLookup() {
        val h = VoiceIndex.hash("Hallo!")
        val idx = VoiceIndex.parse("""{"version":1,"lines":{"de":{"$h":"hello"},"en":{}}}""")
        assertEquals("hello", idx.idFor("de", "Hallo!"))
        assertNull(idx.idFor("de", "Hallo"))
        assertNull(idx.idFor("fr", "Hallo!"))
        assertEquals(1, idx.count("de"))
    }

    @Test fun badManifestIsEmptyNotFatal() {
        assertEquals(0, VoiceIndex.parse("{kaputt").count("de"))
        assertEquals(0, VoiceIndex.parse(null).count("de"))
        assertEquals(0, VoiceIndex.parse("""{"lines":5}""").count("de"))
    }

    @Test fun praiseNeverRepeatsLastAndStaysInRange() {
        val r = Random(42)
        var last = 0
        repeat(500) {
            val n = KiloPraise.pick(true, last, r)
            assertTrue(n in 1..KiloPraise.PRAISE_LINES)
            assertTrue(n != last)
            last = n
        }
        last = 0
        repeat(500) {
            val n = KiloPraise.pick(false, last, r)
            assertTrue(n in 1..KiloPraise.RETRY_LINES)
            assertTrue(n != last)
            last = n
        }
    }

    @Test fun praiseIsDeterministicWithSeed() {
        assertEquals(KiloPraise.pick(true, 2, Random(7)), KiloPraise.pick(true, 2, Random(7)))
        assertEquals("kilo_retry_3", KiloPraise.key(false, 3))
    }

    // Jede Aufnahme im Manifest muss als Datei existieren und zum aktuellen Text in den Strings passen (sonst veraltet).
    @Test fun manifestMatchesFilesAndCurrentStrings() {
        val manifest = File(assets, "manifest.json")
        assertTrue(manifest.exists(), "manifest.json fehlt")
        val idx = VoiceIndex.parse(manifest.readText())
        for ((lang, dir) in mapOf("en" to "values", "de" to "values-de")) {
            val strings = readStrings(File(res, "$dir/strings.xml"))
            val manifestIds = manifestIds(manifest.readText(), lang)
            for ((hash, id) in manifestIds) {
                assertTrue(File(assets, "$lang/$id.ogg").exists(), "Datei fehlt: $lang/$id.ogg")
                val text = spokenText(id, strings)
                assertNotNull(text, "Keine Textquelle fuer $id ($lang)")
                assertEquals(hash, VoiceIndex.hash(text), "Aufnahme veraltet: $lang/$id (Text in strings.xml geaendert? tools/voice neu ausfuehren)")
            }
            assertTrue(idx.count(lang) == manifestIds.size)
        }
    }

    private fun spokenText(id: String, s: Map<String, String>): String? {
        if (!id.endsWith(".opts")) return s[id]
        val key = id.removeSuffix(".opts")
        val base = s[key] ?: return null
        val shapes = listOf(s["shape_circle_blue"], s["shape_square_orange"], s["shape_triangle_green"])
        val opts = generateSequence(1) { it + 1 }.map { s["${key}_opt$it"] }.takeWhile { it != null }.toList()
        return base + " " + opts.mapIndexed { i, o -> "${shapes[i % 3]}: $o" }.joinToString(". ")
    }

    private fun manifestIds(json: String, lang: String): Map<String, String> =
        Regex("\"$lang\":\\s*\\{([^}]*)\\}").find(json)?.groupValues?.get(1)
            ?.let { Regex("\"([0-9a-f]{12})\":\\s*\"([^\"]+)\"").findAll(it).associate { m -> m.groupValues[1] to m.groupValues[2] } }
            .orEmpty()

    private fun readStrings(f: File): Map<String, String> {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            n.attributes.getNamedItem("name").nodeValue to n.textContent.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
        }
    }
}
