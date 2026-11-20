package app.kiloquest.nav

enum class PackCategory { DOCUMENTS, CLOTHES, HEALTH, CAR, KIDS, CAMPING }

/** Eintrag der Packliste; die Anzeigetexte stehen in den Ressourcen (Schluessel = Name). */
enum class PackItem(val category: PackCategory) {
    ID_PAPERS(PackCategory.DOCUMENTS), BOOKINGS(PackCategory.DOCUMENTS), INSURANCE_CARD(PackCategory.DOCUMENTS), CASH_CARDS(PackCategory.DOCUMENTS),
    RAIN_JACKET(PackCategory.CLOTHES), SPARE_CLOTHES(PackCategory.CLOTHES), WARM_LAYERS(PackCategory.CLOTHES), WINTER_GEAR(PackCategory.CLOTHES),
    SUN_PROTECTION(PackCategory.CLOTHES), SWIMWEAR(PackCategory.CLOTHES),
    FIRST_AID(PackCategory.HEALTH), MEDICINE(PackCategory.HEALTH), TRAVEL_SICKNESS(PackCategory.HEALTH), MOSQUITO(PackCategory.HEALTH),
    CHARGERS(PackCategory.CAR), SNACKS_WATER(PackCategory.CAR), WARNING_TRIANGLE(PackCategory.CAR), TRASH_BAGS(PackCategory.CAR),
    PILLOWS_BLANKETS(PackCategory.KIDS), CUDDLY_TOY(PackCategory.KIDS), AUDIO_BOOKS(PackCategory.KIDS), DIAPERS(PackCategory.KIDS), GAMES_BOOKS(PackCategory.KIDS),
    TENT_GEAR(PackCategory.CAMPING), SLEEPING_BAGS(PackCategory.CAMPING), HEADLAMP(PackCategory.CAMPING),
}

/** Wie eine Reise aussieht, soweit sie die Packliste beeinflusst. */
data class PackContext(
    /** Breitengrad des Ziels; ab 60 gilt es als kalt. */
    val destinationLat: Double?,
    /** Monat 1-12 der Abreise. */
    val month: Int,
    val nights: Int,
    val drivingDays: Int,
    val smallKids: Boolean,
    val camping: Boolean,
)

/** Regelbasierte Packliste. Deterministisch, ohne Zufall; die Reihenfolge folgt der Reihenfolge von [PackItem]. */
object PackingList {
    fun build(c: PackContext): List<PackItem> {
        val cold = (c.destinationLat ?: 0.0) >= 60.0 || (c.month in listOf(11, 12, 1, 2, 3) && (c.destinationLat ?: 50.0) >= 40.0)
        val winter = c.month in listOf(12, 1, 2) && (c.destinationLat ?: 50.0) >= 50.0
        val summer = c.month in 6..8
        val items = LinkedHashSet<PackItem>()
        items += listOf(PackItem.ID_PAPERS, PackItem.BOOKINGS, PackItem.INSURANCE_CARD, PackItem.CASH_CARDS)
        items += listOf(PackItem.RAIN_JACKET, PackItem.SPARE_CLOTHES, PackItem.FIRST_AID, PackItem.MEDICINE)
        items += listOf(PackItem.CHARGERS, PackItem.SNACKS_WATER, PackItem.WARNING_TRIANGLE, PackItem.TRASH_BAGS)
        items += listOf(PackItem.TRAVEL_SICKNESS, PackItem.GAMES_BOOKS)
        if (cold) items += PackItem.WARM_LAYERS
        if (winter) items += PackItem.WINTER_GEAR
        if (summer || (c.destinationLat ?: 50.0) < 45.0) items += PackItem.SUN_PROTECTION
        if (summer) { items += PackItem.SWIMWEAR; items += PackItem.MOSQUITO }
        if ((c.destinationLat ?: 0.0) >= 60.0 && c.month in 5..9) items += PackItem.MOSQUITO // Muecken im Norden
        if (c.drivingDays >= 2 || c.nights >= 1) items += PackItem.PILLOWS_BLANKETS
        if (c.drivingDays >= 2) items += PackItem.AUDIO_BOOKS
        if (c.smallKids) { items += PackItem.CUDDLY_TOY; items += PackItem.DIAPERS }
        if (c.camping) items += listOf(PackItem.TENT_GEAR, PackItem.SLEEPING_BAGS, PackItem.HEADLAMP)
        return PackItem.entries.filter { it in items }
    }

    /** Anteil abgehakter Eintraege (0..1); unbekannte Namen werden ignoriert. */
    fun progress(items: List<PackItem>, packed: List<String>): Double =
        if (items.isEmpty()) 0.0 else items.count { it.name in packed }.toDouble() / items.size
}
