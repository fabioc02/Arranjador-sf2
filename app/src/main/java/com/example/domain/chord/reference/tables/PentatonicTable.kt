package com.example.domain.chord.reference.tables

import com.example.domain.models.Chord

class PentatonicTable : VoiceLeadingTable {
    override fun mapNote(transposedNote: Int, originalNote: Int, sourceChord: Chord, targetChord: Chord): TableResult {
        val degree = (originalNote - sourceChord.rootKey + 12) % 12
        val pentatonicDegrees = listOf(0, 2, 4, 7, 9) // Major pentatonic
        
        var mappedNote = transposedNote
        val targetDegree = (mappedNote - targetChord.rootKey + 12) % 12
        
        if (targetDegree !in pentatonicDegrees) {
            mappedNote -= 1 // simplistic rounding
        }
        
        return TableResult(
            originalNote = originalNote,
            mappedNote = mappedNote,
            sourceDegree = degree,
            targetDegree = (mappedNote - targetChord.rootKey + 12) % 12,
            isChordTone = false,
            isPassingTone = true,
            confidence = ConfidenceLevel.HYPOTHESIS,
            documentation = "Applies strict Pentatonic scale mapping."
        )
    }
}
