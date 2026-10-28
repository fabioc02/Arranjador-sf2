package com.example.domain.midi

import com.example.domain.playback.GeneratedMidiEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MidiMonitor {
    private val _incomingMessages = MutableStateFlow<List<MidiMessage>>(emptyList())
    val incomingMessages: StateFlow<List<MidiMessage>> = _incomingMessages

    private val _generatedMessages = MutableStateFlow<List<GeneratedMidiEvent>>(emptyList())
    val generatedMessages: StateFlow<List<GeneratedMidiEvent>> = _generatedMessages

    fun logIncoming(msg: MidiMessage) {
        val current = _incomingMessages.value.toMutableList()
        current.add(msg)
        if (current.size > 100) current.removeAt(0)
        _incomingMessages.value = current
    }

    fun logGenerated(msg: GeneratedMidiEvent) {
        val current = _generatedMessages.value.toMutableList()
        current.add(msg)
        if (current.size > 100) current.removeAt(0)
        _generatedMessages.value = current
    }
}
