package com.example.domain.engine

import com.example.domain.models.MidiEvent
import kotlinx.coroutines.flow.StateFlow

interface MidiEngine {
    fun sendEvent(event: MidiEvent)
    fun setBpm(bpm: Double)
    val currentBpm: StateFlow<Double>
}
