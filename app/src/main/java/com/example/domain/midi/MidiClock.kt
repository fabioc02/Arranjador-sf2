package com.example.domain.midi

enum class ClockSource {
    Internal, External
}

class MidiClock {
    var source: ClockSource = ClockSource.Internal
    var isRunning = false
    var currentSongPosition: Int = 0 // in MIDI beats (sixteenth notes)

    fun start() { 
        isRunning = true 
        // Emits Start (FA)
    }
    fun stop() { 
        isRunning = false 
        // Emits Stop (FC)
    }
    fun continuePlay() {
        isRunning = true
        // Emits Continue (FB)
    }

    // Emits MIDI Clock (F8) events 24 times per quarter note
    fun emitClock() {
        if (isRunning && source == ClockSource.Internal) {
            // Send F8
        }
    }
    
    fun setSongPositionPointer(position: Int) {
        currentSongPosition = position
        // Emits SPP (F2)
    }
}
