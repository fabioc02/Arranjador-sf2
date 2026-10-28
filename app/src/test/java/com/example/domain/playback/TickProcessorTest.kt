package com.example.domain.playback

import com.example.domain.chord.ChordEngine
import com.example.domain.models.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TickProcessorTest {

    private fun loadSty(filename: String): StyFile {
        val candidates = listOf(
            File("../testdata/github_styles/$filename"),
            File("testdata/github_styles/$filename"),
            File("../../testdata/github_styles/$filename"),
            File("../testdata/styles/$filename"),
            File("testdata/styles/$filename")
        )
        val file = candidates.find { it.exists() }
        assertTrue("Test file not found: $filename", file != null)
        val data = Files.readAllBytes(file!!.toPath())
        val reader = com.example.data.parser.StyReader()
        val sty = reader.read(data, file.name)
        assertTrue("Parsing failed for $filename: ${sty.errorMessage}", sty.valid)
        return sty
    }

    @Test
    fun testIntroAuxBassRouting() {
        val sty = loadSty("BregaBrasil.T161.sty")
        assertEquals("O PPQ deve ser estritamente 1920", 1920, sty.ppq)
        val chordEngine = ChordEngine()
        val engine = StylePlaybackEngine(sty, chordEngine)
        
        val introSection = StyleSection.IntroB
        assertTrue("Intro B section not found", sty.hasSection(introSection))
        engine.playSection(introSection)
        
        var foundAuxBassEvent = false
        var foundBypassBassEvent = false
        
        // Em 1920 PPQ, 1 compasso 4/4 = 7680 ticks. Testamos até 2 compassos (15360 ticks).
        val maxTicksToTest = 15360
        for (i in 0..maxTicksToTest) { 
            val (events, _) = engine.processNextTick()
            for (event in events) {
                // Assert CASM routing rule Dest 0A (Channel 10)
                if (event.channel == 0x0A || event.channel == 10 || event.channel == 11) { 
                    if (event.nttApplied == NTT.MELODY.displayName || event.nttApplied == NTT.HARMONIC_MINOR.displayName || event.nttApplied == NTT.DORIAN.displayName) {
                        foundAuxBassEvent = true
                    } else if (event.nttApplied == NTT.BYPASS.displayName) {
                        foundBypassBassEvent = true
                    }
                }
            }
            if (foundAuxBassEvent) break
        }
        
        assertTrue("Expected to route Aux (NTT 08/Dorian) to Dest 0A at 1920 PPQ", foundAuxBassEvent)
    }
}
