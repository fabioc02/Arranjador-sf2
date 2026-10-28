package com.example.domain.playback.reference

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.chord.reference.tables.ReferencePipeline
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.NTR
import com.example.domain.models.NTT

class NoteLimitReferenceTest {
    @Test
    fun verifyNoteLimitWrap() {
        val sourceChord = Chord(0, ChordType.Major) // C
        val targetChord = Chord(0, ChordType.Major) // C
        
        // Wrap down (Above High Limit)
        val resultHigh = ReferencePipeline.process(
            originalNote = 80,
            sourceChord = sourceChord,
            targetChord = targetChord,
            ntr = NTR.ROOT_FIXED,
            ntt = NTT.BYPASS,
            highKey = 127,
            lowLimit = 0,
            highLimit = 70 // Drops 80 -> 68
        )
        assertEquals(68, resultHigh)
        
        // Wrap up (Below Low Limit)
        val resultLow = ReferencePipeline.process(
            originalNote = 30,
            sourceChord = sourceChord,
            targetChord = targetChord,
            ntr = NTR.ROOT_FIXED,
            ntt = NTT.BYPASS,
            highKey = 127,
            lowLimit = 40, // Brings 30 -> 42
            highLimit = 127
        )
        assertEquals(42, resultLow)
    }
}
