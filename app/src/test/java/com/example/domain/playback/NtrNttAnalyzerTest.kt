
package com.example.domain.playback

import org.junit.Test
import java.io.File
import java.nio.file.Files

class NtrNttAnalyzerTest {
    @Test
    fun analyze() {
        val dir = File("../testdata/styles/")
        val files = dir.listFiles { f -> f.extension == "sty" || f.extension == "STY" } ?: emptyArray()
        
        val ntrCount = mutableMapOf<String, Int>()
        val nttCount = mutableMapOf<String, Int>()
        
        val ntrDetails = mutableMapOf<String, MutableSet<String>>()
        val nttDetails = mutableMapOf<String, MutableSet<String>>()
        
        for (file in files) {
            try {
                val data = Files.readAllBytes(file.toPath())
                val sty = com.example.data.parser.StyReader().read(data, file.name)
                if (sty.casm == null) continue
                
                for (section in sty.casm!!.sections) {
                    for (ch in section.channels) {
                        val ntrVal = ch.ntr.displayName
                        val nttVal = ch.ntt.displayName
                        
                        ntrCount[ntrVal] = (ntrCount[ntrVal] ?: 0) + 1
                        nttCount[nttVal] = (nttCount[nttVal] ?: 0) + 1
                        
                        val detailStr = "${file.name} | ${section.name} | Ch:${ch.sourceChannel}->${ch.destChannel}"
                        ntrDetails.getOrPut(ntrVal) { mutableSetOf() }.add(detailStr)
                        nttDetails.getOrPut(nttVal) { mutableSetOf() }.add(detailStr)
                    }
                }
            } catch (e: Exception) {}
        }
        
        println("=== NTR ANALYSIS ===")
        ntrCount.entries.sortedBy { it.key }.forEach { entry ->
            val ntrVal = entry.key
            println("NTR $ntrVal : ${entry.value} occurrences")
            val examples = ntrDetails[ntrVal]?.take(5)?.joinToString(" || ")
            println("   Examples: $examples")
        }
        
        println("=== NTT ANALYSIS ===")
        nttCount.entries.sortedBy { it.key }.forEach { entry ->
            val nttVal = entry.key
            println("NTT $nttVal : ${entry.value} occurrences")
            val examples = nttDetails[nttVal]?.take(5)?.joinToString(" || ")
            println("   Examples: $examples")
        }
    }
}
