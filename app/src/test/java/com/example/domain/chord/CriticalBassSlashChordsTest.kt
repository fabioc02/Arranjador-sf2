package com.example.domain.chord

import com.example.domain.models.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalBassSlashChordsTest {

    private val sourceChord = Chord(0, ChordType.Major) // C
    private val bassCasm = CasmChannel(
        sourceChannel = 10,
        destChannel = 10,
        sourceChordRoot = "C",
        sourceChordType = "Maj",
        ntr = NTR.ROOT_TRANS,
        ntt = NTT.BASS,
        highKey = 3, // Eb (Yamaha standard High Key for Bass)
        noteLowLimit = 28, // E1
        noteHighLimit = 67 // G4
    )

    private val bassRule = YamahaRuleFactory.getRule(bassCasm.ntr, bassCasm.ntt)

    /**
     * Teste 1: Dm/A (D minor with A in bass)
     * Deve resultar em A1 (MIDI 33, ~55 Hz), JAMAIS caindo para sub-bass A0 (MIDI 21).
     */
    @Test
    fun testDmA_BassPitchIsNaturalA1() {
        val target = Chord(rootKey = 2, type = ChordType.Minor, bassKey = 9)
        val result = bassRule.transpose(36, sourceChord, target, bassCasm)
        assertEquals("Dm/A bass must be A1 (MIDI 33)", 33, result)
    }

    /**
     * Teste 2: C/A (C major with A in bass)
     * Deve resultar em A1 (MIDI 33), sem double-drop.
     */
    @Test
    fun testCA_BassPitchIsNaturalA1() {
        val target = Chord(rootKey = 0, type = ChordType.Major, bassKey = 9)
        val result = bassRule.transpose(36, sourceChord, target, bassCasm)
        assertEquals("C/A bass must be A1 (MIDI 33)", 33, result)
    }

    /**
     * Teste 3: Dm/B (D minor with B in bass)
     * Deve resultar em B1 (MIDI 35).
     */
    @Test
    fun testDmB_BassPitchIsNaturalB1() {
        val target = Chord(rootKey = 2, type = ChordType.Minor, bassKey = 11)
        val result = bassRule.transpose(36, sourceChord, target, bassCasm)
        assertEquals("Dm/B bass must be B1 (MIDI 35)", 35, result)
    }

    /**
     * Teste 4: C#m/A (C# minor with A in bass)
     * Deve resultar em A1 (MIDI 33).
     */
    @Test
    fun testCshmA_BassPitchIsNaturalA1() {
        val target = Chord(rootKey = 1, type = ChordType.Minor, bassKey = 9)
        val result = bassRule.transpose(36, sourceChord, target, bassCasm)
        assertEquals("C#m/A bass must be A1 (MIDI 33)", 33, result)
    }

    /**
     * Teste 5: C/G (C major with G in bass)
     * Deve resultar em G1 (MIDI 31), sem cair para G0 (MIDI 19).
     */
    @Test
    fun testCG_BassPitchIsNaturalG1() {
        val target = Chord(rootKey = 0, type = ChordType.Major, bassKey = 7)
        val result = bassRule.transpose(36, sourceChord, target, bassCasm)
        assertEquals("C/G bass must be G1 (MIDI 31)", 31, result)
    }

    /**
     * Testes adicionais de acordes invertidos padrão (C/E, F/A, G/B)
     */
    @Test
    fun testStandardSlashChords() {
        // C/E -> E1 (MIDI 28)
        val ce = bassRule.transpose(36, sourceChord, Chord(0, ChordType.Major, bassKey = 4), bassCasm)
        assertEquals("C/E must be E1 (28)", 28, ce)

        // F/A -> A1 (MIDI 33)
        val fa = bassRule.transpose(36, sourceChord, Chord(5, ChordType.Major, bassKey = 9), bassCasm)
        assertEquals("F/A must be A1 (33)", 33, fa)

        // G/B -> B1 (MIDI 35)
        val gb = bassRule.transpose(36, sourceChord, Chord(7, ChordType.Major, bassKey = 11), bassCasm)
        assertEquals("G/B must be B1 (35)", 35, gb)
    }

    /**
     * Verifica que o baixo de todos os acordes permanece na faixa natural de baixo (28..67)
     */
    @Test
    fun testAllBassPitchesRemainWithinNaturalRegister() {
        val targets = listOf(
            Chord(2, ChordType.Minor, bassKey = 9),
            Chord(0, ChordType.Major, bassKey = 9),
            Chord(2, ChordType.Minor, bassKey = 11),
            Chord(1, ChordType.Minor, bassKey = 9),
            Chord(0, ChordType.Major, bassKey = 7),
            Chord(0, ChordType.Major, bassKey = 4),
            Chord(5, ChordType.Major, bassKey = 9),
            Chord(7, ChordType.Major, bassKey = 11)
        )
        for (tgt in targets) {
            val note = bassRule.transpose(36, sourceChord, tgt, bassCasm)
            assertTrue("Bass note $note for $tgt must be >= 28", note >= 28)
            assertTrue("Bass note $note for $tgt must be <= 67", note <= 67)
        }
    }
}
