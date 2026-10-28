package com.example.domain.chord

import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.NTR
import com.example.domain.models.NTT
import com.example.domain.chord.engine.ReferenceTranspositionEngine
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CasmForensicAuditTest {

    private val engine = ReferenceTranspositionEngine()

    @Test
    fun runAuditForChords() {
        val originalNote = 36 // C2
        val sourceChord = Chord(0, ChordType.Major7) // CMaj7

        val chordsToTest = listOf(
            "C" to Chord(0, ChordType.Major),
            "G" to Chord(7, ChordType.Major),
            "F" to Chord(5, ChordType.Major),
            "Am" to Chord(9, ChordType.Minor),
            "Dm" to Chord(2, ChordType.Minor),
            "C/E" to Chord(0, ChordType.Major, bassKey = 4),
            "C/G" to Chord(0, ChordType.Major, bassKey = 7),
            "G/B" to Chord(7, ChordType.Major, bassKey = 11),
            "G/D" to Chord(7, ChordType.Major, bassKey = 2),
            "F/A" to Chord(5, ChordType.Major, bassKey = 9),
            "Am/E" to Chord(9, ChordType.Minor, bassKey = 4),
            "Dm/F" to Chord(2, ChordType.Minor, bassKey = 5)
        )

        println("==================================================")
        println(" KOTLIN ENGINE AUDIT - PopReggaeBR Bass Track")
        println(" Original Note: C2 (MIDI 36)")
        println(" CASM: DestCh=10, NTR=0(ROOT_FIXED), NTT=2(CHORD), HighKey=0, LowLim=0, HighLim=95")
        println("==================================================")
        
        println(String.format("%-6s | %-12s | %-12s | %-5s", "Chord", "Base Shift", "After HighKey", "Final"))
        println("--------------------------------------------------")

        for ((name, targetChord) in chordsToTest) {
            val result = engine.transpose(
                originalNote = originalNote,
                sourceChannel = 10,
                destChannel = 10,
                ntr = NTR.ROOT_FIXED,
                ntt = NTT.CHORD,
                sourceChord = sourceChord,
                targetChord = targetChord,
                highKey = 0,
                noteLimitLow = 0,
                noteLimitHigh = 95
            )

            // Let's manually calculate to show in output
            val baseShift = originalNote + (targetChord.bassKey - sourceChord.rootKey)
            val nttApplied = baseShift // NTT=CHORD, C is root, target is root/bass... wait, NTT Chord shifts to chord tone.
            val effHk = 0
            val drop = if (targetChord.bassKey > effHk) 12 else 0
            val afterHk = nttApplied - drop

            println(String.format("%-6s | %-12s | %-12s | %-5d", name, "$baseShift (Midi)", "$afterHk", result.generatedNote))
        }
    }
}
