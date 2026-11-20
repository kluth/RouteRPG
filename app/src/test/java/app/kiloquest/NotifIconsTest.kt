package app.kiloquest

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.kiloquest.nav.Glyph
import app.kiloquest.nav.IconPalette
import app.kiloquest.nav.IconSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotifIconsTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()

    @Test fun everyGlyphHasASmallIconAndGeometry() {
        for (g in Glyph.entries) {
            assertTrue(g.name, NotifIcons.smallRes(g) != 0)
            assertEquals("ic_stat_${g.id}", ctx.resources.getResourceEntryName(NotifIcons.smallRes(g)))
            assertNotNull(g.name, IconGeometry.GLYPHS[g.id])
        }
    }

    @Test fun everyPaletteIsKnownToTheGeometry() {
        for (p in IconPalette.entries) assertNotNull(p.name, IconGeometry.PALETTES[p.name])
    }

    @Test fun largeIconIsRenderedAndCached() {
        val spec = IconSpec(Glyph.PAUSE, IconPalette.NIGHT)
        val a = NotifIcons.large(spec, 96)
        assertEquals(96, a.width); assertEquals(96, a.height)
        assertTrue(NotifIcons.large(spec, 96) === a)
    }
}
