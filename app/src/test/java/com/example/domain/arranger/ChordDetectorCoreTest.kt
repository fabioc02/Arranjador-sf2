package com.example.domain.arranger

import com.example.domain.models.ChordType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChordDetectorCoreTest {

    private lateinit var state: ArrangerCoreState
    private lateinit var detector: ChordDetectorCore

    @Before
    fun setup() {
        state = ArrangerCoreState()
        detector = ChordDetectorCore(state)
        state.splitPoint = 54 // F#2
    }

    @Test
    fun testMajorChordDetection() {
        // C Major: C2 (48), E2 (52), G2 (55 - Wait, G2 is above F#2 if split is 54)
        // Let's use lower octave: C1 (36), E1 (40), G1 (43)
        detector.processNoteOn(36, 100)
        detector.processNoteOn(40, 100)
        detector.processNoteOn(43, 100)

        val chord = state.currentChord.get()
        assertEquals(0, chord.rootKey) // C
        assertEquals(ChordType.Major, chord.type)
        assertEquals(0, state.currentBassNote)
    }

    @Test
    fun testIgnoreNotesAboveSplitPoint() {
        // C1 (36), E1 (40), C3 (60 - should be ignored)
        detector.processNoteOn(36, 100)
        detector.processNoteOn(40, 100)
        detector.processNoteOn(60, 100)

        // It should still detect based only on 36 and 40.
        // C, E alone might not form a full triad, but currently our fallback takes root.
        val chord = state.currentChord.get()
        assertEquals(0, chord.rootKey)
    }

    @Test
    fun testLeftHoldMaintainsChordAfterNoteOff() {
        state.leftHoldEnabled.set(true)
        
        // Play C Major
        detector.processNoteOn(36, 100)
        detector.processNoteOn(40, 100)
        detector.processNoteOn(43, 100)

        var chord = state.currentChord.get()
        assertEquals(0, chord.rootKey)

        // Release keys
        detector.processNoteOff(36)
        detector.processNoteOff(40)
        detector.processNoteOff(43)

        // Because left hold is true, chord should still be C Major
        chord = state.currentChord.get()
        assertEquals(0, chord.rootKey)
    }
}
