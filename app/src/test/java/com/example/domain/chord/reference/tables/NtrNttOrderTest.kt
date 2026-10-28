package com.example.domain.chord.reference.tables

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.models.*

class NtrNttOrderTest {
    @Test
    fun testBypassOverridesNTR() {
        val source = Chord(0, ChordType.Major)
        val target = Chord(7, ChordType.Major)
        
        // NTR.BYPASS preserves original note without transposition
        val result = ReferencePipeline.process(
            originalNote = 60,
            sourceChord = source,
            targetChord = target,
            ntr = NTR.BYPASS,
            ntt = NTT.BYPASS,
            highKey = 127, lowLimit = 0, highLimit = 127
        )
        
        assertEquals(60, result)
    }
}
