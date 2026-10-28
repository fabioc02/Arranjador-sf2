package com.example.domain.models

data class ChannelInitState(
    var msb: Int = -1,
    var lsb: Int = -1,
    var pc: Int = -1,
    var volume: Int = -1,
    var pan: Int = -1,
    var reverb: Int = -1,
    var chorus: Int = -1
)

object StyleInitialization {
    fun extract(sty: StyFile): Map<Int, ChannelInitState> {
        val result = mutableMapOf<Int, ChannelInitState>()
        
        // Let's look at all raw events up to the first actual section (e.g. Intro/Main)
        // Since sections might be skipped or start later, let's just use all events from tick 0 to 1920 (first measure) or until the first section starts
        val firstSectionTick = sty.sections.values.map { it.startTick }.minOrNull() ?: 1920L
        
        sty.sections.values.forEach { sectionData ->
            // if this section starts at 0, it might be the SInt section which we want to parse
            if (sectionData.startTick <= firstSectionTick) {
                sectionData.tracks.forEach { (channel, track) ->
                    val state = result.getOrPut(channel) { ChannelInitState() }
                    track.events.forEach { event ->
                        when (event) {
                            is MidiEvent.ControlChange -> {
                                when (event.controller) {
                                    0 -> state.msb = event.value
                                    32 -> state.lsb = event.value
                                    7 -> state.volume = event.value
                                    10 -> state.pan = event.value
                                    91 -> state.reverb = event.value
                                    93 -> state.chorus = event.value
                                }
                            }
                            is MidiEvent.ProgramChange -> {
                                state.pc = event.program
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
        
        // Also let's scan raw tracks if there's any data not in a known section
        return result
    }
}
