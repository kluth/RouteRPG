package app.kiloquest.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarsTest {
    @Test fun catalogIsRichAndUnique() {
        assertTrue(Avatars.all.size >= 80)
        assertEquals(Avatars.all.size, Avatars.all.map { it.emoji }.toSet().size, "no duplicates")
        for (c in AvatarCategory.entries) assertTrue(Avatars.byCategory(c).size >= 10, c.name)
        assertTrue(Avatars.all.any { it.emoji == Avatars.DEFAULT })
    }

    @Test fun catalogEntriesAreValidCustomEmoji() {
        for (a in Avatars.all) assertEquals(a.emoji, Avatars.sanitizeCustom(a.emoji), a.emoji)
    }

    @Test fun acceptsSingleEmojiVariants() {
        assertEquals("🦊", Avatars.sanitizeCustom(" 🦊 "))
        assertEquals("👍🏽", Avatars.sanitizeCustom("👍🏽"))
        assertEquals("🏴‍☠️", Avatars.sanitizeCustom("🏴‍☠️"))
        assertEquals("🇩🇰", Avatars.sanitizeCustom("🇩🇰"))
    }

    @Test fun onlyTheFirstEmojiCounts() {
        assertEquals("🦊", Avatars.sanitizeCustom("🦊🚗"))
        assertEquals("🚀", Avatars.sanitizeCustom("🚀 Rakete"))
    }

    @Test fun rejectsTextAndEmpty() {
        assertNull(Avatars.sanitizeCustom(""))
        assertNull(Avatars.sanitizeCustom("   "))
        assertNull(Avatars.sanitizeCustom("abc"))
        assertNull(Avatars.sanitizeCustom("1"))
        assertNull(Avatars.sanitizeCustom("x".repeat(40)))
        assertNotNull(Avatars.sanitizeCustom("⭐"))
    }

    @Test fun pointerEmojiTurnsToHeading() {
        // Rakete zeigt in der Schrift nach Nordosten (45 Grad): Fahrt nach Norden = -45 = 315, nach Osten = 45
        assertEquals(315f, Avatars.pose("🚀", 0f).rotationDegrees)
        assertEquals(45f, Avatars.pose("🚀", 90f).rotationDegrees)
        assertEquals(0f, Avatars.pose("✈️", 45f).rotationDegrees)
        assertEquals(false, Avatars.pose("🚀", 200f).mirrored)
    }

    @Test fun sideViewVehiclesMirrorInsteadOfTurningUpsideDown() {
        assertEquals(MarkerPose(0f, true), Avatars.pose("🚗", 90f))   // nach Osten: nach rechts schauen
        assertEquals(MarkerPose(0f, false), Avatars.pose("🚗", 270f)) // nach Westen: Standard (links)
        assertEquals(0f, Avatars.pose("🚗", 180f).rotationDegrees)
    }

    @Test fun uprightAndUnknownHeadingStayPut() {
        assertEquals(MarkerPose(0f, false), Avatars.pose("🦊", 123f))
        assertEquals(MarkerPose(0f, false), Avatars.pose("🚀", null))
        assertEquals(MarkerPose(0f, false), Avatars.pose("🦄", 10f))
        assertEquals(Orient.UPRIGHT, Avatars.orientOf("🌈"))
        assertEquals(Orient.SIDE_LEFT, Avatars.orientOf("🚌"))
    }

    @Test fun headingIsNormalized() {
        assertEquals(Avatars.pose("🚀", 45f), Avatars.pose("🚀", 405f))
        assertEquals(Avatars.pose("🚀", 350f), Avatars.pose("🚀", -10f))
    }

    @Test fun threeDModelsAreMappedToCatalogEmoji() {
        val withModel = Avatars.all.filter { Avatars.model3d(it.emoji) != null }
        assertTrue(withModel.size >= 55, "figures with 3D models: ${withModel.size}")
        assertEquals(Avatars.models3d.size, withModel.size, "every 3D model is reachable from the catalog, one emoji each")
        assertEquals(Avatars.models3d.size, Avatars.models3d.map { it.id }.toSet().size)
        assertNull(Avatars.modelOf("🦊"))
        assertEquals("sedan", Avatars.modelOf("🚗"))
        assertEquals(ModelKind.WALKER, Avatars.model3d("🐱")!!.kind)
        assertEquals(ModelKind.VEHICLE, Avatars.model3d("🚒")!!.kind)
    }

    @Test fun everyModelHasAllRotationFrames() {
        val dir = java.io.File("../app/src/main/assets/figures3d")
        for (m in Avatars.models3d) for (name in m.assetNames()) {
            val f = java.io.File(dir, name)
            assertTrue(f.isFile && f.length() > 300, "missing sprite $name")
        }
    }

    @Test fun walkersStoreHalfTheHeadingsAndMirrorTheRest() {
        val cat = Avatars.model3d("🐱")!!
        assertEquals(24, cat.headings); assertEquals(6, cat.walkFrames)
        assertEquals(13 * 6, cat.assetNames().size)
        assertTrue("cat_h12_w5.webp" in cat.assetNames())
        assertEquals(36, Avatars.model3d("🚗")!!.assetNames().size)
    }

    @Test fun headingIndexScalesWithHeadings() {
        assertEquals(6, Avatars.headingIndex(90f, 24))
        assertEquals(12, Avatars.headingIndex(180f, 24))
        assertEquals(18, Avatars.headingIndex(270f, 24))
        assertEquals(0, Avatars.headingIndex(353f, 24))
        assertEquals(6, Avatars.headingIndex(null, 24))
        assertEquals(9, Avatars.headingIndex(null, 36))
    }

    @Test fun frameFollowsHeading() {
        assertEquals(0, Avatars.frameFor(0f))
        assertEquals(9, Avatars.frameFor(90f))
        assertEquals(18, Avatars.frameFor(180f))
        assertEquals(27, Avatars.frameFor(270f))
        assertEquals(0, Avatars.frameFor(359f))   // rundet auf Norden
        assertEquals(0, Avatars.frameFor(364f))
        assertEquals(35, Avatars.frameFor(-10f))
        assertEquals(9, Avatars.frameFor(null))
    }
}
