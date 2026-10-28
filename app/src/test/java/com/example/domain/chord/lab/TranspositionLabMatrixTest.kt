package com.example.domain.chord.lab

import org.junit.Test
import com.example.domain.models.*
import com.example.domain.chord.*
import java.io.File

class TranspositionLabMatrixTest {
    @Test
    fun generateMatrices() {
        val legacy = ChordTransposer()
        val out = File("lab_matrices.md").bufferedWriter()
        
        out.write("| Original Note | Source Root | Target Root | Chord Type | NTR | NTT | Legacy Result | Lab Result (Hypothesis) | Rule |\n")
        out.write("|---|---|---|---|---|---|---|---|---|\n")
        
        val testNotes = listOf(60, 62, 64, 65, 67) // C4, D4, E4, F4, G4
        val targetChords = listOf(
            Chord(0, ChordType.Major), // C Maj
            Chord(5, ChordType.Major), // F Maj
            Chord(7, ChordType.Major), // G Maj
            Chord(0, ChordType.Minor), // C Min
            Chord(0, ChordType.Dominant7) // C7
        )
        val sourceChord = Chord(0, ChordType.Major)
        
        // NTT 02 (Chord)
        for (target in targetChords) {
            for (note in testNotes) {
                val casmChannel = CasmChannel(0, 0, "C", "Maj", NTR.ROOT_FIXED, NTT.CHORD, 127, 0, 0, 127)
                val legRes = legacy.transpose(note, sourceChord, target, casmChannel)
                val labRes = TranspositionLab.experiment(note, sourceChord, target, NTR.ROOT_FIXED, NTT.CHORD, 127, 0, 127)
                
                out.write("| $note | 0 | ${target.rootKey} | ${target.type.name} | ROOT_FIXED | CHORD | ${legRes.generatedNote} | ${labRes.finalNote} | CHORD |\n")
            }
        }

        // NTT 08 (Dorian)
        for (target in targetChords) {
            for (note in testNotes) {
                val casmChannel = CasmChannel(0, 0, "C", "Maj", NTR.ROOT_FIXED, NTT.DORIAN, 127, 0, 0, 127)
                val legRes = legacy.transpose(note, sourceChord, target, casmChannel)
                val labRes = TranspositionLab.experiment(note, sourceChord, target, NTR.ROOT_FIXED, NTT.DORIAN, 127, 0, 127)
                
                out.write("| $note | 0 | ${target.rootKey} | ${target.type.name} | ROOT_FIXED | DORIAN | ${legRes.generatedNote} | ${labRes.finalNote} | DORIAN |\n")
            }
        }
        
        out.close()
    }
}
