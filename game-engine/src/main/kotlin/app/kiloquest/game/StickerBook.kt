package app.kiloquest.game

import kotlin.random.Random

data class Sticker(val id: String, val emoji: String)

/** Sammelalbum. Sticker werden zufaellig aus den noch fehlenden vergeben. */
class StickerBook(collected: Set<String> = emptySet()) {
    private val _collected = collected.filterTo(LinkedHashSet()) { id -> ALL.any { it.id == id } }
    val collected: Set<String> get() = _collected
    val isComplete: Boolean get() = _collected.size == ALL.size

    /** Vergibt einen neuen Sticker oder null, wenn das Album voll ist. */
    fun award(random: Random): Sticker? {
        val missing = ALL.filter { it.id !in _collected }
        if (missing.isEmpty()) return null
        val s = missing[random.nextInt(missing.size)]
        _collected += s.id
        return s
    }

    companion object {
        /** Emojis sind sprachneutral, daher keine Uebersetzung noetig. */
        val ALL: List<Sticker> = listOf(
            Sticker("fox", "🦊"), Sticker("owl", "🦉"), Sticker("moose", "🫎"), Sticker("reindeer", "🦌"),
            Sticker("bear", "🐻"), Sticker("wolf", "🐺"), Sticker("seal", "🦭"), Sticker("whale", "🐋"),
            Sticker("hedgehog", "🦔"), Sticker("swan", "🦢"), Sticker("rabbit", "🐰"), Sticker("penguin", "🐧"),
            Sticker("snowflake", "❄️"), Sticker("aurora", "🌌"), Sticker("castle", "🏰"), Sticker("ship", "🚢"),
        )
    }
}
