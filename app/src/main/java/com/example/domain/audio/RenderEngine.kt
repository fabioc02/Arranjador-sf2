package com.example.domain.audio

import com.example.domain.playback.GeneratedMidiEvent

class RenderEngine(
    private val synthBackend: SynthBackend? = null
) {
    fun processEvent(event: GeneratedMidiEvent) {
        if (synthBackend == null) return
        
        when (event.eventType) {
            "NoteOn" -> synthBackend.noteOn(event.channel, event.note, event.velocity)
            "NoteOff" -> synthBackend.noteOff(event.channel, event.note)
            "ControlChange" -> synthBackend.controlChange(event.channel, event.ccController, event.ccValue)
            "ProgramChange" -> synthBackend.programChange(event.channel, event.program)
            "PitchBend" -> synthBackend.pitchBend(event.channel, event.velocity) // using velocity as placeholder for PB value
        }
    }
}
