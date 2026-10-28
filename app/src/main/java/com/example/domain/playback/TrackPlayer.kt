package com.example.domain.playback

import com.example.domain.models.MidiEvent
import com.example.domain.models.StyleTrack

class TrackPlayer(
    val name: String,
    val casmChannelIndex: Int
) {
    private var events: List<MidiEvent> = emptyList()
    private var currentIndex: Int = 0
    private var isPlaying: Boolean = false
    private var sectionStartTick: Long = 0
    private var sectionEndTick: Long = 0

    private var initialSetupEvents: List<MidiEvent> = emptyList()

    private var isRelativeTicks: Boolean = true

    fun loadTrack(track: StyleTrack, startTick: Long, endTick: Long) {
        // Collect setup events before startTick (ProgramChange, CC for Bank/Volume/Pan/Reverb/Chorus)
        val setupEvents = mutableListOf<MidiEvent>()
        var lastPC: MidiEvent? = null
        var lastBankMsb: MidiEvent? = null
        var lastBankLsb: MidiEvent? = null
        var lastVolume: MidiEvent? = null
        var lastPan: MidiEvent? = null
        var lastReverb: MidiEvent? = null
        var lastChorus: MidiEvent? = null

        val hasAbsoluteTicks = startTick > 0 && track.events.any { it.tick >= startTick }
        isRelativeTicks = !hasAbsoluteTicks

        if (hasAbsoluteTicks) {
            for (event in track.events) {
                if (event.tick >= startTick) break
                if (event is com.example.domain.models.MidiEvent.ProgramChange) lastPC = event.copy(tick = startTick)
                if (event is com.example.domain.models.MidiEvent.ControlChange) {
                    when (event.controller) {
                        0 -> lastBankMsb = event.copy(tick = startTick)
                        32 -> lastBankLsb = event.copy(tick = startTick)
                        7 -> lastVolume = event.copy(tick = startTick)
                        10 -> lastPan = event.copy(tick = startTick)
                        91 -> lastReverb = event.copy(tick = startTick)
                        93 -> lastChorus = event.copy(tick = startTick)
                    }
                }
            }
            this.events = track.events.filter { it.tick in startTick until endTick }.sortedBy { it.tick }
        } else {
            this.events = track.events.sortedBy { it.tick }
        }
        
        lastBankMsb?.let { setupEvents.add(it) }
        lastBankLsb?.let { setupEvents.add(it) }
        lastPC?.let { setupEvents.add(it) }
        lastVolume?.let { setupEvents.add(it) }
        lastPan?.let { setupEvents.add(it) }
        lastReverb?.let { setupEvents.add(it) }
        lastChorus?.let { setupEvents.add(it) }

        this.initialSetupEvents = setupEvents
        this.sectionStartTick = startTick
        this.sectionEndTick = endTick
        this.currentIndex = 0
        this.isPlaying = true
    }

    fun reset() {
        currentIndex = 0
        isPlaying = true
    }

    fun stop() {
        isPlaying = false
        events = emptyList()
        currentIndex = 0
    }

    fun getEventsForTick(currentTick: Long): List<MidiEvent> {
        if (!isPlaying) return emptyList()
        
        // The current tick from the clock is relative to the start of the section
        val targetTick = if (isRelativeTicks) currentTick else (sectionStartTick + currentTick)
        
        val tickEvents = mutableListOf<MidiEvent>()
        
        if (currentTick == 0L) {
            tickEvents.addAll(initialSetupEvents)
        }
        
        while (currentIndex < events.size && events[currentIndex].tick <= targetTick) {
            tickEvents.add(events[currentIndex])
            currentIndex++
        }
        
        return tickEvents
    }

    fun peekNextEventTick(): Long? {
        if (!isPlaying || currentIndex >= events.size) return null
        val nextTick = events[currentIndex].tick
        return if (isRelativeTicks) nextTick else (nextTick - sectionStartTick)
    }

    fun hasFinished(): Boolean {
        return currentIndex >= events.size
    }
    
    fun getPendingEventsCount(): Int {
        return Math.max(0, events.size - currentIndex)
    }
}
