package com.example.domain.chord.legacy

import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.NTR
import com.example.domain.models.NTT
import com.example.domain.models.CasmChannel
import com.example.domain.chord.ChordTransposer
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyBassSlashChordTest {

    private val transposer = ChordTransposer()

    @Test
    fun testGD_SlashChord_ShouldResultInD1_MIDI26() {
        val originalNote = 36 // C2
        
        val sourceChord = Chord(0, ChordType.Major7) // CMaj7
        val targetChord = Chord(7, ChordType.Major, bassKey = 2) // G/D

        val casm = CasmChannel(
            sourceChannel = 10,
            destChannel = 10, // Bass
            ntr = NTR.ROOT_FIXED,
            ntt = NTT.CHORD,
            highKey = 0,
            noteLowLimit = 0,
            noteHighLimit = 95
        )
        
        val result = transposer.transpose(
            originalNote = originalNote,
            sourceChord = sourceChord,
            targetChord = targetChord,
            casmChannel = casm
        )
        
        assertEquals(26, result.generatedNote)
    }

    @Test
    fun testCE_SlashChord_ShouldResultInE1_MIDI28() {
        val originalNote = 36 // C2
        
        val sourceChord = Chord(0, ChordType.Major7) // CMaj7
        val targetChord = Chord(0, ChordType.Major, bassKey = 4) // C/E

        val casm = CasmChannel(
            sourceChannel = 10,
            destChannel = 10, // Bass
            ntr = NTR.ROOT_FIXED,
            ntt = NTT.CHORD,
            highKey = 0,
            noteLowLimit = 0,
            noteHighLimit = 95
        )

        val result = transposer.transpose(
            originalNote = originalNote,
            sourceChord = sourceChord,
            targetChord = targetChord,
            casmChannel = casm
        )
        
        assertEquals(28, result.generatedNote)
    }
}
