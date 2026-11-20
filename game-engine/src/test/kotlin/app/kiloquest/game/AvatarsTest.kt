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
        val withModel = Avatars.all.filter { Avatars.modelOf(it.emoji) != null }
        assertTrue(withModel.size >= 20, "vehicles with 3D models: ${withModel.size}")
        assertEquals(Avatars.modelIds.size, withModel.map { Avatars.modelOf(it.emoji) }.toSet().size, "one model per emoji")
        assertNull(Avatars.modelOf("🦊"))
        assertEquals("sedan", Avatars.modelOf("🚗"))
    }

    @Test fun everyModelHasAllRotationFrames() {
        val dir = java.io.File("../app/src/main/assets/figures3d")
        for (id in Avatars.modelIds) for (i in 0 until Avatars.FRAMES_3D) {
            val f = java.io.File(dir, "${id}_${i.toString().padStart(2, '0')}.webp")
            assertTrue(f.isFile && f.length() > 500, "missing sprite ${f.name}")
        }
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
