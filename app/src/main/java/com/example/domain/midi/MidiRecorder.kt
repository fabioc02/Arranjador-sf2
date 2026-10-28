package com.example.domain.midi

enum class MidiFileFormat {
    Format0, Format1
}

class MidiRecorder {
    var isRecording = false
        private set
    
    private val recordedEvents = mutableListOf<MidiMessage>()
    
    fun start() {
        isRecording = true
        recordedEvents.clear()
    }
    
    fun stop() {
        isRecording = false
    }
    
    fun recordEvent(event: MidiMessage) {
        if (isRecording) {
            recordedEvents.add(event)
        }
    }
    
    fun exportToMidiFile(path: String, format: MidiFileFormat = MidiFileFormat.Format0) {
        // To be implemented: Export standard MIDI file (SMF)
    }
}
