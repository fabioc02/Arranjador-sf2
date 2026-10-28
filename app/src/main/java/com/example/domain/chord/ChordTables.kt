package com.example.domain.chord

import com.example.domain.models.ChordType

object ChordTables {
    val chordIntervals = mapOf(
        ChordType.Major to listOf(0, 4, 7),
        ChordType.Minor to listOf(0, 3, 7),
        ChordType.Major7 to listOf(0, 4, 7, 11),
        ChordType.Minor7 to listOf(0, 3, 7, 10),
        ChordType.Dominant7 to listOf(0, 4, 7, 10),
        ChordType.Minor7b5 to listOf(0, 3, 6, 10),
        ChordType.Diminished to listOf(0, 3, 6),
        ChordType.Augmented to listOf(0, 4, 8),
        ChordType.Sus2 to listOf(0, 2, 7),
        ChordType.Sus4 to listOf(0, 5, 7),
        ChordType.Major6 to listOf(0, 4, 7, 9),
        ChordType.Major9 to listOf(0, 4, 7, 10, 14),
        ChordType.Major11 to listOf(0, 4, 7, 10, 14, 17),
        ChordType.Major13 to listOf(0, 4, 7, 10, 14, 17, 21),
        ChordType.Add9 to listOf(0, 4, 7, 14)
    )

    fun getIntervals(type: ChordType): List<Int> {
        return chordIntervals[type] ?: listOf(0, 4, 7)
    }
}
