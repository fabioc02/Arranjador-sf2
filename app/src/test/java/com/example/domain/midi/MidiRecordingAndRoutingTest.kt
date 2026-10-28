package com.example.domain.midi

import com.example.MidiRhythmState
import com.example.MidiState
import com.example.domain.chord.YamahaRuleFactory
import com.example.domain.models.CasmChannel
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.NTR
import com.example.domain.models.NTT
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MidiRecordingAndRoutingTest {

    @Before
    fun setUp() {
        MidiState.isArrangerMode = false
        MidiRhythmState.isRecording = false
        MidiRhythmState.clearActiveNotes()
        MidiRhythmState.selectedTrackIndex = 0
        MidiRhythmState.tracks[0].notes = emptyList()
        MidiRhythmState.tracks[0].ccEvents = emptyList()
        MidiRhythmState.currentTick = 0
    }

    /**
     * TEST 1: Right 1 filter
     * When recordInputSource is RIGHT_1, notes from Channel 0 (Right 1) are recorded,
     * but notes from Channel 1 (Right 2) are completely ignored.
     */
    @Test
    fun testRecordInputSource_Right1_FiltersOutRight2() {
        MidiRhythmState.recordInputSource = MidiRhythmState.RecordInputSource.RIGHT_1
        MidiRhythmState.isRecording = true

        val track = MidiRhythmState.tracks[0]
        assertEquals(0, track.notes.size)

        // Note from Right 2 (channel 1, note 62)
        MidiRhythmState.processIncomingMidi(0x91, 62, 80, 1_000_000L)
        assertEquals("Right 2 notes should NOT be recorded when input is RIGHT_1", 0, track.notes.size)

        // Note from Right 1 (channel 0, note 60)
        MidiRhythmState.processIncomingMidi(0x90, 60, 90, 2_000_000L)
        assertEquals("Right 1 note must be recorded", 1, track.notes.size)
        assertEquals(60, track.notes[0].note)
        assertEquals(90, track.notes[0].velocity)
    }

    /**
     * TEST 2: Right 2 filter
     * When recordInputSource is RIGHT_2, notes from Channel 1 (Right 2) are recorded,
     * but notes from Channel 0 (Right 1) are ignored.
     */
    @Test
    fun testRecordInputSource_Right2_FiltersOutRight1() {
        MidiRhythmState.recordInputSource = MidiRhythmState.RecordInputSource.RIGHT_2
        MidiRhythmState.isRecording = true

        val track = MidiRhythmState.tracks[0]
        assertEquals(0, track.notes.size)

        // Note from Right 1 (channel 0, note 60)
        MidiRhythmState.processIncomingMidi(0x90, 60, 90, 1_000_000L)
        assertEquals("Right 1 notes should NOT be recorded when input is RIGHT_2", 0, track.notes.size)

        // Note from Right 2 (channel 1, note 64)
        MidiRhythmState.processIncomingMidi(0x91, 64, 85, 2_000_000L)
        assertEquals("Right 2 note must be recorded", 1, track.notes.size)
        assertEquals(64, track.notes[0].note)
        assertEquals(85, track.notes[0].velocity)
    }

    /**
     * TEST 3: Anti-duplication for layered Right 1 + Right 2
     * When both parts are enabled on the keyboard, a single key press sends Note On on
     * Channel 0 and Channel 1 almost simultaneously. Deduplication ensures only ONE note is stored.
     */
    @Test
    fun testRecordInputSource_AllRight_DeduplicatesLayeredNotes() {
        MidiRhythmState.recordInputSource = MidiRhythmState.RecordInputSource.ALL_RIGHT
        MidiRhythmState.isRecording = true

        val track = MidiRhythmState.tracks[0]

        // Right 1 sends note 60 at T = 10ms
        MidiRhythmState.processIncomingMidi(0x90, 60, 80, 10_000_000L)
        assertEquals(1, track.notes.size)

        // Right 2 sends note 60 at T = 12ms (layered trigger on same key press)
        MidiRhythmState.processIncomingMidi(0x91, 60, 78, 12_000_000L)
        assertEquals("Duplicate layered Note On MUST be suppressed", 1, track.notes.size)

        // Note Off for note 60
        MidiRhythmState.processIncomingMidi(0x80, 60, 0, 100_000_000L)
        assertEquals(1, track.notes.size)
    }

    /**
     * TEST 4: Legitimate rapid repetitions are NOT suppressed
     * Separate key strikes with Note Off in between or separated by normal playing time
     * must both be preserved.
     */
    @Test
    fun testLegitimateRepeatedNotes_ArePreserved() {
        MidiRhythmState.recordInputSource = MidiRhythmState.RecordInputSource.RIGHT_1
        MidiRhythmState.isRecording = true

        val track = MidiRhythmState.tracks[0]

        // Strike 1: Note 60 on beat 1 (tick 0)
        MidiRhythmState.currentTick = 0
        MidiRhythmState.processIncomingMidi(0x90, 60, 90, 10_000_000L)
        // Release 1 at tick 240
        MidiRhythmState.currentTick = 240
        MidiRhythmState.processIncomingMidi(0x80, 60, 0, 100_000_000L)

        // Strike 2: Note 60 on beat 2 (tick 480, 100ms later)
        MidiRhythmState.currentTick = 480
        MidiRhythmState.processIncomingMidi(0x90, 60, 95, 200_000_000L)
        MidiRhythmState.currentTick = 720
        MidiRhythmState.processIncomingMidi(0x80, 60, 0, 300_000_000L)

        assertEquals("Both distinct repeated notes must be recorded", 2, track.notes.size)
        assertEquals(60, track.notes[0].note)
        assertEquals(60, track.notes[1].note)
        assertEquals(0, track.notes[0].tick)
        assertEquals(480, track.notes[1].tick)
    }

    /**
     * TEST 5: Control Change messages are recorded as CC events, not notes
     */
    @Test
    fun testControlChange_NotRecordedAsNotes() {
        MidiRhythmState.recordInputSource = MidiRhythmState.RecordInputSource.RIGHT_1
        MidiRhythmState.isRecording = true

        val track = MidiRhythmState.tracks[0]

        // Send CC 64 (Sustain = 127) on channel 0
        MidiRhythmState.processIncomingMidi(0xB0, 64, 127, 10_000_000L)
        assertEquals(0, track.notes.size)
        assertEquals(1, track.ccEvents.size)
        assertEquals(64, track.ccEvents[0].cc)
        assertEquals(127, track.ccEvents[0].value)
    }

    /**
     * TEST 6: GlobalMidiRouter live echo is disabled by default
     * Prevents destructive feedback and double notes on the PSR-S750.
     */
    @Test
    fun testGlobalMidiRouter_LiveEchoDisabledByDefault() {
        assertFalse("Live MIDI echo must be disabled by default for workstation hardware", GlobalMidiRouter.enableLiveMidiEcho)
    }

    /**
     * TEST 7: MelodyRule respects Note Limits and High Key
     */
    @Test
    fun testMelodyRule_AppliesNoteLimitsAndHighKey() {
        val casmChannel = CasmChannel(
            sourceChannel = 14,
            destChannel = 15,
            sourceChordRoot = "C",
            sourceChordType = "Maj7",
            ntr = NTR.ROOT_TRANS,
            ntt = NTT.MELODY,
            highKey = 6, // F#
            noteLowLimit = 48, // C3
            noteHighLimit = 84  // C6
        )

        val rule = YamahaRuleFactory.getRule(casmChannel.ntr, casmChannel.ntt)
        val sourceChord = Chord(0, ChordType.Major)

        // Test normal transposition C -> D (+2 semitones)
        val note60 = rule.transpose(60, sourceChord, Chord(2, ChordType.Major), casmChannel)
        assertEquals(62, note60)

        // Test High Key limit: Target G (root 7 > highKey 6) -> drops 1 octave
        val noteWithHk = rule.transpose(60, sourceChord, Chord(7, ChordType.Major), casmChannel)
        // 60 + 7 - 12 = 55
        assertEquals(55, noteWithHk)

        // Test Note Low Limit: A low note that would fall below 48 is shifted up an octave
        val lowNote = rule.transpose(40, sourceChord, Chord(0, ChordType.Major), casmChannel)
        assertTrue("Note must stay above or at lowLimit 48", lowNote >= 48)
    }
}
