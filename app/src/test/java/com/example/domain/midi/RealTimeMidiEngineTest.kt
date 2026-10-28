package com.example.domain.midi

import com.example.domain.models.MidiEvent
import com.example.domain.models.StyleTrack
import com.example.domain.playback.TrackPlayer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RealTimeMidiEngineTest {

    // ==========================================
    // TESTE A — Tick skipping
    // ==========================================
    @Test
    fun testTickSkippingPreservesNoteOff() {
        val player = TrackPlayer("TestTrack", 0)
        val track = StyleTrack(
            events = listOf(
                MidiEvent.NoteOn(tick = 10, channel = 0, note = 60, velocity = 100),
                MidiEvent.NoteOff(tick = 11, channel = 0, note = 60, velocity = 0),
                MidiEvent.NoteOn(tick = 20, channel = 0, note = 62, velocity = 100)
            )
        )
        player.loadTrack(track, startTick = 0, endTick = 100)

        // Process up to tick 10
        val events10 = player.getEventsForTick(10)
        assertEquals("Deve processar 1 evento no tick 10", 1, events10.size)
        assertTrue("Deve ser NoteOn", events10[0] is MidiEvent.NoteOn)
        assertEquals(60, (events10[0] as MidiEvent.NoteOn).note)

        // Scheduler jumps straight from 10 to 12 (skipping 11)
        val events12 = player.getEventsForTick(12)
        assertEquals("NoteOff do tick 11 DEVE ser entregue ao saltar para o tick 12", 1, events12.size)
        assertTrue("Deve ser NoteOff", events12[0] is MidiEvent.NoteOff)
        assertEquals(11L, events12[0].tick)
        assertEquals(60, (events12[0] as MidiEvent.NoteOff).note)
    }

    // ==========================================
    // TESTE B — Múltiplos eventos no intervalo
    // ==========================================
    @Test
    fun testMultipleEventsInInterval() {
        val player = TrackPlayer("TestTrack", 0)
        val track = StyleTrack(
            events = listOf(
                MidiEvent.NoteOn(tick = 10, channel = 1, note = 64, velocity = 90),
                MidiEvent.ControlChange(tick = 11, channel = 1, controller = 7, value = 100),
                MidiEvent.NoteOff(tick = 12, channel = 1, note = 64, velocity = 0),
                MidiEvent.ProgramChange(tick = 13, channel = 1, program = 25)
            )
        )
        player.loadTrack(track, startTick = 0, endTick = 100)

        // Skip directly from start to 13
        val events = player.getEventsForTick(13)
        assertEquals("Todos os 4 eventos do intervalo [0..13] devem ser entregues", 4, events.size)

        assertEquals(10L, events[0].tick)
        assertTrue(events[0] is MidiEvent.NoteOn)

        assertEquals(11L, events[1].tick)
        assertTrue(events[1] is MidiEvent.ControlChange)

        assertEquals(12L, events[2].tick)
        assertTrue(events[2] is MidiEvent.NoteOff)

        assertEquals(13L, events[3].tick)
        assertTrue(events[3] is MidiEvent.ProgramChange)
    }

    // ==========================================
    // TESTE C — Ausência de duplicação
    // ==========================================
    @Test
    fun testNoDuplicateEvents() {
        val player = TrackPlayer("TestTrack", 0)
        val track = StyleTrack(
            events = listOf(
                MidiEvent.NoteOn(tick = 10, channel = 0, note = 60, velocity = 100),
                MidiEvent.NoteOff(tick = 15, channel = 0, note = 60, velocity = 0)
            )
        )
        player.loadTrack(track, startTick = 0, endTick = 100)

        val firstCall = player.getEventsForTick(15)
        assertEquals("Primeira chamada deve retornar os 2 eventos", 2, firstCall.size)

        // Querying the same tick again or intermediate tick
        val secondCall = player.getEventsForTick(15)
        assertTrue("Segunda chamada para mesmo tick NÃO pode duplicar eventos", secondCall.isEmpty())

        val thirdCall = player.getEventsForTick(16)
        assertTrue("Chamada subsequente sem novos eventos deve ser vazia", thirdCall.isEmpty())
    }

    // ==========================================
    // TESTE D — Notas ativas
    // ==========================================
    @Test
    fun testActiveNotesTracking() {
        val sentNoteOffs = mutableListOf<Triple<Int, Int, Int>>()
        val sentCCs = mutableListOf<Triple<Int, Int, Int>>()
        val safetyManager = MidiSafetyManager(
            sendNoteOff = { ch, note, vel, _ -> sentNoteOffs.add(Triple(ch, note, vel)) },
            sendControlChange = { ch, ctrl, value, _ -> sentCCs.add(Triple(ch, ctrl, value)) }
        )

        assertEquals(0, safetyManager.getActiveNotesCount())
        assertFalse(safetyManager.isNoteActive(10, 36))

        // Note On CH10 Note 36
        safetyManager.onNoteOn(10, 36, 100)
        assertTrue("Nota 36 no canal 10 deve estar ativa", safetyManager.isNoteActive(10, 36))
        assertEquals(1, safetyManager.getActiveNotesCount())

        // Note Off CH10 Note 36
        safetyManager.onNoteOff(10, 36)
        assertFalse("Nota 36 no canal 10 deve ser removida", safetyManager.isNoteActive(10, 36))
        assertEquals(0, safetyManager.getActiveNotesCount())

        // Velocity 0 acts as Note Off
        safetyManager.onNoteOn(9, 42, 80)
        assertTrue(safetyManager.isNoteActive(9, 42))
        safetyManager.onNoteOn(9, 42, 0)
        assertFalse(safetyManager.isNoteActive(9, 42))
    }

    // ==========================================
    // TESTE E — Panic / All Notes Off
    // ==========================================
    @Test
    fun testPanicMidiAndIdempotence() {
        val sentNoteOffs = mutableListOf<Triple<Int, Int, Int>>()
        val sentCCs = mutableListOf<Triple<Int, Int, Int>>()
        val safetyManager = MidiSafetyManager(
            sendNoteOff = { ch, note, vel, _ -> sentNoteOffs.add(Triple(ch, note, vel)) },
            sendControlChange = { ch, ctrl, value, _ -> sentCCs.add(Triple(ch, ctrl, value)) }
        )

        // Turn on 3 notes on channel 9 and 10
        safetyManager.onNoteOn(9, 36, 100)
        safetyManager.onNoteOn(9, 38, 100)
        safetyManager.onNoteOn(10, 48, 100)
        assertEquals(3, safetyManager.getActiveNotesCount())

        // Execute Panic
        safetyManager.panicMidi()

        // 1. Must send NoteOff for all active notes
        assertEquals("Deve enviar NoteOff para as 3 notas ativas", 3, sentNoteOffs.size)
        assertTrue(sentNoteOffs.contains(Triple(9, 36, 0)))
        assertTrue(sentNoteOffs.contains(Triple(9, 38, 0)))
        assertTrue(sentNoteOffs.contains(Triple(10, 48, 0)))

        // 2. Must send CC 123 to channels used (9 and 10)
        assertTrue("Deve enviar CC 123 no canal 9", sentCCs.contains(Triple(9, 123, 0)))
        assertTrue("Deve enviar CC 123 no canal 10", sentCCs.contains(Triple(10, 123, 0)))

        // 3. Active notes count must be 0
        assertEquals("Notas ativas devem ser 0 após Panic", 0, safetyManager.getActiveNotesCount())

        // 4. Calling panic again must be safe and idempotent (no active notes to turn off)
        val initialNoteOffCount = sentNoteOffs.size
        safetyManager.panicMidi()
        assertEquals("Não deve enviar NoteOff adicional na segunda chamada", initialNoteOffCount, sentNoteOffs.size)
    }

    // ==========================================
    // TESTE F — Dois modos geram a MESMA sequência musical
    // ==========================================
    @Test
    fun testDualSchedulerGeneratesIdenticalMidiSequence() {
        // Build a deterministic track in 1920 PPQ
        val originalEvents = listOf(
            MidiEvent.NoteOn(tick = 0, channel = 9, note = 36, velocity = 100),
            MidiEvent.NoteOff(tick = 960, channel = 9, note = 36, velocity = 0),
            MidiEvent.ControlChange(tick = 1000, channel = 9, controller = 7, value = 110),
            MidiEvent.NoteOn(tick = 1920, channel = 9, note = 38, velocity = 100),
            MidiEvent.NoteOff(tick = 2880, channel = 9, note = 38, velocity = 0),
            MidiEvent.ProgramChange(tick = 3000, channel = 9, program = 1)
        )

        // Mode 1: Simulate J5_COMPATIBILITY execution
        val playerJ5 = TrackPlayer("J5Track", 0)
        playerJ5.loadTrack(StyleTrack(events = originalEvents), 0, 4000)
        val j5Collected = mutableListOf<MidiEvent>()

        var currentTickJ5 = 0L
        while (currentTickJ5 <= 3840L) {
            val events = playerJ5.getEventsForTick(currentTickJ5)
            j5Collected.addAll(events)
            val nextTick = playerJ5.peekNextEventTick()
            currentTickJ5 = if (nextTick != null && nextTick > currentTickJ5) {
                nextTick
            } else {
                currentTickJ5 + 480L
            }
        }

        // Mode 2: Simulate HIGH_PRECISION execution (e.g. finer tick steps)
        val playerHP = TrackPlayer("HPTrack", 0)
        playerHP.loadTrack(StyleTrack(events = originalEvents), 0, 4000)
        val hpCollected = mutableListOf<MidiEvent>()

        var currentTickHP = 0L
        while (currentTickHP <= 3840L) {
            val events = playerHP.getEventsForTick(currentTickHP)
            hpCollected.addAll(events)
            currentTickHP += 48L // Fine steps
        }

        assertEquals("Ambos os modos devem gerar exatamente a mesma quantidade de eventos", j5Collected.size, hpCollected.size)
        assertEquals("Quantidade de eventos deve bater com os originais", originalEvents.size, j5Collected.size)

        for (i in j5Collected.indices) {
            val eJ5 = j5Collected[i]
            val eHP = hpCollected[i]
            assertEquals("Tick deve ser idêntico no índice $i", eJ5.tick, eHP.tick)
            assertEquals("Classe de evento deve ser idêntica no índice $i", eJ5::class, eHP::class)

            if (eJ5 is MidiEvent.NoteOn && eHP is MidiEvent.NoteOn) {
                assertEquals(eJ5.channel, eHP.channel)
                assertEquals(eJ5.note, eHP.note)
                assertEquals(eJ5.velocity, eHP.velocity)
            }
            if (eJ5 is MidiEvent.NoteOff && eHP is MidiEvent.NoteOff) {
                assertEquals(eJ5.channel, eHP.channel)
                assertEquals(eJ5.note, eHP.note)
            }
            if (eJ5 is MidiEvent.ControlChange && eHP is MidiEvent.ControlChange) {
                assertEquals(eJ5.channel, eHP.channel)
                assertEquals(eJ5.controller, eHP.controller)
                assertEquals(eJ5.value, eHP.value)
            }
            if (eJ5 is MidiEvent.ProgramChange && eHP is MidiEvent.ProgramChange) {
                assertEquals(eJ5.channel, eHP.channel)
                assertEquals(eJ5.program, eHP.program)
            }
        }
    }

    // ==========================================
    // TESTE G — 1920 PPQ é preservado em ambos os modos
    // ==========================================
    @Test
    fun testBothModesUse1920Ppq() {
        val j5Scheduler = J5CompatibilityScheduler(
            ppq = 1920,
            getBpm = { 120f },
            onTick = { _, _ -> }
        )
        val hpScheduler = HighPrecisionScheduler(
            ppq = 1920,
            getBpm = { 120f },
            onTick = { _, _ -> }
        )
        val unifiedSchedulerAuto = RealTimeMidiScheduler(
            ppq = 1920,
            getBpm = { 120f },
            mode = MidiSchedulerMode.AUTO,
            onTick = { _, _ -> }
        )

        assertEquals("J5 Scheduler deve operar em 1920 PPQ", 1920, j5Scheduler.ppq)
        assertEquals("High Precision Scheduler deve operar em 1920 PPQ", 1920, hpScheduler.ppq)
        assertEquals("Unified Scheduler deve operar em 1920 PPQ", 1920, unifiedSchedulerAuto.ppq)
    }
}
