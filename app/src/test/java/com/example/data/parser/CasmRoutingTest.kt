package com.example.data.parser

import com.example.domain.models.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CasmRoutingTest {

    private fun loadSty(filename: String): StyFile {
        val file = File("../testdata/styles/$filename")
        assertTrue("Test file not found: $filename", file.exists())
        val data = Files.readAllBytes(file.toPath())
        val reader = StyReader()
        val sty = reader.read(data, file.name)
        assertTrue("Parsing failed for $filename", sty.valid)
        return sty
    }

    @Test
    fun testCtb2DestinationChannelOffset() {
        val sty = loadSty("BregaCalypso.sty")
        val casm = sty.casm
        
        // CSEG 2 corresponds to index 1 (Intro B, Ending B)
        val cseg2 = casm.sections.filter { it.name.contains("Intro B") }
        assertTrue(cseg2.isNotEmpty())
        
        val section = cseg2.first()
        val auxBass = section.channels.find { it.sourceChannel == 0x02 }
        assertNotNull("Auxiliary Bass (Source 02) not found", auxBass)
        
        // Test +09 offset mapping
        assertEquals("Dest channel should be 0A", 0x0A, auxBass!!.destChannel)
        
        val auxChord1 = section.channels.find { it.sourceChannel == 0x03 }
        assertNotNull(auxChord1)
        assertEquals("Dest channel should be 0B", 0x0B, auxChord1!!.destChannel)
        
        val bypassBass = section.channels.find { it.sourceChannel == 0x0A }
        assertNotNull(bypassBass)
        assertEquals("Dest channel should be 0A", 0x0A, bypassBass!!.destChannel)
        
        // Also check NTR and NTT
        assertEquals("NTR should be 00", NTR.ROOT_TRANS, auxBass.ntr)
        assertTrue("NTT should be Melody or Dorian", auxBass.ntt == NTT.MELODY || auxBass.ntt == NTT.DORIAN)
    }

    @Test
    fun testCasmRoutingMain() {
        val sty = loadSty("BregaCalypso.sty")
        val casm = sty.casm
        
        val mainA = casm.sections.find { it.name == "Main A" }
        assertNotNull(mainA)
        
        val bass = mainA!!.channels.find { it.sourceChannel == 0x0A }
        assertNotNull(bass)
        
        assertEquals(0x0A, bass!!.destChannel)
        assertEquals(NTR.ROOT_TRANS, bass.ntr)
    }

    @Test
    fun testCasmRoutingIntroAux() {
        val sty = loadSty("BregaCalypso.sty")
        val casm = sty.casm
        
        val introA = casm.sections.find { it.name == "Intro B" }
        assertNotNull(introA)
        
        val auxBass = introA!!.channels.find { it.sourceChannel == 0x02 }
        assertNotNull(auxBass)
        
        assertEquals(0x0A, auxBass!!.destChannel)
        assertEquals(NTR.ROOT_TRANS, auxBass.ntr)
    }

    @Test
    fun testCasmRoutingIntroBypass() {
        val sty = loadSty("BregaCalypso.sty")
        val casm = sty.casm
        
        val introA = casm.sections.find { it.name == "Intro B" }
        assertNotNull(introA)
        
        val bypassBass = introA!!.channels.find { it.sourceChannel == 0x0A }
        assertNotNull(bypassBass)
        
        assertEquals(0x0A, bypassBass!!.destChannel)
        assertEquals(NTR.ROOT_TRANS, bypassBass.ntr)
    }
}
