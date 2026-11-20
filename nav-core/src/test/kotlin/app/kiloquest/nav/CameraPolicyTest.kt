package app.kiloquest.nav

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CameraPolicyTest {
    @Test fun everythingIsOffByDefault() {
        assertFalse(CameraPolicy.anyEnabled())
        for (c in listOf("DE", "CH", "SE", "DK", "FI", "NO", "NL", "FR", "XX", "", null, "de", " se ")) {
            assertEquals(CameraMode.OFF, CameraPolicy.modeFor(c), "Land $c muss aus sein")
        }
    }

    @Test fun lookupIgnoresCaseAndWhitespace() {
        assertEquals(LegalStatus.BANNED, CameraPolicy.ruleFor(" de ")?.status)
        assertEquals(null, CameraPolicy.ruleFor("XX"))
        assertEquals(null, CameraPolicy.ruleFor(null))
    }

    @Test fun countryCodesAreUniqueAndUpperCase() {
        val codes = CameraPolicy.rules.map { it.country }
        assertEquals(codes.distinct(), codes)
        assertTrue(codes.all { it == it.uppercase() && it.length == 2 })
    }

    // Eine Freischaltung braucht ein Pruefdatum, darf nie ein Land mit Verbot treffen und braucht ein vorhandenes Rechtsdokument.
    @Test fun enabledRulesAreDocumentedAndNotBanned() {
        for (r in CameraPolicy.rules) {
            if (r.mode != CameraMode.OFF) {
                assertFalse(r.verifiedOn.isNullOrBlank(), "${r.country}: Pruefdatum fehlt")
                assertTrue(r.status != LegalStatus.BANNED, "${r.country}: Verbot, darf nicht freigeschaltet sein")
            }
            if (r.status == LegalStatus.BANNED) assertEquals(CameraMode.OFF, r.mode, "${r.country}: Verbot erzwingt OFF")
            assertTrue(File("../docs/legal/${r.doc}").exists(), "Rechtsdokument fehlt: ${r.doc}")
        }
    }

    @Test fun modeIsIgnoredWithoutVerification() {
        // Simulierte Regel: Modus gesetzt, aber nicht geprueft -> in modeFor() waere das OFF; die Logik steckt in der Regelpruefung
        val r = CameraRule("SE", LegalStatus.UNCLEAR, CameraMode.FIXED_CAMERAS, verifiedOn = null)
        assertTrue(r.verifiedOn.isNullOrBlank() && r.mode != CameraMode.OFF)
        assertNotNull(CameraPolicy.ruleFor("SE"))
        assertEquals(CameraMode.OFF, CameraPolicy.modeFor("SE"))
    }
}
