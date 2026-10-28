
package com.example.domain.playback

import org.junit.Test
import java.io.File
import java.nio.file.Files

class DumpIntroBTest {
    @Test
    fun dump() {
        val file = File("../testdata/styles/BregaCalypso.sty")
        val data = Files.readAllBytes(file.toPath())
        val sty = com.example.data.parser.StyReader().read(data, file.name)
        
        var targetSection: com.example.domain.models.CasmSection? = null
        for (sec in sty.casm!!.sections) {
            if (sec.name.equals("Intro B", ignoreCase = true)) {
                targetSection = sec
                break
            }
        }
        
        if (targetSection == null) {
            println("Intro B not found")
            return
        }
        
        println("=== INTRO B ROUTING ===")
        for (ch in targetSection.channels) {
            val srcHex = String.format("%02X", ch.sourceChannel)
            val destHex = String.format("%02X", ch.destChannel)
            println("SRC:${srcHex} -> DEST:${destHex} (Dec:${ch.destChannel}) | NTR:${ch.ntr.displayName} | NTT:${ch.ntt.displayName}")
        }
        println("=======================")
    }
}
