package com.example.domain.playback.reference

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.chord.reference.tables.PentatonicTable
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.chord.reference.tables.ConfidenceLevel

class NttPentatonicReferenceTest {
    @Test
    fun verifyPentatonicTable() {
        val table = PentatonicTable()
        val result = table.mapNote(60, 60, Chord(0, ChordType.Major), Chord(5, ChordType.Major))
        assertEquals(ConfidenceLevel.HYPOTHESIS, result.confidence)
    }
}
