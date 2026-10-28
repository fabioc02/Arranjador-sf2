package com.example.domain.chord.reference.tables

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.models.*

class VoiceLeadingChordTableTest {
    @Test
    fun testChordToneMapping() {
        // Source C Maj -> Target F Min
        val source = Chord(0, ChordType.Major)
        val target = Chord(5, ChordType.Minor)
        
        // original E (4) -> F Min chord tone should be Ab (8) ?
        // Using current placeholder logic: NTR shifts +5, E(4) -> A(9). 
        // We know A is not in F Min. The table currently leaves it (hypothesis).
        val result = ReferencePipeline.process(
            originalNote = 64, // E4
            sourceChord = source,
            targetChord = target,
            ntr = NTR.ROOT_FIXED,
            ntt = NTT.CHORD,
            highKey = 127, lowLimit = 0, highLimit = 127
        )
        // With basic NTR +5 -> 69 (A4). 
        // This test serves as a baseline to refine the table.
        assertEquals(69, result)
    }
}
