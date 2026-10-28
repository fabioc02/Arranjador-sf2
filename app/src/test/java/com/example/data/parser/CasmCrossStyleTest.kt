package com.example.data.parser

import com.example.domain.models.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CasmCrossStyleTest {

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
    fun testAllStylesFollowPattern() {
        val stylesDir = File("../testdata/styles")
        val styles = stylesDir.listFiles { _, name -> name.lowercase().endsWith(".sty") }
            ?.map { it.name }
            ?: emptyList()
            
        assertTrue("No styles found in testdata/styles", styles.isNotEmpty())
        
        for (filename in styles) {
            val sty = loadSty(filename)
            val casm = sty.casm
            
            // Verifying Main sections routing
            val mains = casm.sections.filter { it.name.startsWith("Main") }
            if (mains.isNotEmpty()) {
                for (main in mains) {
                    val bass = main.channels.find { it.sourceChannel == 0x0A }
                    if (bass != null) {
                        val dest = bass.destChannel
                        assertTrue("Dest should be 0A or FF in $filename ${main.name} (got $dest)", dest == 0x0A || dest == 0xFF)
                        assertEquals("Failed NTR in $filename ${main.name}", NTR.ROOT_TRANS, bass.ntr)
                        
                        assertTrue("Failed NTT in $filename ${main.name}", bass.ntt != NTT.UNKNOWN)
                    }
                }
            }
            
            // Verifying Complex Intro sections routing (Intro B and Intro C, Endings)
            val complexIntros = casm.sections.filter { 
                it.name.startsWith("Intro B") || it.name.startsWith("Intro C") || 
                it.name.startsWith("Ending B") || it.name.startsWith("Ending C")
            }
            for (intro in complexIntros) {
                val auxBass = intro.channels.find { it.sourceChannel == 0x02 }
                if (auxBass != null) {
                    val dest = auxBass.destChannel
                    assertTrue("Dest should be 0A or FF in $filename ${intro.name} auxBass (got $dest)", dest == 0x0A || dest == 0xFF)
                    // Aux bass in Intro B/C can use Dorian, Chord, Melody, or Bypass (fixed solo)
                    assertTrue(
                        "NTT must be a valid known NTT in $filename ${intro.name} auxBass. Got ${auxBass.ntt}", 
                        auxBass.ntt != NTT.UNKNOWN
                    )
                }
                
                val bypassBass = intro.channels.find { it.sourceChannel == 0x0A }
                if (bypassBass != null) {
                    val dest = bypassBass.destChannel
                    assertTrue("Dest should be 0A or FF in $filename ${intro.name} bypassBass (got $dest)", dest == 0x0A || dest == 0xFF)
                    
                    // Real styles vary in their NTT for channel 0x0A during Intros/Endings (Bypass, Chord, Melody, Harmonic Minor, etc.)
                    assertTrue(
                        "NTT must be a valid known NTT in $filename ${intro.name} bypassBass. Got ${bypassBass.ntt}", 
                        bypassBass.ntt != NTT.UNKNOWN
                    )
                }
            }
        }
    }
}
