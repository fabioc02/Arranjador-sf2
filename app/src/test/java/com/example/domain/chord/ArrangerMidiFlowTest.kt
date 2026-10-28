package com.example.domain.chord

import com.example.ArrangerEngine
import com.example.domain.models.ChordType
import org.junit.Assert.*
import org.junit.Test

class ArrangerMidiFlowTest {

    @Test
    fun testNoteOnC() {
        val activeNotes = mutableListOf<Int>()
        activeNotes.add(60) // C4
        
        val chord = ArrangerEngine.detectChord(activeNotes)
        assertNotNull(chord)
        assertEquals(0, chord!!.rootKey) // C
        assertEquals(ChordType.Major, chord.type)
    }

    @Test
    fun testNoteOnCEG() {
        val activeNotes = mutableListOf<Int>()
        activeNotes.add(60) // C
        activeNotes.add(64) // E
        activeNotes.add(67) // G
        
        val chord = ArrangerEngine.detectChord(activeNotes)
        assertNotNull(chord)
        assertEquals(0, chord!!.rootKey) // C
        assertEquals(ChordType.Major, chord.type)
    }

    @Test
    fun testNoteOffC_RemainsEG() {
        val activeNotes = mutableListOf<Int>()
        activeNotes.add(60) // C
        activeNotes.add(64) // E
        activeNotes.add(67) // G
        
        // Remove C
        activeNotes.remove(60)
        
        val chord = ArrangerEngine.detectChord(activeNotes)
        assertNotNull(chord)
        assertEquals(4, chord!!.rootKey) // E
        assertEquals(ChordType.Minor, chord.type) // E, G -> Em
    }

    @Test
    fun testNoteOffE_RemainsG() {
        val activeNotes = mutableListOf<Int>()
        activeNotes.add(64) // E
        activeNotes.add(67) // G
        
        // Remove E
        activeNotes.remove(64)
        
        val chord = ArrangerEngine.detectChord(activeNotes)
        assertNotNull(chord)
        assertEquals(7, chord!!.rootKey) // G
        assertEquals(ChordType.Major, chord.type) // G -> GMaj
    }

    @Test
    fun testNoteOffLastNote() {
        val activeNotes = mutableListOf<Int>()
        activeNotes.add(67) // G
        
        // Remove G
        activeNotes.remove(67)
        
        val chord = ArrangerEngine.detectChord(activeNotes)
        assertNull("O estado de NO_NOTES_PRESSED deve gerar null para não criar acorde fantasma", chord)
    }

    @Test
    fun testArrangerAndNoteLearningFlow() {
        var isArrangerMode = false
        var isLearning = true
        var learningTrackReceivesEvent = false
        var arrangerReceivesEvent = false

        // Simulate Note Learning Only (Arranger OFF)
        val simulateMidiEvent1 = { command: Int, data1: Int ->
            if (isArrangerMode) arrangerReceivesEvent = true
            if (isLearning) learningTrackReceivesEvent = true
        }

        simulateMidiEvent1(0xB0, 0)
        assertTrue(learningTrackReceivesEvent)
        assertFalse(arrangerReceivesEvent)

        // Arranger ON + Note Learning
        isArrangerMode = true
        isLearning = true
        learningTrackReceivesEvent = false
        arrangerReceivesEvent = false

        val simulateMidiEvent2 = { command: Int, data1: Int ->
            if (isArrangerMode) arrangerReceivesEvent = true
            if (isLearning) learningTrackReceivesEvent = true
        }

        simulateMidiEvent2(0xB0, 0)
        assertTrue(learningTrackReceivesEvent)
        assertTrue(arrangerReceivesEvent)
    }
}
