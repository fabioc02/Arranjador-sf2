package com.example.domain.chord.engine

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.models.*
import com.example.domain.chord.*
import java.io.File
import java.nio.file.Files

class TranspositionEngineIntegrationTest {
    @Test
    fun testLegacyVsReference() {
        val legacy = LegacyTranspositionEngine(ChordTransposer())
        val reference = ReferenceTranspositionEngine()
        
        val dir = File("../testdata/styles/")
        val files = dir.listFiles { f -> f.extension.equals("sty", true) } ?: return

        var diffs = 0
        var matches = 0

        val notesToTest = listOf(48, 60, 72)
        val rootsToTest = listOf(0, 5, 7) // C, F, G

        for (file in files.take(5)) {
            val sty = try {
                com.example.data.parser.StyReader().read(Files.readAllBytes(file.toPath()), file.name)
            } catch (e: Exception) { continue }
            
            sty.casm?.sections?.forEach { sec ->
                sec.channels.take(3).forEach { ch ->
                    for (note in notesToTest) {
                        for (root in rootsToTest) {
                            val targetChord = Chord(root, ChordType.Major)
                            val sourceChord = Chord(0, ChordType.Major)
                            
                            val legRes = legacy.transpose(note, ch.sourceChannel, ch.destChannel, ch.ntr, ch.ntt, sourceChord, targetChord, ch.highKey, ch.noteLowLimit, ch.noteHighLimit)
                            val refRes = reference.transpose(note, ch.sourceChannel, ch.destChannel, ch.ntr, ch.ntt, sourceChord, targetChord, ch.highKey, ch.noteLowLimit, ch.noteHighLimit)

                            if (legRes.generatedNote != refRes.generatedNote) {
                                diffs++
                                println("DIFF | ${file.name} | Sec:${sec.name} | Source:${ch.sourceChannel} -> Dest:${ch.destChannel} | Note:$note | Legacy:${legRes.generatedNote} | Reference:${refRes.generatedNote} | NTR:${ch.ntr} | NTT:${ch.ntt} | HighKey:${ch.highKey}")
                            } else {
                                matches++
                            }
                        }
                    }
                }
            }
        }
        println("Integration Test: $matches MATCHES, $diffs DIFFERENCES")
    }
}
