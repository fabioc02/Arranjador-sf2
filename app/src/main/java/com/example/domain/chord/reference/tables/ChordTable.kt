package com.example.domain.chord.reference.tables

import com.example.domain.models.Chord
import com.example.domain.models.ChordType

/**
 * Yamaha NTT 02 (Chord)
 * HYPOTHESIS: Maps notes to their corresponding scale degree in the target chord.
 * Chord tones map to chord tones. Passing tones map to passing tones.
 */
class ChordTable : VoiceLeadingTable {
    
    override fun mapNote(transposedNote: Int, originalNote: Int, sourceChord: Chord, targetChord: Chord): TableResult {
        // Simplified mapping hypothesis
        val degree = (originalNote - sourceChord.rootKey + 12) % 12
        val isChordTone = isChordTone(degree, sourceChord.type)
        
        // For now, if we don't have the explicit table, we keep the shifted note
        // but clamp it to the target scale if it violates chord structure.
        // This is a placeholder HYPOTHESIS.
        val mappedNote = transposedNote // In Root Fixed, NTR already shifted it.
        
        return TableResult(
            originalNote = originalNote,
            mappedNote = mappedNote,
            sourceDegree = degree,
            targetDegree = (mappedNote - targetChord.rootKey + 12) % 12,
            isChordTone = isChordTone,
            isPassingTone = !isChordTone,
            confidence = ConfidenceLevel.HYPOTHESIS,
            documentation = "Pending explicit degree-to-degree mapping validation."
        )
    }

    private fun isChordTone(degree: Int, type: ChordType): Boolean {
        return when (type) {
            ChordType.Major -> degree in listOf(0, 4, 7)
            ChordType.Minor -> degree in listOf(0, 3, 7)
            ChordType.Dominant7 -> degree in listOf(0, 4, 7, 10)
            ChordType.Major7 -> degree in listOf(0, 4, 7, 11)
            ChordType.Minor7 -> degree in listOf(0, 3, 7, 10)
            else -> degree == 0 // simplify for lab
        }
    }
}
