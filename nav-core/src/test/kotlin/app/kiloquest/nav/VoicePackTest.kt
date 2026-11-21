package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals

class VoicePackTest {
    private fun m(type: Int, street: String?, exit: Int? = null) = Maneuver(type, "x", null, null, listOfNotNull(street), 0.0, 10.0, exit)
    private fun route(vararg ms: Maneuver) = Route(Polyline(listOf(LatLon(0.0, 0.0), LatLon(0.0, 0.01))), ms.toList(), 1000.0, 100.0)

    @Test fun collectsNamesOnceInRouteOrder() {
        val r = route(m(15, "Hauptstraße"), m(10, "Bahnhofstraße"), m(15, "Hauptstraße"), m(10, "Am Markt"))
        assertEquals(listOf("Hauptstraße", "Bahnhofstraße"), VoicePack.neededNames(r, SpokenLang.DE) { false })
    }

    @Test fun skipsKnownClipsAndRoundaboutsAndDestination() {
        val r = route(m(26, "Ring", exit = 2), m(4, "Ziel"), m(15, "Hauptstraße"), m(10, "Bahnhofstraße"))
        assertEquals(listOf("Bahnhofstraße"), VoicePack.neededNames(r, SpokenLang.DE) { it == "Hauptstraße" })
    }

    @Test fun englishKeepsAllNamesAndLimitApplies() {
        val r = route(m(15, "Am Markt"), m(10, "High Street"), m(15, "Mill Lane"))
        assertEquals(listOf("Am Markt", "High Street", "Mill Lane"), VoicePack.neededNames(r, SpokenLang.EN) { false })
        assertEquals(listOf("Am Markt", "High Street"), VoicePack.neededNames(r, SpokenLang.EN, limit = 2) { false })
    }

    @Test fun hashTrimsAndIsStable() {
        assertEquals(12, VoicePack.nameHash("Hauptstraße").length)
        assertEquals(VoicePack.nameHash("Hauptstraße"), VoicePack.nameHash("  Hauptstraße\n"))
    }
}
