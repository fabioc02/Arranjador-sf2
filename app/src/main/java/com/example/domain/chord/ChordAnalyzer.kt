package com.example.domain.chord

import com.example.domain.models.Chord
import com.example.domain.models.ChordType

class ChordAnalyzer {

    // Helper structure to map intervals to chord types
    private val intervalMaps = mapOf(
        setOf(0, 4, 7) to ChordType.Major,
        setOf(0, 3, 7) to ChordType.Minor,
        setOf(0, 4, 7, 10) to ChordType.Dominant7,
        setOf(0, 4, 7, 11) to ChordType.Major7,
        setOf(0, 3, 7, 10) to ChordType.Minor7,
        setOf(0, 3, 6) to ChordType.Diminished,
        setOf(0, 3, 6, 9) to ChordType.Diminished, // full diminished 7th is often just "dim"
        setOf(0, 4, 8) to ChordType.Augmented,
        setOf(0, 2, 7) to ChordType.Sus2,
        setOf(0, 5, 7) to ChordType.Sus4,
        setOf(0, 3, 6, 10) to ChordType.Minor7b5
    )

    fun detectChord(notes: List<Int>): Chord? {
        if (notes.isEmpty()) return null

        // Deduplicate and normalize to 0-11
        val uniquePitchClasses = notes.map { it % 12 }.distinct()
        
        if (uniquePitchClasses.size < 2) {
            // Can't reliably detect a chord from a single note, but let's default to Major for arranger purposes
            val singleNote = uniquePitchClasses.first()
            return Chord(singleNote, ChordType.Major, notes.minOrNull()!! % 12)
        }

        val bass = notes.minOrNull()!! % 12

        // Try every possible root note from the pressed keys
        for (rootCandidate in uniquePitchClasses) {
            // Calculate intervals relative to this root candidate
            val intervals = uniquePitchClasses.map { (it - rootCandidate + 12) % 12 }.toSet()
            
            // Check if these intervals form a known chord shape
            val matchedType = intervalMaps[intervals]
            if (matchedType != null) {
                return Chord(rootCandidate, matchedType, bass)
            }
            
            // Allow simplified matching for power chords (root + fifth) -> assume Major
            if (intervals == setOf(0, 7)) {
                return Chord(rootCandidate, ChordType.Major, bass)
            }
        }

        // If no strict match, fallback to heuristics based on the bass note as root
        val root = bass
        val type = if (uniquePitchClasses.contains((root + 3) % 12)) ChordType.Minor else ChordType.Major
        return Chord(root, type, bass)
    }
}
