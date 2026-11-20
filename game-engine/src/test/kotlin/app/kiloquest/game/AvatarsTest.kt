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
}
