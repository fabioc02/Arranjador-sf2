
package com.example.domain.playback

import org.junit.Test
import java.io.File
import java.nio.file.Files

class NtrNttAnalyzerAcusticoTest {
    @Test
    fun analyze() {
        val file = File("../testdata/styles/AcusticoBR1.sty")
        val data = Files.readAllBytes(file.toPath())
        val sty = com.example.data.parser.StyReader().read(data, file.name)
        
        for (section in sty.casm!!.sections) {
            println("=== SECTION: ${section.name} ===")
            for (ch in section.channels) {
                val ntrVal = ch.ntr.displayName
                val nttVal = ch.ntt.displayName
                println("Ch:${ch.sourceChannel}->${ch.destChannel} | NTR:$ntrVal | NTT:$nttVal")
            }
        }
    }
}
