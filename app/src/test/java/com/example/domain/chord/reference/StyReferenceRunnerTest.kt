
package com.example.domain.chord.reference

import org.junit.Test
import com.example.domain.models.*
import com.example.domain.chord.*
import java.io.File
import java.nio.file.Files

class StyReferenceRunnerTest {
    @Test
    fun run13Styles() {
        println("--- REGRESSION AGAINST 13 STYLES ---")
        val refEngine = CasmTranspositionEngine()
        val oldEngine = ChordTransposer()
        
        val dir = File("../testdata/styles/")
        val files = dir.listFiles { f -> f.extension == "sty" || f.extension == "STY" } ?: emptyArray()
        
        var totalCount = 0
        var diffCount = 0

        for (file in files) {
            try {
                val sty = com.example.data.parser.StyReader().read(Files.readAllBytes(file.toPath()), file.name)
                sty.casm?.sections?.take(2)?.forEach { sec ->
                    sec.channels.take(3).forEach { ch ->
                        val n = 60
                        val ref = refEngine.transpose(n, ch.sourceChannel, ch.destChannel, ch.ntr, ch.ntt, 0, 5, ChordType.Major, ch.highKey, ch.noteLowLimit, ch.noteHighLimit)
                        val old = oldEngine.transpose(n, Chord(0, ChordType.Major), Chord(5, ChordType.Major), ch)
                        
                        val isDiff = ref.finalNote != old.generatedNote
                        totalCount++
                        if (isDiff) {
                            diffCount++
                            println("DIFF | ${file.name} | Sec:${sec.name} | Ch:${ch.sourceChannel}->${ch.destChannel} | NTR:${ch.ntr} | NTT:${ch.ntt} | Old:${old.generatedNote} | Ref:${ref.finalNote}")
                        }
                    }
                }
            } catch(e:Exception){}
        }
        println("Total checks: $totalCount | Differences: $diffCount")
    }
}
