package com.example.domain.audio

data class TimeSignature(val numerator: Int, val denominator: Int)

data class TimelineState(
    var tick: Long = 0,
    var beat: Int = 1,
    var bar: Int = 1,
    var measure: Int = 1,
    var tempoBpm: Int = 120,
    var signature: TimeSignature = TimeSignature(4, 4),
    var marker: String = "None"
)

class EventTimeline {
    val state = TimelineState()

    fun updateTick(newTick: Long) {
        state.tick = newTick
        // Complex calculation of beat/bar/measure would happen here based on PPQN and tempo
    }
    
    fun setTempo(bpm: Int) { state.tempoBpm = bpm }
    fun setSignature(sig: TimeSignature) { state.signature = sig }
    fun setMarker(marker: String) { state.marker = marker }
}
