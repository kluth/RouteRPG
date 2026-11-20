package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflinePackTest {
    private fun route(): Route {
        val line = Polyline((0..50).map { LatLon(50.0 + it * 0.01, 8.0 + it * 0.002) })
        val m = listOf(
            Maneuver(1, "Los", "Gleich los", null, listOf("A1"), 0.0, 4000.0),
            Maneuver(26, "Kreisel", null, "Danach", emptyList(), 4000.0, 1000.0, roundaboutExit = 2),
        )
        return Route(line, m, line.lengthMeters, 3600.0)
    }

    private fun pack(): OfflinePack {
        val speed = SpeedProfile().also { it.addWindow(0.0, 5000.0, listOf(SpeedSegment(0.0, 2000.0, 50), SpeedSegment(2000.0, 5000.0, SpeedProfile.UNLIMITED))) }
        val toilets = listOf(
            ToiletStop(ToiletKind.REST_AREA, "Rasthof", LatLon(50.1, 8.02), true, setOf(StopFeature.TOILET, StopFeature.PLAYGROUND), "24/7"),
            ToiletStop(ToiletKind.FUEL, null, LatLon(50.2, 8.04)),
        )
        val lanes = listOf(LaneGuidance(3900.0, listOf(Lane(listOf("left"), valid = true, active = false), Lane(listOf("straight"), valid = false, active = false))))
        val quests = listOf(PackQuest("t1", "key", "SPOTTING", 1500.0, "CHECK", points = 20, args = listOf("Köln")))
        return OfflinePackCodec.build("id1", "Tag 1", "Hannover", 20000, route(), speed, listOf(0.0 to "DE", 3000.0 to "DK"), toilets, lanes, quests, 120)
    }

    @Test fun roundTripKeepsRouteAndManeuvers() {
        val p = OfflinePackCodec.decode(OfflinePackCodec.encode(pack()))!!
        val r = OfflinePackCodec.route(p)
        assertEquals(route().line.points.size, r.line.points.size)
        assertEquals(route().lengthMeters, r.lengthMeters, 1.0)
        assertEquals(2, r.maneuvers.size)
        assertEquals(2, r.maneuvers[1].roundaboutExit)
        assertEquals("Gleich los", r.maneuvers[0].verbalPre)
        assertEquals(50.0, r.line.points.first().lat, 1e-5)
    }

    @Test fun speedProfileAndCountriesSurvive() {
        val p = OfflinePackCodec.decode(OfflinePackCodec.encode(pack()))!!
        val prof = OfflinePackCodec.speedProfile(p)
        assertEquals(50, prof.limitAt(1000.0))
        assertEquals(SpeedProfile.UNLIMITED, prof.limitAt(3000.0))
        assertEquals(5000.0, prof.coveredUntilMeters)
        val c = OfflinePackCodec.countryTimeline(p)!!
        assertEquals("DE", c.countryAt(10.0))
        assertEquals("DK", c.countryAt(3500.0))
    }

    @Test fun toiletsAndLanesSurvive() {
        val p = OfflinePackCodec.decode(OfflinePackCodec.encode(pack()))!!
        val t = OfflinePackCodec.toilets(p)
        assertEquals(2, t.size)
        assertTrue(t[0].verified && StopFeature.PLAYGROUND in t[0].features && t[0].openingHours == "24/7")
        val l = OfflinePackCodec.lanes(p).single()
        assertEquals(3900.0, l.atMeters)
        assertEquals(LaneHint.LEFT, l.hint())
    }

    @Test fun badOrNewerDataGivesNull() {
        assertNull(OfflinePackCodec.decode("{kaputt"))
        assertNull(OfflinePackCodec.decode(null))
        val newer = OfflinePackCodec.encode(pack().copy(version = 99))
        assertNull(OfflinePackCodec.decode(newer))
    }

    @Test fun unknownEnumNamesAreSkippedNotFatal() {
        val p = pack().copy(toilets = listOf(PackToilet("UFO", "x", 1.0, 1.0), PackToilet("TOILETS", "ok", 1.0, 1.0, features = listOf("XYZ", "TOILET"))))
        val t = OfflinePackCodec.toilets(p)
        assertEquals(1, t.size)
        assertEquals(setOf(StopFeature.TOILET), t.single().features)
    }

    @Test fun metaCountsContent() {
        val m = OfflinePackCodec.decodeMeta(OfflinePackCodec.encodeMeta(OfflinePackCodec.meta(pack())))
        assertNotNull(m)
        assertEquals(1, m.quests)
        assertEquals(2, m.toilets)
        assertEquals("Hannover", m.destinationName)
    }

    @Test fun emptySpeedGivesEmptyProfile() {
        val p = pack().copy(speed = emptyList())
        assertNull(OfflinePackCodec.speedProfile(p).limitAt(10.0))
    }
}
