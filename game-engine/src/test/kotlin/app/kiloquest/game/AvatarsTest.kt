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
}
