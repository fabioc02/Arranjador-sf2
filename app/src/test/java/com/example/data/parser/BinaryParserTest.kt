package com.example.data.parser

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class BinaryParserTest {

    @Test
    fun testOriginalStyParsing() {
        val file = File("../testdata/styles_original/BregaCalypso.sty")
        assertTrue("Test file not found: ${file.absolutePath}", file.exists())
        
        val data = file.readBytes()
        val reader = StyReader()
        val sty = reader.read(data, "BregaCalypso.S263.sty")
        
        assertTrue("STY should be valid", sty.valid)
        assertNotNull("CASM should be parsed", sty.casm)
        assertTrue("CASM should be valid", sty.casm.valid)
        
        val sections = sty.casm.sections
        assertTrue("Should have sections", sections.isNotEmpty())
        
        var foundMainA = false
        var foundChordRule = false
        var foundHighKeyLimit = false
        
        for (section in sections) {
            if (section.name == "Main A") {
                foundMainA = true
                for (channel in section.channels) {
                    // Check if NTTs are valid (mostly 0, 2, 8)
                    if (channel.ntt == com.example.domain.models.NTT.CHORD) {
                        foundChordRule = true
                    }
                    if (channel.highKey > 0) {
                        foundHighKeyLimit = true
                    }
                }
            }
        }
        
        assertTrue("Main A section should be found", foundMainA)
        assertTrue("Chord rules (NTT=2) should be found", foundChordRule)
    }
}
