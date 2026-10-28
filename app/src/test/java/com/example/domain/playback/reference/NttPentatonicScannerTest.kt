package com.example.domain.playback.reference

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.models.NTT
import com.example.data.parser.StyReader
import java.io.File
import java.nio.file.Files

class NttPentatonicScannerTest {
    @Test
    fun scanAllPentatonicOccurrences() {
        val dir = File("../testdata/styles/")
        val files = dir.listFiles { f -> f.extension.equals("sty", true) || f.extension.matches(Regex("S\\d+")) } ?: return
        
        var totalPentatonic = 0
        var totalCasm = 0
        
        for (file in files) {
            val sty = StyReader().read(Files.readAllBytes(file.toPath()), file.name)
            val casm = sty.casm ?: continue
            totalCasm++
            
            for (section in casm.sections) {
                for (ch in section.channels) {
                    if (ch.ntt == NTT.PENTATONIC) {
                        println("FOUND PENTATONIC: File=${file.name}, Section=${section.name}, Channel=${ch.destChannel}, SourceCh=${ch.sourceChannel}, NTR=${ch.ntr}, HighKey=${ch.highKey}")
                        totalPentatonic++
                    }
                }
            }
        }
        println("TOTAL PENTATONIC CHANNELS FOUND: $totalPentatonic")
        assertTrue(true)
    }
}
