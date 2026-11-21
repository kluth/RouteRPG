package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripOverviewDatexTest {
    // Gerade Linie nach Osten, ~ 1 Grad Laenge = ~70 km bei lat 50 -> 4 Grad ~ 286 km
    private val route = Route(
        Polyline((0..40).map { LatLon(50.0, 8.0 + it * 0.1) }), emptyList(),
        lengthMeters = Polyline((0..40).map { LatLon(50.0, 8.0 + it * 0.1) }).lengthMeters, durationSeconds = 3 * 3600.0 * 2,
    )

    @Test
    fun `overview splits into days with keys reminders and lodging area`() {
        val days = TripOverview.build(route, startDay = 20_000, booked = emptyList(), budgetSeconds = 3 * 3600.0)
        assertEquals(2, days.size)
        assertEquals("origin", days[0].startKey)
        assertEquals("night:1", days[0].endKey)
        assertEquals("night:1", days[1].startKey)
        assertEquals("destination", days[1].endKey)
        assertEquals(route.lengthMeters, days.sumOf { it.distanceMeters }, 1.0)
        assertEquals(20_001, days[1].epochDay)
        // Vorabend 18:00 UTC des Vortags
        assertEquals(19_999L * 86_400_000L + 18 * 3_600_000L, days[0].reminderAtMillis)
        assertTrue(days[0].lodgingSearch != null)
        assertNull(days[1].lodgingSearch)
        assertEquals(TripOverview.questSuggestion(days[0].distanceMeters), days[0].questSuggestion)
    }

    @Test
    fun `booked stay becomes day end`() {
        val stay = BookedStay("Hotel X", LatLon(50.0, 10.0))
        val days = TripOverview.build(route, 20_000, listOf(stay), 8 * 3600.0)
        assertEquals("stay:Hotel X", days[0].endKey)
        assertTrue(days[0].endsAtBooking)
    }

    @Test
    fun `quest suggestion is clamped`() {
        assertEquals(2, TripOverview.questSuggestion(1_000.0))
        assertEquals(10, TripOverview.questSuggestion(200_000.0))
        assertEquals(40, TripOverview.questSuggestion(5_000_000.0))
    }

    @Test
    fun `lodging query and parse`() {
        val q = LodgingQuery.build(LodgingSearchArea(LatLon(50.0, 9.0), 15_000))
        assertEquals("[out:json][timeout:25];nwr(around:15000,50.00000,9.00000)[tourism~\"^(hotel|guest_house|hostel|camp_site)\$\"][name];out center tags 120;", q)
        val body = """{"elements":[
          {"type":"node","lat":50.01,"lon":9.02,"tags":{"tourism":"hotel","name":"Zur Post","stars":"3"}},
          {"type":"way","center":{"lat":50.02,"lon":9.03},"tags":{"tourism":"camp_site","name":"Seeblick"}},
          {"type":"node","lat":50.0,"lon":9.0,"tags":{"tourism":"hotel"}}]}"""
        val r = LodgingQuery.parse(body)
        assertEquals(listOf("Zur Post", "Seeblick"), r.map { it.name })
        assertEquals(LodgingKind.HOTEL, r[0].kind)
        assertEquals(LodgingKind.CAMPING, r[1].kind)
        assertEquals(LatLon(50.02, 9.03), r[1].position)
    }

    private val datex = """<?xml version="1.0" encoding="UTF-8"?>
<d2:d2LogicalModel xmlns:d2="http://datex2.eu/schema/3/d2Payload" xmlns:sit="http://datex2.eu/schema/3/situation"
  xmlns:com="http://datex2.eu/schema/3/common" xmlns:loc="http://datex2.eu/schema/3/locationReferencing"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
 <d2:payload xsi:type="sit:SituationPublication">
  <sit:situation id="S1"><sit:situationRecord xsi:type="sit:Roadworks" id="R1">
    <sit:validity><com:validityTimeSpecification>
      <com:overallStartTime>2026-05-01T00:00:00+02:00</com:overallStartTime>
      <com:overallEndTime>2026-06-01T00:00:00+02:00</com:overallEndTime></com:validityTimeSpecification></sit:validity>
    <sit:locationReference xsi:type="loc:LinearLocation">
      <loc:roadNumber><loc:values><loc:value lang="de">A5</loc:value></loc:values></loc:roadNumber>
      <loc:from><loc:pointCoordinates><loc:latitude>50.0</loc:latitude><loc:longitude>8.5</loc:longitude></loc:pointCoordinates></loc:from>
      <loc:to><loc:pointCoordinates><loc:latitude>50.0</loc:latitude><loc:longitude>8.8</loc:longitude></loc:pointCoordinates></loc:to>
    </sit:locationReference></sit:situationRecord></sit:situation>
  <sit:situation id="S2"><sit:situationRecord xsi:type="sit:AbnormalTraffic" id="T1">
    <sit:locationReference><loc:locationForDisplay><loc:latitude>50.0005</loc:latitude><loc:longitude>9.5</loc:longitude></loc:locationForDisplay></sit:locationReference>
  </sit:situationRecord></sit:situation>
  <sit:situation id="S3"><sit:situationRecord xsi:type="sit:RoadOrCarriagewayOrLaneManagement" id="C1">
    <sit:roadOrCarriagewayOrLaneManagementType>roadClosed</sit:roadOrCarriagewayOrLaneManagementType>
    <sit:locationReference><loc:locationForDisplay><loc:latitude>50.0</loc:latitude><loc:longitude>10.5</loc:longitude></loc:locationForDisplay></sit:locationReference>
  </sit:situationRecord></sit:situation>
  <sit:situation id="S4"><sit:situationRecord xsi:type="sit:Roadworks" id="FAR">
    <sit:locationReference><loc:locationForDisplay><loc:latitude>51.0</loc:latitude><loc:longitude>9.0</loc:longitude></loc:locationForDisplay></sit:locationReference>
  </sit:situationRecord></sit:situation>
  <sit:situation id="S5"><sit:situationRecord xsi:type="sit:PoorRoadInfrastructure" id="NOLOC"/></sit:situation>
 </d2:payload>
</d2:d2LogicalModel>"""

    @Test
    fun `parses datex records`() {
        val l = DatexParser.parse(datex)
        assertEquals(listOf("R1", "T1", "C1", "FAR"), l.map { it.id })
        assertEquals(listOf(SituationType.ROADWORKS, SituationType.CONGESTION, SituationType.CLOSURE, SituationType.ROADWORKS), l.map { it.type })
        assertEquals("A5", l[0].road)
        assertNull(l[1].road)
        assertEquals(LatLon(50.0, 8.5), l[0].start)
        assertEquals(LatLon(50.0, 8.8), l[0].end)
        assertEquals(java.time.OffsetDateTime.parse("2026-05-01T00:00:00+02:00").toInstant().toEpochMilli(), l[0].validFromMillis)
        assertNull(l[1].validToMillis)
    }

    @Test
    fun `invalid xml and doctype give empty list`() {
        assertEquals(emptyList(), DatexParser.parse("not xml"))
        assertEquals(emptyList(), DatexParser.parse("<!DOCTYPE a [<!ENTITY x \"y\">]><a/>"))
    }

    @Test
    fun `situations along route sorted with meters and time filter`() {
        val all = DatexParser.parse(datex)
        val r = DatexOnRoute.along(route.line, all, 100.0)
        assertEquals(listOf("R1", "T1", "C1"), r.map { it.situation.id })
        assertTrue(r[0].lengthMeters > 15_000)
        assertTrue(r[0].alongMeters < r[1].alongMeters)
        val inMay = java.time.OffsetDateTime.parse("2026-05-10T00:00:00+02:00").toInstant().toEpochMilli()
        assertEquals(3, DatexOnRoute.along(route.line, all, 100.0, inMay).size)
        val july = java.time.OffsetDateTime.parse("2026-07-10T00:00:00+02:00").toInstant().toEpochMilli()
        assertEquals(listOf("T1", "C1"), DatexOnRoute.along(route.line, all, 100.0, july).map { it.situation.id })
    }
}
