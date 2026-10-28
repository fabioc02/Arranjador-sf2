package com.example.domain.chord.reference.tables

import com.example.domain.models.Chord

class DorianTable : VoiceLeadingTable {
    override fun mapNote(transposedNote: Int, originalNote: Int, sourceChord: Chord, targetChord: Chord): TableResult {
        val degree = (originalNote - sourceChord.rootKey + 12) % 12
        
        // HYPOTHESIS: Force notes to Dorian scale of the target root.
        // Dorian degrees: 0, 2, 3, 5, 7, 9, 10
        val targetDegree = (transposedNote - targetChord.rootKey + 12) % 12
        val dorianDegrees = listOf(0, 2, 3, 5, 7, 9, 10)
        
        var mappedNote = transposedNote
        if (targetDegree !in dorianDegrees) {
            // Find nearest dorian note (simplified)
            mappedNote -= 1 
        }

        return TableResult(
            originalNote = originalNote,
            mappedNote = mappedNote,
            sourceDegree = degree,
            targetDegree = (mappedNote - targetChord.rootKey + 12) % 12,
            isChordTone = false, // Not strictly chord tones
            isPassingTone = true,
            confidence = ConfidenceLevel.HYPOTHESIS,
            documentation = "Applies strict Dorian scale clamping."
        )
    }
}
