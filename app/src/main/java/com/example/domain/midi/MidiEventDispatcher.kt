package com.example.domain.midi

class MidiEventDispatcher(private val queue: MidiEventQueue, private val router: MidiRouter) {
    var isRunning = false
    
    // In a real scenario, this would run on a dedicated high-priority thread
    fun processEvents() {
        if (!isRunning) return
        var msg = queue.poll()
        while (msg != null) {
            router.routeIncomingMidi(msg)
            msg = queue.poll()
        }
    }
}
