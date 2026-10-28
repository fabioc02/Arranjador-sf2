package com.example.domain.chord

object VoiceLeading {
    fun findClosestChordTone(rootKey: Int, intervals: List<Int>, previousNote: Int): Int {
        var closest = -1
        var minDiff = 127
        
        // Priority: Shortest movement.
        // We evaluate notes in a reasonable range around the previous note (e.g. -12 to +12)
        // to avoid large jumps unless necessary.
        
        for (octave in -1..8) {
            val base = octave * 12 + rootKey
            for (i in intervals.indices) {
                val note = base + intervals[i]
                if (note in 0..127) {
                    val diff = Math.abs(note - previousNote)
                    // Prefer shortest movement; on equal distance, resolve upward to maintain separation
                    if (diff < minDiff || (diff == minDiff && note > closest)) {
                        minDiff = diff
                        closest = note
                    }
                }
            }
        }
        
        // Prevent jumps > 7 semitones if possible, though findClosestChordTone inherently minimizes `minDiff`.
        return if (closest != -1) closest else previousNote
    }
}
