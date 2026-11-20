package app.kiloquest.game

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** i18n-Wachposten: jede Quest-Vorlage muss in allen Sprachdateien der App uebersetzt sein. */
class CatalogI18nTest {
    private val resDir = File("../app/src/main/res")

    private fun keys(file: File): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toSet()

    @Test
    fun `all catalog texts exist in every language`() {
        val dirs = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        assertTrue(dirs.size >= 2, "expected values/ and values-de/")
        val needed = QuestCatalog.default.flatMap { listOf(it.textKey) + it.optionKeys } + RouteFacts.textKeys + WikiStories.textKeys + NatureSpotting.textKeys + LanguageCourses.textKeys + PlaneSpotting.textKeys
        for (d in dirs) {
            val have = keys(File(d, "strings.xml"))
            val missing = needed.filterNot { it in have }
            assertTrue(missing.isEmpty(), "${d.name} is missing: $missing")
        }
    }

    @Test
    fun `all languages define the same string keys`() {
        val dirs = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        val sets = dirs.associate { it.name to keys(File(it, "strings.xml")) }
        val all = sets.values.flatten().toSet()
        for ((name, set) in sets) assertTrue((all - set).isEmpty(), "$name lacks ${all - set}")
    }
}
