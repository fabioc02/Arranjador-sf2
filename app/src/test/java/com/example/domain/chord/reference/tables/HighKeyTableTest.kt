package com.example.domain.chord.reference.tables

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.models.*

class HighKeyTableTest {
    @Test
    fun testHighKeyDropsOctave() {
        val source = Chord(0, ChordType.Major)
        
        // HighKey = 0. Target = F (5). Since 5 > 0, it should drop 12.
        val target = Chord(5, ChordType.Major)
        
        // C4(60) -> F4(65) by ROOT_FIXED. 
        // Then HighKey(0) triggers -> F3(53).
        val result = ReferencePipeline.process(60, source, target, NTR.ROOT_FIXED, NTT.CHORD, 0, 0, 127)
        assertEquals("HighKey 0 should drop F4 to F3", 53, result)
    }
    
    @Test
    fun testHighKeyDoesNotDropIfBelowOrEqual() {
        val source = Chord(0, ChordType.Major)
        val target = Chord(5, ChordType.Major) // F
        
        // HighKey = 6. 5 <= 6, so no drop.
        val result = ReferencePipeline.process(60, source, target, NTR.ROOT_FIXED, NTT.CHORD, 6, 0, 127)
        assertEquals("HighKey 6 should not drop F4", 65, result)
    }
}
