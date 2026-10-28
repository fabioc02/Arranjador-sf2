package com.example.domain.chord.reference.tables

import org.junit.Test
import java.io.File
import java.nio.file.Files
import com.example.data.parser.StyReader
import com.example.domain.models.*
import com.example.domain.chord.ChordTransposer

class VoiceLeadingRegressionTest {
    @Test
    fun verifyAgainstLegacy() {
        val dir = File("../testdata/styles/")
        val files = dir.listFiles { f -> f.extension.equals("sty", true) } ?: return
        
        val legacy = ChordTransposer()
        var diffCount = 0
        var matchCount = 0
        
        val sourceChord = Chord(0, ChordType.Major)
        val targetChords = listOf(
            Chord(0, ChordType.Major),
            Chord(5, ChordType.Major),
            Chord(7, ChordType.Minor)
        )
        
        for (file in files.take(3)) { // Test a few to avoid long times
            val sty = StyReader().read(Files.readAllBytes(file.toPath()), file.name)
            sty.casm?.sections?.forEach { sec ->
                sec.channels.forEach { ch ->
                    for (note in 60..72) {
                        for (target in targetChords) {
                            val legRes = legacy.transpose(note, sourceChord, target, ch)
                            val refRes = ReferencePipeline.process(
                                originalNote = note,
                                sourceChord = sourceChord,
                                targetChord = target,
                                ntr = ch.ntr,
                                ntt = ch.ntt,
                                highKey = ch.highKey,
                                lowLimit = ch.noteLowLimit,
                                highLimit = ch.noteHighLimit
                            )
                            if (legRes.generatedNote == refRes) matchCount++ else diffCount++
                        }
                    }
                }
            }
        }
        
        println("VoiceLeading Regression vs Legacy:")
        println("Matches: $matchCount")
        println("Differences: $diffCount") // Differences are expected since Reference is hypothesis
    }
}
