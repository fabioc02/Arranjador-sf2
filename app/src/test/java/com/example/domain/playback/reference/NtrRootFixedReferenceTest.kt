package com.example.domain.playback.reference

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.chord.reference.tables.ReferencePipeline
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.NTR
import com.example.domain.models.NTT

class NtrRootFixedReferenceTest {
    @Test
    fun verifyRootFixedShift() {
        val sourceChord = Chord(0, ChordType.Major) // C
        val targetChord = Chord(5, ChordType.Major) // F
        
        // 60 = C4. With Root Fixed, the distance is +5.
        // It should become 65 (F4).
        val result = ReferencePipeline.process(
            originalNote = 60,
            sourceChord = sourceChord,
            targetChord = targetChord,
            ntr = NTR.ROOT_FIXED,
            ntt = NTT.BYPASS, // Ensure NTT doesn't alter it
            highKey = 127,
            lowLimit = 0,
            highLimit = 127
        )
        
        assertEquals(65, result)
    }
}
