package com.example.domain.midi

import com.example.MidiRhythmState
import com.example.MidiState
import com.example.RhythmTrack
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VirtualKeyboardAndRecordingValidationTest {

    @Before
    fun setup() {
        MidiRhythmState.isRecording = false
        MidiRhythmState.blockWrongNotes = false
        MidiRhythmState.evalMode = MidiRhythmState.EvalMode.CHORD
        MidiRhythmState.lastEvaluatedNote = null
        MidiRhythmState.lastEvaluatedStatus = null
        MidiRhythmState.clearActiveNotes()
    }

    @Test
    fun testChordMode_EvaluatesChordTonesAsCerto() {
        val chordTrack = RhythmTrack("Chord 1", 12, 0, 112, 0, false).apply {
            sourceRoot = Chord(0, ChordType.Major) // C Major (C, E, G)
        }
        MidiRhythmState.evalMode = MidiRhythmState.EvalMode.CHORD

        // C (0), E (4), G (7) across any octave should be CERTO
        val (c4Certo, _) = MidiRhythmState.evaluateNoteAgainstTrack(60, chordTrack) // C4
        assertTrue("C4 should be CERTO in C Major chord", c4Certo)

        val (e4Certo, _) = MidiRhythmState.evaluateNoteAgainstTrack(64, chordTrack) // E4
        assertTrue("E4 should be CERTO in C Major chord", e4Certo)

        val (g4Certo, _) = MidiRhythmState.evaluateNoteAgainstTrack(67, chordTrack) // G4
        assertTrue("G4 should be CERTO in C Major chord", g4Certo)

        // Notes outside chord: D (62), F (65), A (69), B (71) should be ERRADO in CHORD mode
        val (d4Errado, _) = MidiRhythmState.evaluateNoteAgainstTrack(62, chordTrack)
        assertFalse("D4 should be ERRADO in C Major chord mode", d4Errado)

        val (f4Errado, _) = MidiRhythmState.evaluateNoteAgainstTrack(65, chordTrack)
        assertFalse("F4 should be ERRADO in C Major chord mode", f4Errado)
    }

    @Test
    fun testScaleMode_EvaluatesScaleTonesAsCerto() {
        val chordTrack = RhythmTrack("Chord 1", 12, 0, 112, 0, false).apply {
            sourceRoot = Chord(0, ChordType.Major) // C Major scale
        }
        MidiRhythmState.evalMode = MidiRhythmState.EvalMode.SCALE

        // In scale mode, D (62) and F (65) and A (69) are CERTO
        val (d4Certo, _) = MidiRhythmState.evaluateNoteAgainstTrack(62, chordTrack)
        assertTrue("D4 should be CERTO in C Major scale mode", d4Certo)

        val (f4Certo, _) = MidiRhythmState.evaluateNoteAgainstTrack(65, chordTrack)
        assertTrue("F4 should be CERTO in C Major scale mode", f4Certo)

        // Non-scale notes: C# (61), D# (63), F# (66) should be ERRADO
        val (cSh4Errado, _) = MidiRhythmState.evaluateNoteAgainstTrack(61, chordTrack)
        assertFalse("C#4 should be ERRADO in C Major scale mode", cSh4Errado)

        val (fSh4Errado, _) = MidiRhythmState.evaluateNoteAgainstTrack(66, chordTrack)
        assertFalse("F#4 should be ERRADO in C Major scale mode", fSh4Errado)
    }

    @Test
    fun testOctaveIndependence() {
        val track = RhythmTrack("Phrase 1", 15, 0, 112, 61, false).apply {
            sourceRoot = Chord(9, ChordType.Minor) // A Minor (A, C, E)
        }
        MidiRhythmState.evalMode = MidiRhythmState.EvalMode.CHORD

        // Note A in octaves 1, 3, 5: MIDI 33, 57, 81
        listOf(33, 57, 81).forEach { aNote ->
            val (isCerto, _) = MidiRhythmState.evaluateNoteAgainstTrack(aNote, track)
            assertTrue("A in octave ${aNote / 12} must be CERTO for Am", isCerto)
        }
    }

    @Test
    fun testBassTrack_AnchoredToYamahaCStandard() {
        val bassTrack = RhythmTrack("Bass", 11, 0, 112, 33, false).apply {
            sourceRoot = Chord(7, ChordType.Major) // Even if sourceRoot was set to G, Bass standard root is C!
        }
        MidiRhythmState.evalMode = MidiRhythmState.EvalMode.CHORD

        // For Bass on C, C (36), E (40), G (43) are valid
        val (cCerto, label) = MidiRhythmState.evaluateNoteAgainstTrack(36, bassTrack)
        assertTrue("C2 must be CERTO on Bass", cCerto)
        assertTrue("Label should indicate Bass Fixo C", label.contains("Bass Fixo C"))
    }

    @Test
    fun testBlockWrongNotes_WhenEnabled_RejectsInvalidNote() {
        val track = RhythmTrack("Chord 1", 12, 0, 112, 0, false).apply {
            sourceRoot = Chord(0, ChordType.Major) // C Major
        }
        MidiRhythmState.tracks = listOf(track)
        MidiRhythmState.selectedTrackIndex = 0
        MidiRhythmState.recordInputSource = MidiRhythmState.RecordInputSource.OMNI
        MidiRhythmState.isRecording = true
        MidiRhythmState.blockWrongNotes = true
        MidiRhythmState.evalMode = MidiRhythmState.EvalMode.CHORD

        // Send Note On for F#4 (MIDI 66 - invalid for C Major chord)
        MidiRhythmState.processIncomingMidi(0x90, 66, 100, 1_000_000_000L)

        // Track should not have recorded note 66
        assertTrue("Track notes must be empty when wrong note was blocked", track.notes.isEmpty())
        assertEquals("Evaluated status must be false (ERRADO)", false, MidiRhythmState.lastEvaluatedStatus)

        // Now send valid note G4 (MIDI 67 - valid 5th of C)
        MidiRhythmState.processIncomingMidi(0x90, 67, 100, 1_100_000_000L)
        assertEquals("Track must contain 1 note after valid note", 1, track.notes.size)
        assertEquals(67, track.notes[0].note)
        assertEquals(true, MidiRhythmState.lastEvaluatedStatus)
    }

    @Test
    fun testLocalControl_StateUpdatesMonitoringFlag() {
        MidiRhythmState.isLocalControlOff = false
        assertFalse("Local Control should start as ON (isLocalControlOff = false)", MidiRhythmState.isLocalControlOff)

        // User activates Local OFF
        MidiRhythmState.isLocalControlOff = true
        assertTrue("Local Control OFF must be active", MidiRhythmState.isLocalControlOff)

        // User activates Local ON
        MidiRhythmState.isLocalControlOff = false
        assertFalse("Local Control ON must restore isLocalControlOff = false", MidiRhythmState.isLocalControlOff)
    }
}
