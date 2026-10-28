package com.example.domain.midi

interface MidiEventReceiver {
    fun onNoteOn(channel: Int, note: Int, velocity: Int)
    fun onNoteOff(channel: Int, note: Int, velocity: Int)
    fun onControlChange(channel: Int, controller: Int, value: Int)
}

interface MidiInputSource {
    fun start()
    fun stop()
    fun setReceiver(receiver: MidiEventReceiver)
}

interface MidiInputProvider {
    fun getAvailableSources(): List<MidiInputSource>
}
