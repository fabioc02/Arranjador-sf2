package com.example.domain.chord.reference.tables

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.models.*

class NoteLimitTableTest {
    @Test
    fun testNoteLimitsWrap() {
        val source = Chord(0, ChordType.Major)
        val target = Chord(0, ChordType.Major) // No shift
        
        // Note = 80. HighLimit = 72. 
        // Should wrap 80 -> 68.
        val result = ReferencePipeline.process(80, source, target, NTR.ROOT_FIXED, NTT.CHORD, 127, 0, 72)
        assertEquals("Should wrap down by octave", 68, result)
        
        // Note = 40. LowLimit = 60.
        // Should wrap 40 -> 52 -> 64.
        val result2 = ReferencePipeline.process(40, source, target, NTR.ROOT_FIXED, NTT.CHORD, 127, 60, 127)
        assertEquals("Should wrap up by octaves", 64, result2)
    }
}
