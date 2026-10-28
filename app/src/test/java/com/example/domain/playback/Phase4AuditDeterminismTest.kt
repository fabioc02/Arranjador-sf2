package com.example.domain.playback

import com.example.domain.chord.ChordEngine
import com.example.domain.models.*
import com.example.data.parser.StyParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class Phase4AuditDeterminismTest {
    @Test
    fun testDeterminism100Times() = runBlocking {
        val candidates = listOf(
            File("../testdata/github_styles/BregaBrasil.T161.sty"),
            File("testdata/github_styles/BregaBrasil.T161.sty"),
            File("../../testdata/github_styles/BregaBrasil.T161.sty"),
            File("../testdata/github_styles/AcusticoBR1.sty"),
            File("testdata/github_styles/AcusticoBR1.sty")
        )
        val file = candidates.find { it.exists() }
        assertTrue("Test file not found", file != null)
        val parser = StyParser()
        val sty = parser.parseBytes(file!!.readBytes(), file.name)
        assertTrue("Style parsing failed: ${sty.errorMessage}", sty.valid)
        val chordEngine = ChordEngine()
        
        // Use Main A and C Major
        val sectionData = sty.sections[StyleSection.MainA] ?: sty.sections.values.firstOrNull { it.exists }
        assertTrue("Section data not found", sectionData != null)
        val currentChord = Chord(0, ChordType.Major)
        
        var expectedHash: String? = null
        
        for (i in 0 until 100) {
            val sectionPlayer = SectionPlayer()
            sectionPlayer.playSection(StyleSection.MainA, sectionData!!)
            val scheduler = EventScheduler(sectionPlayer)
            val tickProcessor = TickProcessor(scheduler, chordEngine)
            
            // Advance for 100 ticks
            val sb = java.lang.StringBuilder()
            for (tick in 0L..100L) {
                val (events, _) = tickProcessor.processTick(tick, sty.casm, "Main A", currentChord)
                for (event in events) {
                    sb.append("${event.tick}|${event.eventType}|${event.channel}|${event.note}|${event.velocity}\n")
                }
            }
            
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(sb.toString().toByteArray())
            val currentHash = hashBytes.joinToString("") { "%02x".format(it) }
            
            if (expectedHash == null) {
                expectedHash = currentHash
            } else {
                assertEquals("Hash mismatch at iteration $i", expectedHash, currentHash)
            }
        }
    }
}
