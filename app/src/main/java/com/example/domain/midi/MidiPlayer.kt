package com.example.domain.midi

class MidiPlayer(private val eventQueue: MidiEventQueue) {
    var isPlaying = false
    
    fun loadMidiFile(path: String) {
        // To be implemented: Parse SMF format 0/1
    }
    
    fun play() {
        isPlaying = true
    }
    
    fun pause() {
        isPlaying = false
    }
    
    fun stop() {
        isPlaying = false
    }
    
    // Called periodically to push events to the same event queue
    fun processPlayback() {
        if (!isPlaying) return
        
        // Push parsed MIDI events to eventQueue
        // eventQueue.offer(MidiMessage(...))
    }
}
