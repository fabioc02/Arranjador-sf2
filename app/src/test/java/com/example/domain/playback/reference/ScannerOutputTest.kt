package com.example.domain.playback.reference

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.models.NTT
import com.example.data.parser.StyReader
import java.io.File
import java.nio.file.Files

class ScannerOutputTest {
    @Test
    fun writeScansToFile() {
        val dir = File("../testdata/styles/")
        val files = dir.listFiles { f -> f.extension.equals("sty", true) || f.extension.matches(Regex("S\\d+")) } ?: return
        
        val outFile = File("docs/reports/SCAN_RESULTS.txt")
        outFile.parentFile.mkdirs()
        val out = outFile.bufferedWriter()
        
        var totalPentatonic = 0
        val highKeyMap = mutableMapOf<Int, Int>()
        
        for (file in files) {
            val sty = StyReader().read(Files.readAllBytes(file.toPath()), file.name)
            val casm = sty.casm ?: continue
            
            for (section in casm.sections) {
                for (ch in section.channels) {
                    highKeyMap[ch.highKey] = (highKeyMap[ch.highKey] ?: 0) + 1
                    if (ch.ntt == NTT.PENTATONIC) {
                        out.write("PENTATONIC: File=${file.name}, Sec=${section.name}, DestCh=${ch.destChannel}, SrcCh=${ch.sourceChannel}\n")
                        totalPentatonic++
                    }
                }
            }
        }
        out.write("TOTAL PENTATONIC: $totalPentatonic\n")
        out.write("HIGH KEY DISTRIBUTION:\n")
        highKeyMap.entries.sortedBy { it.key }.forEach {
            out.write("HighKey ${it.key}: ${it.value}\n")
        }
        out.close()
        assertTrue(true)
    }
}
