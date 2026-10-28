package com.example.domain.playback

import com.example.domain.models.SectionData
import com.example.domain.models.StyleSection

class SectionPlayer {
    var currentSection: StyleSection = StyleSection.Unknown
        private set
        
    var sectionData: SectionData? = null
        private set

    val trackPlayers = mutableMapOf<Int, TrackPlayer>()
    
    init {
        // Init typical track players (e.g. 0-15)
        for (i in 0..15) {
            trackPlayers[i] = TrackPlayer("Track $i", i)
        }
    }

    fun playSection(section: StyleSection, data: SectionData) {
        currentSection = section
        sectionData = data
        
        // Setup all track players
        for (i in 0..15) {
            val track = data.tracks[i]
            if (track != null) {
                trackPlayers[i]?.loadTrack(track, data.startTick, data.endTick)
            } else {
                trackPlayers[i]?.stop()
            }
        }
    }

    fun reset() {
        trackPlayers.values.forEach { it.reset() }
    }
    
    fun getPendingEventsCount(): Int {
        return trackPlayers.values.sumOf { it.getPendingEventsCount() }
    }

    fun peekNextEventTick(): Long? {
        var minTick: Long? = null
        for (tp in trackPlayers.values) {
            val t = tp.peekNextEventTick() ?: continue
            if (minTick == null || t < minTick) {
                minTick = t
            }
        }
        return minTick
    }
    
    fun getActiveTracksCount(): Int {
        return trackPlayers.values.count { !it.hasFinished() }
    }
}
