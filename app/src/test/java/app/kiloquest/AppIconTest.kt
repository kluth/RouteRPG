package app.kiloquest

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jede waehlbare Icon-Variante braucht einen Alias im Manifest und alle Ressourcen; genau ein Alias ist standardmaessig aktiv. */
class AppIconTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val res = File("src/main/res")

    @Test fun aliasesMatchVariants() {
        val names = Regex("""<activity-alias\s+android:name="\.(\w+)"\s+android:enabled="(true|false)"""").findAll(manifest).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals(AppIcon.Variant.entries.map { it.alias }, names.map { it.first })
        assertEquals(listOf("IconDefault"), names.filter { it.second == "true" }.map { it.first })
    }

    @Test fun onlyAliasesHaveALauncherEntry() {
        val launcher = Regex("android.intent.category.LAUNCHER").findAll(manifest).count()
        assertEquals(AppIcon.Variant.entries.size, launcher)
    }

    @Test fun resourcesExistForEveryVariant() {
        for (v in listOf("", "_night", "_sunset", "_forest", "_mono")) {
            for (f in listOf("drawable/ic_launcher${v}_background.xml", "drawable/ic_launcher${v}_foreground.xml", "mipmap-anydpi-v26/ic_launcher$v.xml")) assertTrue(f, File(res, f).exists())
        }
        for (n in listOf("default", "night", "sunset", "forest", "mono")) assertTrue(n, File(res, "drawable-nodpi/icon_preview_$n.png").exists())
        assertTrue(File(res, "drawable/ic_launcher_monochrome.xml").exists())
        for (g in listOf("star", "arrow", "pause", "wc", "camera", "calendar", "warning", "cross", "book", "flag", "moon", "snow", "sun")) assertTrue(g, File(res, "drawable/ic_stat_$g.xml").exists())
        for (d in listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")) assertTrue(File(res, "mipmap-$d/ic_launcher.png").exists())
    }

    @Test fun everyVariantHasNamesInBothLanguages() {
        for (dir in listOf("values", "values-de")) {
            val text = File(res, "$dir/strings.xml").readText()
            AppIcon.Variant.entries.forEach { assertTrue("$dir ${it.name}", Regex("name=\"icon_${it.name.lowercase()}\"").containsMatchIn(text)) }
        }
    }
}
