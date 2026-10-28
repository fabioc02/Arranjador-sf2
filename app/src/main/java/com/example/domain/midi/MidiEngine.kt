package com.example.domain.midi

import com.example.domain.performance.PerformanceEngine

enum class PlaybackMode {
    StrictYamaha, Enhanced, Studio
}

class MidiEngine(val performanceEngine: PerformanceEngine) {
    val eventQueue = MidiEventQueue(2048)
    val monitor = MidiMonitor()
    val outputManager = MidiOutputManager(eventQueue)
    val router = MidiRouter(performanceEngine, monitor, outputManager = outputManager)
    val dispatcher = MidiEventDispatcher(eventQueue, router)
    val inputManager = MidiInputManager(eventQueue)
    val clock = MidiClock()
    val recorder = MidiRecorder()
    val player = MidiPlayer(eventQueue)
    val midiLearn = MidiLearn()
    
    var playbackMode: PlaybackMode = PlaybackMode.StrictYamaha

    fun start() {
        dispatcher.isRunning = true
    }

    fun stop() {
        dispatcher.isRunning = false
    }
}
