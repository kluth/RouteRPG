package app.kiloquest.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkTest {
    @Test fun clientMessagesRoundTrip() {
        for (m in listOf<ClientMsg>(ClientMsg.Hello("Mias Tablet", "EXPLORER"), ClientMsg.Answer(2), ClientMsg.Answer(null), ClientMsg.Skip, ClientMsg.Next, ClientMsg.Reroute(true), ClientMsg.Break(false), ClientMsg.Age("MINI"), ClientMsg.ToiletAlarm, ClientMsg.ToiletResume, ClientMsg.ToiletDismiss, ClientMsg.ToiletRequest, ClientMsg.Ping)) {
            val line = Wire.encode(m)
            assertTrue(line.endsWith("\n") && line.count { it == '\n' } == 1, line)
            assertEquals(m, Wire.decodeClient(line))
        }
    }

    @Test fun hostMessagesRoundTripAndHideCorrectAnswer() {
        val q = QuestDto("quiz_x", "quest_quiz_x", "QUIZ", "CHOICE", listOf("a", "b"), emptyList(), 15, 1200.0)
        val snap = Snapshot(seq = 5, quest = q, scores = listOf(10, 5), plan = listOf(q), reroute = RerouteDto(8, false, "JAM", "A1"))
        for (m in listOf<HostMsg>(HostMsg.Welcome(Role.KID, 0), HostMsg.Denied(DenyReason.FULL), HostMsg.SnapshotMsg(snap), HostMsg.Bye)) {
            assertEquals(m, Wire.decodeHost(Wire.encode(m)))
        }
        assertFalse("correct" in Wire.encode(HostMsg.SnapshotMsg(snap)).substringBefore("feedback"), "open quest must not carry the answer")
    }

    @Test fun garbageIsIgnored() {
        assertNull(Wire.decodeClient("not json"))
        assertNull(Wire.decodeClient("""{"t":"hack","x":1}"""))
        assertNull(Wire.decodeHost(""))
    }

    @Test fun lineReaderSplitsAcrossChunksAndDropsOversize() {
        val r = LineReader(maxBytes = 16)
        assertEquals(emptyList(), r.feed("{\"t\":\"pi".toByteArray()))
        assertEquals(listOf("{\"t\":\"ping\"}"), r.feed("ng\"}\n".toByteArray()))
        val big = ByteArray(100) { 'x'.code.toByte() } + "\nok\n".toByteArray()
        assertEquals(listOf("ok"), r.feed(big))
        assertEquals(listOf("a", "b"), LineReader().feed("a\nb\n".toByteArray()))
    }

    private fun hello(n: String = "Tab", age: String = "MINI", v: Int = PROTOCOL_VERSION) = ClientMsg.Hello(n, age, v)

    @Test fun rosterApprovalFlowAndPlayerNumbers() {
        val r = DeviceRoster(maxKids = 2, maxCopilots = 1)
        assertEquals(DeviceRoster.HelloResult.Pending, r.onHello("A", hello("A", "MINI")))
        assertEquals(1, r.pendingRequests().size)
        val a = assertNotNull(r.approve("A", Role.KID))
        assertEquals(0, a.player)
        r.onHello("B", hello("B", "PRO")); assertEquals(1, assertNotNull(r.approve("B", Role.KID)).player)
        r.onHello("C", hello("C")); assertNull(r.approve("C", Role.KID), "kid limit")
        assertNotNull(r.approve("C", Role.COPILOT))
        assertTrue(r.remove("A"))
        r.onHello("D", hello("D")); assertEquals(0, assertNotNull(r.approve("D", Role.KID)).player, "freed player number is reused")
        assertEquals(listOf("MINI", "PRO"), r.ageBands(2, "EXPLORER"))
    }

    @Test fun knownDeviceReconnectsWithoutAskingAgain() {
        val r = DeviceRoster()
        r.onHello("A", hello("A")); r.approve("A", Role.KID)
        val res = r.onHello("A", hello("Neu", "EXPLORER"))
        assertTrue(res is DeviceRoster.HelloResult.Known && res.device.name == "Neu" && res.device.ageBand == "EXPLORER")
        assertTrue(r.pendingRequests().isEmpty())
    }

    @Test fun wrongVersionAndDeny() {
        val r = DeviceRoster()
        assertEquals(DeviceRoster.HelloResult.Rejected(DenyReason.VERSION), r.onHello("A", hello(v = 99)))
        r.onHello("B", hello()); assertTrue(r.deny("B")); assertNull(r.approve("B", Role.KID))
    }

    @Test fun permissionsPerRole() {
        val r = DeviceRoster()
        assertTrue(r.allows(Role.KID, ClientMsg.Answer(1)))
        assertFalse(r.allows(Role.KID, ClientMsg.Reroute(true)), "kids cannot decide detours")
        assertFalse(r.allows(Role.COPILOT, ClientMsg.Answer(1)))
        assertTrue(r.allows(Role.COPILOT, ClientMsg.Break(true)))
        assertTrue(r.allows(Role.KID, ClientMsg.Ping))
    }

    @Test fun ageBandsFallBackToDefault() {
        val r = DeviceRoster()
        r.onHello("A", hello("A", "PRO")); r.approve("A", Role.KID)
        assertEquals(listOf("PRO", "EXPLORER", "EXPLORER"), r.ageBands(3, "EXPLORER"))
    }

    @Test fun restoreKeepsPairingsAcrossRestart() {
        val r = DeviceRoster()
        r.onHello("A", hello("A", "PRO")); val a = r.approve("A", Role.KID)!!
        val r2 = DeviceRoster(); r2.restore(listOf(a))
        assertTrue(r2.onHello("A", hello("A")) is DeviceRoster.HelloResult.Known)
    }

    @Test fun pendingListIsCapped() {
        val r = DeviceRoster(maxPending = 2)
        r.onHello("A", hello()); r.onHello("B", hello())
        assertEquals(DeviceRoster.HelloResult.Rejected(DenyReason.FULL), r.onHello("C", hello()))
        assertEquals(DeviceRoster.HelloResult.Pending, r.onHello("A", hello("A2"))) // bekannte Anfrage darf aktualisieren
        assertEquals(2, r.pendingRequests().size)
    }

    @Test fun toiletPermissions() {
        val r = DeviceRoster()
        assertTrue(r.allows(Role.COPILOT, ClientMsg.ToiletAlarm))
        assertTrue(r.allows(Role.COPILOT, ClientMsg.ToiletResume))
        assertFalse(r.allows(Role.KID, ClientMsg.ToiletAlarm), "kids only ask, the co-driver decides")
        assertTrue(r.allows(Role.KID, ClientMsg.ToiletRequest))
        assertFalse(r.allows(Role.COPILOT, ClientMsg.ToiletRequest))
    }

    @Test fun snapshotCarriesToiletState() {
        val snap = Snapshot(seq = 1, toilet = ToiletDto("HEADING", "Rastplatz", 240.0, 3200.0), toiletRequest = "Mias Tablet")
        assertEquals(snap, (Wire.decodeHost(Wire.encode(HostMsg.SnapshotMsg(snap))) as HostMsg.SnapshotMsg).snapshot)
    }
}
