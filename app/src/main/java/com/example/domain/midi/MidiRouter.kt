package com.example.domain.midi

import com.example.domain.performance.PerformanceEngine
import com.example.domain.playback.GeneratedMidiEvent

class MidiRouter(
    private val performanceEngine: PerformanceEngine,
    val monitor: MidiMonitor = MidiMonitor(),
    val midiLearn: MidiLearn = MidiLearn(),
    val recorder: MidiRecorder = MidiRecorder(),
    val outputManager: MidiOutputManager = MidiOutputManager()
) {
    fun routeIncomingMidi(message: MidiMessage) {
        monitor.logIncoming(message)
        recorder.recordEvent(message)
        
        if (midiLearn.processIncoming(message)) {
            return // Consumed by MIDI Learn
        }
        
        when (message.type) {
            MidiMessageType.NoteOn -> {
                performanceEngine.processMidiNoteOn(message.data1, message.data2)
            }
            MidiMessageType.NoteOff -> {
                performanceEngine.processMidiNoteOff(message.data1)
            }
            else -> {
                // Future handling of CC, PitchBend, etc.
            }
        }
    }
    
    fun routeGeneratedMidi(events: List<GeneratedMidiEvent>) {
        events.forEach { event ->
            monitor.logGenerated(event)
        }
        outputManager.sendGeneratedEvents(events)
    }
}
