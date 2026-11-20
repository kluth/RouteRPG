package app.kiloquest.nav

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherTest {
    private val route: Route = run {
        val pts = (0..2000).map { LatLon(50.0 + it * 0.001, 8.0) } // ca. 222 km
        val line = Polyline(pts)
        Route(line, emptyList(), line.lengthMeters, 3 * 3600.0)     // 3 Stunden
    }
    private val now = LocalDateTime.parse("2026-11-20T10:00:00").toEpochSecond(ZoneOffset.UTC)

    @Test
    fun `samples include start and destination with growing eta`() {
        val s = WeatherApi.samples(route, 0.0, now)
        assertTrue(s.size in 3..5, "size=${s.size}")
        assertEquals(now, s.first().etaEpochSec)
        assertTrue(Math.abs(s.last().etaEpochSec - (now + 3 * 3600)) < 5)
        assertEquals(s.map { it.etaEpochSec }.sorted(), s.map { it.etaEpochSec })
        assertEquals(route.line.lengthMeters, s.last().alongMeters, 1.0)
    }

    @Test
    fun `samples are capped`() {
        assertTrue(WeatherApi.samples(route, 0.0, now, stepMeters = 1_000.0, maxPoints = 12).size <= 12)
    }

    @Test
    fun `url has all coordinates and gmt timezone`() {
        val s = WeatherApi.samples(route, 0.0, now)
        val url = WeatherApi.buildUrl(s)
        assertTrue("timezone=GMT" in url && "weather_code" in url && "sunset" in url)
        assertEquals(s.size, url.substringAfter("latitude=").substringBefore('&').split(',').size)
    }

    @Test
    fun `wmo codes map to conditions`() {
        assertEquals(WeatherCondition.CLEAR, WeatherApi.condition(0))
        assertEquals(WeatherCondition.SNOW, WeatherApi.condition(73))
        assertEquals(WeatherCondition.RAIN, WeatherApi.condition(63))
        assertEquals(WeatherCondition.STORM, WeatherApi.condition(95))
        assertEquals(WeatherCondition.UNKNOWN, WeatherApi.condition(null))
    }

    @Test
    fun `hazard rules`() {
        assertTrue(WeatherHazard.SNOW in WeatherApi.hazards(-2.0, 0.5, 73, 10.0))
        assertTrue(WeatherHazard.ICE in WeatherApi.hazards(0.0, 0.4, 61, 10.0))
        assertTrue(WeatherHazard.HEAVY_RAIN in WeatherApi.hazards(8.0, 4.0, 65, 10.0))
        assertTrue(WeatherHazard.WIND in WeatherApi.hazards(8.0, 0.0, 3, 80.0))
        assertTrue(WeatherHazard.STORM in WeatherApi.hazards(18.0, 0.0, 96, 20.0))
        assertTrue(WeatherHazard.FROST in WeatherApi.hazards(-20.0, 0.0, 0, 5.0))
        assertTrue(WeatherApi.hazards(10.0, 0.0, 1, 10.0).isEmpty())
    }

    private fun hours(day: String) = (0..23).map { "%sT%02d:00".format(day, it) }

    private fun location(day: String, temp: Double, code: Int, precip: Double, gust: Double, sunrise: String?, sunset: String?) = """{
      "hourly":{"time":${hours(day).joinToString(",", "[", "]") { "\"$it\"" }},
        "temperature_2m":${List(24) { temp }},"precipitation":${List(24) { precip }},
        "weather_code":${List(24) { code }},"wind_gusts_10m":${List(24) { gust }}},
      "daily":{"time":["$day"],"sunrise":[${sunrise?.let { "\"$it\"" } ?: "null"}],"sunset":[${sunset?.let { "\"$it\"" } ?: "null"}]}}"""

    @Test
    fun `parses multi location response and finds hazards and dark arrival`() {
        val s = WeatherApi.samples(route, 0.0, now)
        val day = "2026-11-20"
        val body = s.indices.joinToString(",", "[", "]") { i ->
            if (i == s.lastIndex) location(day, -6.0, 73, 1.0, 30.0, "${day}T06:30", "${day}T12:30") // Ziel: Schnee, Sonnenuntergang 12:30
            else location(day, 4.0, 3, 0.0, 20.0, "${day}T06:30", "${day}T15:30")
        }
        val sum = WeatherApi.parse(body, s)
        assertEquals(s.size, sum.points.size)
        assertEquals(WeatherCondition.SNOW, sum.destination?.condition)
        assertEquals(-6.0, sum.destination?.tempC)
        assertTrue(sum.hazardsAhead.any { it.first == WeatherHazard.SNOW })
        assertEquals(true, sum.arrivesInDark) // Ankunft ca. 13:00 nach Sonnenuntergang 12:30
    }

    @Test
    fun `polar night leaves darkness unknown`() {
        val s = listOf(WeatherSample(0.0, LatLon(68.0, 20.0), now))
        val body = location("2026-11-20", -10.0, 0, 0.0, 5.0, null, null)
        val sum = WeatherApi.parse(body, s) // einzelnes Objekt statt Array
        assertNull(sum.arrivesInDark)
        assertEquals(-10.0, sum.destination?.tempC)
    }

    @Test
    fun `garbage response gives empty summary`() {
        val sum = WeatherApi.parse("""{"error":true}""", WeatherApi.samples(route, 0.0, now))
        assertTrue(sum.points.size <= 1)
    }
}
