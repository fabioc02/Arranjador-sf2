package com.example.domain.midi

class MidiInputManager(private val eventQueue: MidiEventQueue) : MidiEventReceiver {
    private val sources = mutableListOf<MidiInputSource>()

    fun addSource(source: MidiInputSource) {
        sources.add(source)
        source.setReceiver(this)
    }

    fun removeSource(source: MidiInputSource) {
        sources.remove(source)
    }

    override fun onNoteOn(channel: Int, note: Int, velocity: Int) {
        eventQueue.offer(MidiMessage(System.nanoTime(), MidiMessageType.NoteOn, channel, note, velocity))
    }

    override fun onNoteOff(channel: Int, note: Int, velocity: Int) {
        eventQueue.offer(MidiMessage(System.nanoTime(), MidiMessageType.NoteOff, channel, note, velocity))
    }

    override fun onControlChange(channel: Int, controller: Int, value: Int) {
        eventQueue.offer(MidiMessage(System.nanoTime(), MidiMessageType.ControlChange, channel, controller, value))
    }
}
