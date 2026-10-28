package com.example.domain.validation

import com.example.domain.models.StyFile
import com.example.domain.models.Chord
import com.example.domain.models.StyleSection
import com.example.domain.playback.StylePlaybackEngine
import com.example.domain.playback.GeneratedMidiEvent
import com.example.domain.chord.ChordEngine

class StyleReplayEngine(
    private val chordEngine: ChordEngine
) {
    fun replay(
        style: StyFile, 
        section: StyleSection, 
        chords: List<Pair<Long, Chord>>, // Tick to change chord, Chord
        maxTicks: Long
    ): List<GeneratedMidiEvent> {
        val playbackEngine = StylePlaybackEngine(style, chordEngine)
        val generatedEvents = mutableListOf<GeneratedMidiEvent>()
        
        playbackEngine.playSection(section)
        
        var chordIndex = 0
        
        for (tick in 0 until maxTicks) {
            // Check if we need to change chord
            if (chordIndex < chords.size && tick >= chords[chordIndex].first) {
                playbackEngine.currentChord = chords[chordIndex].second
                chordIndex++
            }
            
            val (events, _) = playbackEngine.processNextTick()
            generatedEvents.addAll(events)
        }
        
        return generatedEvents
    }
}
