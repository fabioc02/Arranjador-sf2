package com.example.domain.chord.lab

import org.junit.Test
import java.io.File
import java.nio.file.Files
import com.example.data.parser.StyReader

class TranspositionLabAnalyzerTest {
    @Test
    fun analyzeAllStyles() {
        val dir = File("../testdata/styles/") // Ah, the test runs with Cwd as app/
        val files = dir.listFiles { f -> f.extension.equals("sty", true) } ?: return
        
        var totalStys = 0
        var totalSections = 0
        var totalChannels = 0
        
        val ntrCounts = mutableMapOf<String, Int>()
        val nttCounts = mutableMapOf<String, Int>()
        val noteLimits = mutableMapOf<String, Int>()
        val highKeys = mutableMapOf<String, Int>()
        
        File("test_stats.txt").bufferedWriter().use { out ->
            for (file in files) {
                totalStys++
                try {
                    val sty = StyReader().read(Files.readAllBytes(file.toPath()), file.name)
                    sty.casm?.sections?.forEach { sec ->
                        totalSections++
                        sec.channels.forEach { ch ->
                            totalChannels++
                            
                            ntrCounts[ch.ntr.name] = ntrCounts.getOrDefault(ch.ntr.name, 0) + 1
                            nttCounts[ch.ntt.name] = nttCounts.getOrDefault(ch.ntt.name, 0) + 1
                            
                            val nl = "${ch.noteLowLimit}-${ch.noteHighLimit}"
                            noteLimits[nl] = noteLimits.getOrDefault(nl, 0) + 1
                            
                            val hk = ch.highKey.toString()
                            highKeys[hk] = highKeys.getOrDefault(hk, 0) + 1
                        }
                    }
                } catch (e: Exception) {
                    out.write("Error parsing ${file.name}: ${e.message}\n")
                }
            }
            
            out.write("--- STATS ---\n")
            out.write("Total STYs: $totalStys\n")
            out.write("Total Sections: $totalSections\n")
            out.write("Total CASM Channels: $totalChannels\n")
            
            out.write("\nNTR Counts:\n")
            ntrCounts.entries.sortedByDescending { it.value }.forEach { out.write("${it.key}: ${it.value}\n") }
            
            out.write("\nNTT Counts:\n")
            nttCounts.entries.sortedByDescending { it.value }.forEach { out.write("${it.key}: ${it.value}\n") }
            
            out.write("\nNote Limits (Low-High):\n")
            noteLimits.entries.sortedByDescending { it.value }.take(10).forEach { out.write("${it.key}: ${it.value}\n") }
            
            out.write("\nHigh Keys:\n")
            highKeys.entries.sortedByDescending { it.value }.take(10).forEach { out.write("${it.key}: ${it.value}\n") }
        }
    }
}
