
package com.example.domain.playback

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.models.*
import com.example.domain.chord.*

class Ntt00Test {
    @Test
    fun testBypass() {
        val transposer = ChordTransposer()
        val cMaj = Chord(0, ChordType.Major)
        val dMin = Chord(2, ChordType.Minor)
        
        val casm = CasmChannel(ntr = NTR.ROOT_FIXED, ntt = NTT.BYPASS)
        
        // Let's test note 60 (C)
        val res1 = transposer.transpose(60, cMaj, dMin, casm)
        assertEquals(60, res1.generatedNote)
    }
}
