package com.example.domain.playback.reference

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.chord.reference.tables.ChordTable
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.chord.reference.tables.ConfidenceLevel

class NttChordReferenceTest {
    @Test
    fun verifyChordTableOutputs() {
        val table = ChordTable()
        val sourceChord = Chord(0, ChordType.Major)
        val targetChord = Chord(5, ChordType.Major)
        
        val result = table.mapNote(60, 60, sourceChord, targetChord) // C to F major
        
        assertNotNull(result)
        assertEquals(ConfidenceLevel.HYPOTHESIS, result.confidence)
        // Currently it's a hypothesis, it returns the input without mapping logic because NTR handles the basic shift
    }
}
