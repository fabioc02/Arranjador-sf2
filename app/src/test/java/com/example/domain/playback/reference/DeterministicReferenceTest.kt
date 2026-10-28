package com.example.domain.playback.reference

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.chord.reference.tables.ReferencePipeline
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.NTR
import com.example.domain.models.NTT

class DeterministicReferenceTest {
    @Test
    fun verifyDeterminism() {
        // Run same input 1000 times, ensure exact same output
        val sourceChord = Chord(0, ChordType.Major)
        val targetChord = Chord(5, ChordType.Minor7)
        
        val baseline = ReferencePipeline.process(60, sourceChord, targetChord, NTR.ROOT_FIXED, NTT.CHORD, 127, 0, 127)
        
        for (i in 0..1000) {
            val result = ReferencePipeline.process(60, sourceChord, targetChord, NTR.ROOT_FIXED, NTT.CHORD, 127, 0, 127)
            assertEquals("Output must be deterministic", baseline, result)
        }
    }
}
