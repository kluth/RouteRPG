package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceIntentTest {
    private val nav = NavIntents.ACTION_NAVIGATE_AUTO

    // ---- Intents (Beispiele aus der Google-Dokumentation)
    @Test fun navigateByQuery() {
        val i = NavIntents.parse(nav, "geo:0,0?q=Googleplex") as NavIntent.Navigate
        assertEquals("Googleplex", i.query)
        assertNull(i.position)
        assertEquals("navigation", i.kind)
        assertEquals('d', i.mode)
    }

    @Test fun navigateWithCoordinatesModeAndAvoid() {
        val i = NavIntents.parse(NavIntents.ACTION_NAVIGATE, "geo:1.1,2.2?q=Starbucks+on+Main+Street&mode=w&intent=navigation&avoid=tf") as NavIntent.Navigate
        assertEquals("Starbucks on Main Street", i.query)
        assertEquals(LatLon(1.1, 2.2), i.position)
        assertEquals('w', i.mode)
        assertEquals(setOf(RouteAvoid.TOLLS, RouteAvoid.FERRIES), i.avoid)
    }

    @Test fun addAStopAndDirectionsKinds() {
        assertEquals("add_a_stop", (NavIntents.parse(nav, "geo:0,0?q=1600+Amphitheatre+parkway&mode=b&intent=add_a_stop") as NavIntent.Navigate).kind)
        assertEquals("directions", (NavIntents.parse(nav, "geo:0,0?q=x&intent=directions") as NavIntent.Navigate).kind)
        assertEquals("navigation", (NavIntents.parse(nav, "geo:0,0?q=x&intent=quatsch") as NavIntent.Navigate).kind)
    }

    @Test fun percentEncodingAndUmlauts() {
        val i = NavIntents.parse(nav, "geo:0,0?q=K%C3%B6ln+Hauptbahnhof") as NavIntent.Navigate
        assertEquals("Köln Hauptbahnhof", i.query)
        assertEquals("100% sicher", NavIntents.decode("100%+sicher".replace("%+", "% ")))
    }

    @Test fun coordinatesInQueryWithLabel() {
        val i = NavIntents.parse(nav, "geo:0,0?q=51.43,6.76(Duisburg)") as NavIntent.Navigate
        assertEquals(LatLon(51.43, 6.76), i.position)
        assertEquals("Duisburg", i.query)
    }

    @Test fun searchIntent() {
        assertEquals(NavIntent.Search("restaurants nearby"), NavIntents.parse(NavIntents.ACTION_VIEW, "geo:0,0?q=restaurants+nearby"))
    }

    @Test fun plainViewWithCoordinatesShowsRoute() {
        val i = NavIntents.parse(NavIntents.ACTION_VIEW, "geo:60.5,24.9") as NavIntent.Navigate
        assertEquals("directions", i.kind)
        assertEquals(LatLon(60.5, 24.9), i.position)
    }

    @Test fun legacyGoogleNavigationScheme() {
        val i = NavIntents.parse(NavIntents.ACTION_VIEW, "google.navigation:q=Rovaniemi&mode=d") as NavIntent.Navigate
        assertEquals("Rovaniemi", i.query)
        assertEquals("navigation", i.kind)
    }

    @Test fun customActions() {
        assertEquals(NavIntent.Action("mute", emptyMap()), NavIntents.parse(NavIntents.ACTION_VIEW, "geo.action:?act=mute"))
        val c = NavIntents.parse(NavIntents.ACTION_VIEW, "geo.action:?act=report_crash&accident_type=major") as NavIntent.Action
        assertEquals("report_crash", c.act)
        assertEquals("major", c.params["accident_type"])
        assertEquals(NavIntent.Action("exit_navigation", emptyMap()), NavIntents.parse(NavIntents.ACTION_VIEW, "geo.action.offline:?act=EXIT_NAVIGATION"))
        assertTrue("mute" in NavIntents.supportedActs && "report_crash" !in NavIntents.supportedActs)
    }

    @Test fun ownCommandScheme() {
        assertEquals(NavIntent.Command("toilet"), NavIntents.parse(NavIntents.ACTION_VIEW, "kiloquest://cmd/toilet"))
        assertNull(NavIntents.parse(NavIntents.ACTION_VIEW, "kiloquest://other/x"))
    }

    @Test fun offlineSchemeAndGarbage() {
        assertNotNull(NavIntents.parse(nav, "geo.offline:0,0?q=Oslo"))
        assertNull(NavIntents.parse(nav, "geo:0,0"))          // weder Ziel noch Koordinaten
        assertNull(NavIntents.parse(nav, "http://example.com"))
        assertNull(NavIntents.parse(nav, null))
        assertNull(NavIntents.parse(nav, "geo:abc?q="))
    }

    @Test fun invalidCoordinatesAreIgnored() {
        assertNull(NavIntents.parse(NavIntents.ACTION_VIEW, "geo:99,200"))
    }

    // ---- Route meiden
    @Test fun valhallaAvoidOptions() {
        val body = ValhallaApi.buildRouteRequest(LatLon(1.0, 1.0), LatLon(2.0, 2.0), "de-DE", avoid = setOf(RouteAvoid.TOLLS, RouteAvoid.FERRIES))
        assertTrue("\"use_tolls\":0.0" in body && "\"use_ferry\":0.0" in body && "use_highways" !in body)
        assertTrue("costing_options" !in ValhallaApi.buildRouteRequest(LatLon(1.0, 1.0), LatLon(2.0, 2.0), "de-DE"))
    }

    // ---- Sprachbefehle
    @Test fun germanAndEnglishCommands() {
        assertEquals(VoiceCommand.TOILET, VoiceCommands.parse("Klo-Alarm", false))
        assertEquals(VoiceCommand.TOILET, VoiceCommands.parse("Wir müssen mal", false))
        assertEquals(VoiceCommand.TOILET, VoiceCommands.parse("we need a bathroom", false))
        assertEquals(VoiceCommand.BREAK, VoiceCommands.parse("Pause bitte", false))
        assertEquals(VoiceCommand.ETA, VoiceCommands.parse("Wie lange noch?", false))
        assertEquals(VoiceCommand.ETA, VoiceCommands.parse("When do we arrive", false))
        assertEquals(VoiceCommand.NEXT_TURN, VoiceCommands.parse("Wo muss ich abbiegen", false))
        assertEquals(VoiceCommand.SPEED_LIMIT, VoiceCommands.parse("Was ist das Tempolimit", false))
        assertEquals(VoiceCommand.STORY, VoiceCommands.parse("Erzähl mir eine Geschichte", false))
        assertEquals(VoiceCommand.STOP_NAVIGATION, VoiceCommands.parse("Navigation beenden", false))
        assertEquals(VoiceCommand.DESTINATION, VoiceCommands.parse("Wohin fahren wir", false))
        assertEquals(VoiceCommand.HELP, VoiceCommands.parse("what can you do", false))
    }

    @Test fun muteAndUnmuteDoNotConfuse() {
        assertEquals(VoiceCommand.MUTE, VoiceCommands.parse("Ansagen aus", false))
        assertEquals(VoiceCommand.UNMUTE, VoiceCommands.parse("Ansagen an", false))
        assertEquals(VoiceCommand.UNMUTE, VoiceCommands.parse("Ansagen wieder an", false))
    }

    @Test fun wakeWordIsRequiredInAlwaysOnMode() {
        assertNull(VoiceCommands.parse("Pause", true))
        assertEquals(VoiceCommand.BREAK, VoiceCommands.parse("Kilo, Pause", true))
        assertEquals(VoiceCommand.BREAK, VoiceCommands.parse("Hey Kilo Pause bitte", true))
        assertEquals(VoiceCommand.TOILET, VoiceCommands.parse("OK Kilo klo alarm", true))
        assertEquals("pause", VoiceCommands.stripWake("Kilo, Pause!"))
        assertNull(VoiceCommands.stripWake("Kilometer bis Hamburg"))
    }

    @Test fun wholeWordsOnlyAndNoFalsePositives() {
        assertNull(VoiceCommands.parse("Wir fahren am Kloster vorbei", false)) // "klo" nicht in "Kloster"
        assertNull(VoiceCommands.parse("Kilo", true))
        assertNull(VoiceCommands.parse("", false))
        assertNull(VoiceCommands.parse("das Wetter ist schön", false))
        assertEquals(VoiceCommand.TOILET, VoiceCommands.parseAny(listOf("Kilo xyz", "Kilo Klo"), true))
    }

    @Test fun gameAndSeriesCommands() {
        assertEquals(VoiceCommand.GAME_WORD_CHAIN, VoiceCommands.parse("Kilo, lass uns Wortkette spielen", true))
        assertEquals(VoiceCommand.GAME_LETTERS, VoiceCommands.parse("kilo tier abc", true))
        assertEquals(VoiceCommand.GAME_RHYME, VoiceCommands.parse("Kilo Reimspiel bitte", true))
        assertEquals(VoiceCommand.GAME_STORY, VoiceCommands.parse("Kilo, gemeinsame Geschichte", true))
        assertEquals(VoiceCommand.STORY, VoiceCommands.parse("Kilo, eine Geschichte", true))
        assertEquals(VoiceCommand.SERIES, VoiceCommands.parse("Kilo, nächste Folge", true))
        assertEquals(VoiceCommand.SERIES, VoiceCommands.parse("hey kilo next episode", true))
        assertEquals(VoiceCommand.GAME_MENU, VoiceCommands.parse("Kilo, wir wollen spielen", true))
        assertEquals(VoiceCommand.GAME_MENU, VoiceCommands.parse("kilo let's play", true))
        assertEquals(VoiceCommand.GAME_STOP, VoiceCommands.parse("Kilo, Spiel beenden", true))
        assertEquals(VoiceCommand.GAME_STOP, VoiceCommands.parse("stop game", false))
        assertNull(VoiceCommands.parse("das Spiel war gestern spannend", true)) // ohne Anrede
        assertNull(VoiceCommands.parse("Kilo, Spielzeug", true)) // ganzes Wort
    }
}
