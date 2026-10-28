package com.example.domain.midi

class MidiLearn {
    private val mappedControls = mutableMapOf<String, MidiMessage>()
    var isLearning = false
    var currentTargetId: String? = null

    fun processIncoming(message: MidiMessage): Boolean {
        if (isLearning && currentTargetId != null) {
            mappedControls[currentTargetId!!] = message
            isLearning = false
            currentTargetId = null
            return true // Consumed
        }
        return false
    }

    fun getMapping(targetId: String): MidiMessage? {
        return mappedControls[targetId]
    }
}
