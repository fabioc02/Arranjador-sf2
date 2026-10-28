package com.example.domain.playback

import com.example.domain.models.MidiEvent

class EventScheduler(private val sectionPlayer: SectionPlayer) {
    
    fun getEventsForTick(tick: Long): Map<TrackPlayer, List<MidiEvent>> {
        val eventsToProcess = mutableMapOf<TrackPlayer, List<MidiEvent>>()
        
        for (trackPlayer in sectionPlayer.trackPlayers.values) {
            val events = trackPlayer.getEventsForTick(tick)
            if (events.isNotEmpty()) {
                eventsToProcess[trackPlayer] = events
            }
        }
        
        return eventsToProcess
    }

    fun getNextEventTick(): Long? = sectionPlayer.peekNextEventTick()
}
