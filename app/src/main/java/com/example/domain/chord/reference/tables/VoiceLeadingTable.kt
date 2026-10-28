package com.example.domain.chord.reference.tables

import com.example.domain.models.Chord

enum class ConfidenceLevel {
    PROVEN, STRONGLY_SUPPORTED, HYPOTHESIS, UNKNOWN
}

data class TableResult(
    val originalNote: Int,
    val mappedNote: Int,
    val sourceDegree: Int,
    val targetDegree: Int,
    val isChordTone: Boolean,
    val isPassingTone: Boolean,
    val confidence: ConfidenceLevel,
    val documentation: String = ""
)

interface VoiceLeadingTable {
    fun mapNote(transposedNote: Int, originalNote: Int, sourceChord: Chord, targetChord: Chord): TableResult
}
