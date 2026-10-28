package com.example.domain.validation

import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.StyFile
import com.example.domain.models.StyleSection

class ChordPlaybackSimulator(
    private val replayEngine: StyleReplayEngine
) {
    fun simulateAllChords(style: StyFile, section: StyleSection): Int {
        val roots = 0..11
        val types = listOf(
            ChordType.Major, ChordType.Minor, ChordType.Dominant7, 
            ChordType.Minor7, ChordType.Major7, ChordType.Sus4, 
            ChordType.Diminished, ChordType.Augmented
        )
        
        var totalEventsGenerated = 0
        
        for (root in roots) {
            for (type in types) {
                val chord = Chord(root, type)
                val events = replayEngine.replay(
                    style = style,
                    section = section,
                    chords = listOf(Pair(0L, chord)),
                    maxTicks = 1920L // One bar at 4/4 480 PPQ
                )
                totalEventsGenerated += events.size
            }
        }
        
        return totalEventsGenerated
    }
}
