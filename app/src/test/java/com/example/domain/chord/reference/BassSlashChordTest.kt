package com.example.domain.chord.reference

import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.NTR
import com.example.domain.models.NTT
import com.example.domain.chord.engine.ReferenceTranspositionEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class BassSlashChordTest {

    private val engine = ReferenceTranspositionEngine()

    @Test
    fun testGD_SlashChord_ShouldResultInD1_MIDI26() {
        val originalNote = 36 // C2
        
        val sourceChord = Chord(0, ChordType.Major7) // CMaj7
        val targetChord = Chord(7, ChordType.Major, bassKey = 2) // G/D

        // According to trace C1.3 for Bass in PopReggaeBR.sty:
        // Source Note = C2 (36)
        // destChannel = 10 (Bass)
        // NTR = 0 (ROOT_FIXED)
        // NTT = 2 (CHORD)
        // HighKey = 0
        // LowLimit = 0
        // HighLimit = 95
        
        val result = engine.transpose(
            originalNote = originalNote,
            sourceChannel = 10,
            destChannel = 10,
            ntr = NTR.ROOT_FIXED,
            ntt = NTT.CHORD,
            sourceChord = sourceChord,
            targetChord = targetChord,
            highKey = 0,
            noteLimitLow = 0,
            noteLimitHigh = 95
        )

        // Math:
        // Base Shift: 36 + (2 - 0) = 38 (D2)
        // NTT Chord: D is 5th of G, remains D2 (38)
        // HighKey: Bass (2) > HK (0) -> drop octave: 38 - 12 = 26 (D1)
        // Limits: 26 in [0..95] -> 26
        
        assertEquals(26, result.generatedNote)
    }

    @Test
    fun testCE_SlashChord_ShouldResultInE1_MIDI28() {
        val originalNote = 36 // C2
        
        val sourceChord = Chord(0, ChordType.Major7) // CMaj7
        val targetChord = Chord(0, ChordType.Major, bassKey = 4) // C/E

        val result = engine.transpose(
            originalNote = originalNote,
            sourceChannel = 10,
            destChannel = 10,
            ntr = NTR.ROOT_FIXED,
            ntt = NTT.CHORD,
            sourceChord = sourceChord,
            targetChord = targetChord,
            highKey = 0,
            noteLimitLow = 0,
            noteLimitHigh = 95
        )

        // Math:
        // Base Shift: 36 + (4 - 0) = 40 (E2)
        // NTT Chord: E is 3rd of C, remains E2 (40)
        // HighKey: Bass (4) > HK (0) -> drop octave: 40 - 12 = 28 (E1)
        
        assertEquals(28, result.generatedNote)
    }
}
