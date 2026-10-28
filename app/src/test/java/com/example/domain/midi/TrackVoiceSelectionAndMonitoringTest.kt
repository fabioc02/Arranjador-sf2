package com.example.domain.midi

import com.example.MidiRhythmState
import com.example.MidiState
import com.example.RecordedNote
import com.example.RhythmTrack
import com.example.domain.casm.CasmPlaybackEngine
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class TrackVoiceSelectionAndMonitoringTest {

    private data class CapturedProgramChange(
        val channel: Int,
        val bankMsb: Int,
        val bankLsb: Int,
        val program: Int
    )

    private val capturedChanges = CopyOnWriteArrayList<CapturedProgramChange>()
    private val listener: (Int, Int, Int, Int) -> Unit = { ch, msb, lsb, pc ->
        capturedChanges.add(CapturedProgramChange(ch, msb, lsb, pc))
    }

    @Before
    fun setUp() {
        MidiRhythmState.resetLastVoiceAppliedKey()
        MidiRhythmState.tracks = MidiRhythmState.createDefaultTracks()
        capturedChanges.clear()
        MidiState.programChangeListeners.add(listener)
    }

    @After
    fun tearDown() {
        MidiState.programChangeListeners.remove(listener)
        capturedChanges.clear()
        MidiRhythmState.resetLastVoiceAppliedKey()
    }

    @Test
    fun testSelectBassTrack_SendsConfiguredBankAndProgramChangeToChannel10() {
        // Track 2 is Bass (channel 11 -> 0-indexed channel 10, bank 0-112-33)
        val bassTrackIndex = MidiRhythmState.tracks.indexOfFirst { it.name.equals("Bass", ignoreCase = true) }
        assertTrue("Bass track must exist in default tracks", bassTrackIndex != -1)
        val bassTrack = MidiRhythmState.tracks[bassTrackIndex]
        assertEquals("Bass track must be on MIDI channel 11", 11, bassTrack.channel)
        assertEquals("Bass track Bank MSB must be 0", 0, bassTrack.bankMsb)
        assertEquals("Bass track Bank LSB must be 112", 112, bassTrack.bankLsb)
        assertEquals("Bass track Program must be 33", 33, bassTrack.program)

        // Select the bass track
        MidiRhythmState.selectTrack(bassTrackIndex)

        // Verify captured Program Change
        assertEquals("Exactly one Program Change event should be dispatched", 1, capturedChanges.size)
        val event = capturedChanges.first()
        assertEquals("Channel must be 10 (MIDI Ch 11 0-indexed)", 10, event.channel)
        assertEquals("Bank MSB must be 0", 0, event.bankMsb)
        assertEquals("Bank LSB must be 112", 112, event.bankLsb)
        assertEquals("Program Change must be 33 (MellowFinger Bass)", 33, event.program)

        // Verify GlobalMidiRouter override channel is set to 10
        assertEquals(10, GlobalMidiRouter.overrideRightHandChannel)
    }

    @Test
    fun testSelectOtherTrack_SendsOnlyConfiguredVoiceToRespectiveChannel() {
        // Select Chord 1 (channel 12 -> 0-indexed channel 11, voice 0-112-0)
        val chordTrackIndex = MidiRhythmState.tracks.indexOfFirst { it.name.equals("Chord 1", ignoreCase = true) }
        assertTrue("Chord 1 track must exist", chordTrackIndex != -1)
        val chordTrack = MidiRhythmState.tracks[chordTrackIndex]

        MidiRhythmState.selectTrack(chordTrackIndex)

        assertEquals("Exactly one Program Change event should be dispatched", 1, capturedChanges.size)
        val event = capturedChanges.first()
        assertEquals("Must send ONLY to channel 11 (MIDI Ch 12)", 11, event.channel)
        assertEquals(chordTrack.bankMsb, event.bankMsb)
        assertEquals(chordTrack.bankLsb, event.bankLsb)
        assertEquals(chordTrack.program, event.program)

        // Verify override channel
        assertEquals(11, GlobalMidiRouter.overrideRightHandChannel)
    }

    @Test
    fun testReentrantOrRecomposition_DoesNotSendDuplicateProgramChanges() {
        val bassTrackIndex = MidiRhythmState.tracks.indexOfFirst { it.name.equals("Bass", ignoreCase = true) }
        val bassTrack = MidiRhythmState.tracks[bassTrackIndex]

        // 1st selection
        MidiRhythmState.selectTrack(bassTrackIndex)
        assertEquals(1, capturedChanges.size)

        // Simulate 10 UI recompositions or re-entrant calls with the same track
        for (i in 0 until 10) {
            MidiRhythmState.applyTrackVoice(bassTrack, force = false)
        }

        // Should still be exactly 1 call!
        assertEquals("Recompositions must NOT trigger redundant Program Changes", 1, capturedChanges.size)

        // Now select Chord 1
        val chordTrackIndex = MidiRhythmState.tracks.indexOfFirst { it.name.equals("Chord 1", ignoreCase = true) }
        MidiRhythmState.selectTrack(chordTrackIndex)
        assertEquals(2, capturedChanges.size)

        // Re-select Bass: should trigger once more
        MidiRhythmState.selectTrack(bassTrackIndex)
        assertEquals(3, capturedChanges.size)
    }

    @Test
    fun testVoiceSelection_DoesNotModifyRecordedNotesPitch() {
        val bassTrackIndex = MidiRhythmState.tracks.indexOfFirst { it.name.equals("Bass", ignoreCase = true) }
        val bassTrack = MidiRhythmState.tracks[bassTrackIndex]

        // Record a note (e.g. C2 = MIDI note 36)
        val note = RecordedNote(
            tick = 0,
            note = 36,
            velocity = 100,
            durationTicks = 480,
            timestampMs = 0L,
            originalTick = 0,
            timestampNs = 0L
        )
        bassTrack.addNote(note)

        // Change voices multiple times
        MidiRhythmState.selectTrack(bassTrackIndex)
        bassTrack.program = 32 // Acoustic Bass
        MidiRhythmState.applyTrackVoice(bassTrack, force = true)
        bassTrack.program = 35 // Fretless Bass
        MidiRhythmState.applyTrackVoice(bassTrack, force = true)

        // Verify the stored note remains strictly 36
        assertEquals("Voice selection must never alter stored note pitch", 36, bassTrack.notes.first().note)
    }

    @Test
    fun testVoiceSelection_DoesNotAffectCasmTranspositionRules() {
        val arrangerCoreState = com.example.domain.arranger.ArrangerCoreState()
        val casmEngine = CasmPlaybackEngine(arrangerCoreState)

        val bassTrackIndex = MidiRhythmState.tracks.indexOfFirst { it.name.equals("Bass", ignoreCase = true) }
        val bassTrack = MidiRhythmState.tracks[bassTrackIndex]

        // Change track voice to anything
        bassTrack.program = 36 // Slap Bass
        MidiRhythmState.applyTrackVoice(bassTrack, force = true)

        // Test CASM transposition with C Major
        val originalNote = 36 // C2
        val chordC = Chord(0, ChordType.Major)
        val finalNoteC = casmEngine.processNote(
            originalNote = originalNote,
            channel = bassTrack.channel - 1,
            sectionName = "Main A",
            currentChord = chordC,
            currentBassNote = chordC.bassKey
        )
        assertEquals("In C Major, C2 must transpose to C2 (identity)", 36, finalNoteC)

        // Test with Dm/A (slash chord)
        val chordDmA = Chord(2, ChordType.Minor, bassKey = 9)
        val finalNoteDmA = casmEngine.processNote(
            originalNote = originalNote,
            channel = bassTrack.channel - 1,
            sectionName = "Main A",
            currentChord = chordDmA,
            currentBassNote = chordDmA.bassKey
        )
        assertEquals("In Dm/A, bass pitch must be A1 (MIDI 33)", 33, finalNoteDmA)
    }

    @Test
    fun testLocalControlOff_MaintainsLiveMonitoringRouteToSelectedTrack() {
        val bassTrackIndex = MidiRhythmState.tracks.indexOfFirst { it.name.equals("Bass", ignoreCase = true) }
        MidiRhythmState.selectTrack(bassTrackIndex)
        MidiRhythmState.isLocalControlOff = true

        assertEquals("Override channel must be 10 for Bass", 10, GlobalMidiRouter.overrideRightHandChannel)
        assertTrue("Local control flag must be true", MidiRhythmState.isLocalControlOff)
    }
}
