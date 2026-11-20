package app.kiloquest

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class MemoryBookBuilderTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()

    // Das eigentliche PDF-Zeichnen laeuft unter Robolectric nicht (PdfDocument ist dort nicht verfuegbar: "document is closed");
    // die Logik (Tage, Fotoseiten, Routenskizze) ist in MemoryBookTest (nav-core) getestet. Das PDF selbst braucht einen Geraetetest.
    @Test fun emptyDiaryGivesNoBook() {
        assertNull(MemoryBookBuilder.build(ctx, emptyList()))
    }
}
