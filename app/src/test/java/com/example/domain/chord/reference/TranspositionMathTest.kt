
package com.example.domain.chord.reference

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.models.*
import com.example.domain.chord.*
import java.io.File
import java.nio.file.Files

class TranspositionMathTest {
    val refEngine = CasmTranspositionEngine()

    private fun casmCh(ntr: NTR, ntt: NTT, hk: Int = 127, low: Int = 0, high: Int = 127): CasmChannel {
        return CasmChannel(ntr = ntr, ntt = ntt, highKey = hk, noteLowLimit = low, noteHighLimit = high)
    }

    @Test
    fun testNtrRootFixedMatrix() {
        println("--- TEST 1: NTR ROOT FIXED MATRIX ---")
        val roots = listOf(0, 2, 4, 5, 7, 9, 11) // C, D, E, F, G, A, B
        for (r in roots) {
            val res = refEngine.transpose(60, 1, 1, NTR.ROOT_FIXED, NTT.CHORD, 0, r, ChordType.Major, 127, 0, 127)
            println("Root: $r | Orig: 60 | NTR Shifted: ${res.ntrShiftedNote}")
        }
    }

    @Test
    fun testNtt00Bypass() {
        println("--- TEST 2: NTT 00 BYPASS ---")
        for (note in 36..96 step 12) {
            val res = refEngine.transpose(note, 1, 1, NTR.ROOT_FIXED, NTT.BYPASS, 0, 5, ChordType.Major, 127, 0, 127)
            assertEquals("Bypass should not alter note", note, res.finalNote)
            println("Orig: $note | Bypass Result: ${res.finalNote}")
        }
    }

    @Test
    fun testNtt02ChordMajorMatrix() {
        println("--- TEST 3: NTT 02 CHORD MAJOR MATRIX ---")
        val oldEngine = ChordTransposer()
        val notes = listOf(60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71) // C to B
        val roots = listOf(0, 5, 7) // C, F, G

        for (r in roots) {
            println("Target Root: $r (Major)")
            for (n in notes) {
                val ref = refEngine.transpose(n, 1, 1, NTR.ROOT_FIXED, NTT.CHORD, 0, r, ChordType.Major, 127, 0, 127)
                val old = oldEngine.transpose(n, Chord(0, ChordType.Major), Chord(r, ChordType.Major), casmCh(NTR.ROOT_FIXED, NTT.CHORD))
                println("  Orig: $n | Ref: ${ref.finalNote} | Old(VoiceLeading): ${old.generatedNote}")
            }
        }
    }

    @Test
    fun testNtt02ChordMinorMatrix() {
        println("--- TEST 4: NTT 02 CHORD MINOR MATRIX ---")
        val oldEngine = ChordTransposer()
        val notes = listOf(60, 62, 64, 65, 67, 69, 71)
        val roots = listOf(0, 9) // Cm, Am

        for (r in roots) {
            println("Target Root: $r (Minor)")
            for (n in notes) {
                val ref = refEngine.transpose(n, 1, 1, NTR.ROOT_FIXED, NTT.CHORD, 0, r, ChordType.Minor, 127, 0, 127)
                val old = oldEngine.transpose(n, Chord(0, ChordType.Major), Chord(r, ChordType.Minor), casmCh(NTR.ROOT_FIXED, NTT.CHORD))
                println("  Orig: $n | Ref: ${ref.finalNote} | Old(VoiceLeading): ${old.generatedNote}")
            }
        }
    }

    @Test
    fun testNtt08EmpiricalMatrix() {
        println("--- TEST 5: NTT 08 EMPIRICAL ---")
        val dir = File("../testdata/styles/")
        val files = dir.listFiles { f -> f.extension == "sty" || f.extension == "STY" } ?: emptyArray()
        for (file in files) {
            try {
                val sty = com.example.data.parser.StyReader().read(Files.readAllBytes(file.toPath()), file.name)
                sty.casm?.sections?.forEach { sec ->
                    sec.channels.filter { it.ntt == NTT.DORIAN }.forEach { ch ->
                        println("${file.name} | Sec:${sec.name} | Src:${ch.sourceChannel} -> Dest:${ch.destChannel} | NTR:${ch.ntr.displayName} | HK:${ch.highKey} | L:${ch.noteLowLimit} | H:${ch.noteHighLimit}")
                    }
                }
            } catch(e:Exception){}
        }
    }

    @Test
    fun testNtt0aPentatonicOccurrences() {
        println("--- TEST 6: NTT 0A PENTATONIC ---")
        val dir = File("../testdata/styles/")
        val files = dir.listFiles { f -> f.extension == "sty" || f.extension == "STY" } ?: emptyArray()
        for (file in files) {
            try {
                val sty = com.example.data.parser.StyReader().read(Files.readAllBytes(file.toPath()), file.name)
                sty.casm?.sections?.forEach { sec ->
                    sec.channels.filter { it.ntt == NTT.PENTATONIC }.forEach { ch ->
                        println("${file.name} | Sec:${sec.name} | Src:${ch.sourceChannel} -> Dest:${ch.destChannel} | HK:${ch.highKey} | L:${ch.noteLowLimit} | H:${ch.noteHighLimit}")
                    }
                }
            } catch(e:Exception){}
        }
    }

    @Test
    fun testNoteLimitsAndHighKey() {
        println("--- TEST 7: NOTE LIMITS & HIGH KEY ---")
        val n = 60
        // Test High Key
        val resHk = refEngine.transpose(n, 1, 1, NTR.ROOT_FIXED, NTT.CHORD, 0, 9, ChordType.Major, highKey = 7, noteLimitLow=0, noteLimitHigh=127)
        println("HighKey 7, Root 9 -> HK Adjusted? ${resHk.adjustedByHighKey} | Note: ${resHk.finalNote}")

        // Test Limits
        val resHigh = refEngine.transpose(80, 1, 1, NTR.ROOT_FIXED, NTT.CHORD, 0, 0, ChordType.Major, 127, 0, 72)
        println("Note 80, LimitHigh 72 -> Limited? ${resHigh.limitedByNoteLimit} | Note: ${resHigh.finalNote}")
        
        val resLow = refEngine.transpose(30, 1, 1, NTR.ROOT_FIXED, NTT.CHORD, 0, 0, ChordType.Major, 127, 40, 127)
        println("Note 30, LimitLow 40 -> Limited? ${resLow.limitedByNoteLimit} | Note: ${resLow.finalNote}")
    }
}
