
package com.example.domain.playback

import com.example.domain.chord.ChordEngine
import com.example.domain.models.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CasmLogTest {
    @Test
    fun testLog() {
        val file = File("../testdata/styles/BregaCalypso.sty")
        val data = Files.readAllBytes(file.toPath())
        val sty = com.example.data.parser.StyReader().read(data, file.name)
        val engine = StylePlaybackEngine(sty, ChordEngine())
        
        engine.playSection(StyleSection.IntroB)
        
        println("--- REAL PLAYBACK LOG START ---")
        for (i in 0..1920) { 
            val (events, _) = engine.processNextTick()
            for (event in events) {
                // The event.originalChannel would be useful, but GeneratedMidiEvent might not have it.
                // We know from the CASM that Dest 0A comes from Src 02, Dest 0B from Src 03.
                println("Log: Tick=${i} | DestChannel=${event.channel} | Note=${event.note} | NTT=${event.nttApplied}")
            }
        }
        println("--- REAL PLAYBACK LOG END ---")
    }
}
