package com.example.domain.playback.reference

import org.junit.Test
import org.junit.Assert.*
import com.example.data.parser.StyReader
import java.io.File
import java.nio.file.Files

class HighKeyScannerTest {
    @Test
    fun scanHighKeyValues() {
        val dir = File("../testdata/styles/")
        val files = dir.listFiles { f -> f.extension.equals("sty", true) || f.extension.matches(Regex("S\\d+")) } ?: return
        
        val highKeyMap = mutableMapOf<Int, Int>()
        
        for (file in files) {
            val sty = StyReader().read(Files.readAllBytes(file.toPath()), file.name)
            val casm = sty.casm ?: continue
            
            for (section in casm.sections) {
                for (ch in section.channels) {
                    highKeyMap[ch.highKey] = (highKeyMap[ch.highKey] ?: 0) + 1
                }
            }
        }
        println("HIGH KEY DISTRIBUTION:")
        highKeyMap.entries.sortedBy { it.key }.forEach {
            println("HighKey ${it.key}: ${it.value} occurrences")
        }
        assertTrue(true)
    }
}
