package com.example.domain.playback.reference

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.chord.reference.tables.ReferencePipeline
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.NTR
import com.example.domain.models.NTT

class HighKeyReferenceTest {
    @Test
    fun verifyHighKeyDrop() {
        val sourceChord = Chord(0, ChordType.Major) // C
        val targetChord = Chord(7, ChordType.Major) // G (which is > 5)
        
        // 60 = C4 -> 67 (G4)
        // But HighKey = 5 (F). 
        // 7 > 5, so note drops by 12 -> 67 - 12 = 55.
        // Manual says High Key is only available for ROOT_TRANS.
        val result = ReferencePipeline.process(
            originalNote = 60,
            sourceChord = sourceChord,
            targetChord = targetChord,
            ntr = NTR.ROOT_TRANS,
            ntt = NTT.BYPASS,
            highKey = 5,
            lowLimit = 0,
            highLimit = 127
        )
        
        assertEquals(55, result)
    }
}
