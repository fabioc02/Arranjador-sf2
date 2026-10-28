package com.example.domain.chord.reference.tables

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.models.*

class VoiceLeadingPassingToneTest {
    @Test
    fun testPassingTonePreservation() {
        val source = Chord(0, ChordType.Major) // C Major
        val target = Chord(7, ChordType.Major) // G Major
        
        // Original D4 (62) - Passing tone in C Major (2nd)
        // With ROOT_FIXED + CHORD NTT:
        // C(0) -> G(7) = +7 semitones
        // D4(62) + 7 = A4(69)
        // A is the 2nd degree of G Major. It perfectly preserves the melodic interval.
        val result = ReferencePipeline.process(62, source, target, NTR.ROOT_FIXED, NTT.CHORD, 127, 0, 127)
        assertEquals("Passing tone should shift by root delta to preserve relative degree", 69, result)
    }
}
