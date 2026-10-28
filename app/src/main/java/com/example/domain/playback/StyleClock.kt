package com.example.domain.playback

class StyleClock(val ppqn: Int = 1920) {
    var bpm: Double = 120.0
    var currentTick: Long = 0
        private set
    var currentBeat: Int = 1
        private set
    var currentBar: Int = 1
        private set
    
    // time signature
    var numerator: Int = 4
    var denominator: Int = 4

    fun reset() {
        currentTick = 0
        currentBeat = 1
        currentBar = 1
    }

    fun tick() {
        currentTick++
        
        // Calculate beat and bar
        val ticksPerBeat = ppqn / (denominator / 4.0)
        val ticksPerBar = ticksPerBeat * numerator
        
        currentBeat = ((currentTick % ticksPerBar) / ticksPerBeat).toInt() + 1
        currentBar = (currentTick / ticksPerBar).toInt() + 1
    }

    fun setTick(tick: Long) {
        currentTick = tick
        val ticksPerBeat = ppqn / (denominator / 4.0)
        val ticksPerBar = ticksPerBeat * numerator
        currentBeat = ((currentTick % ticksPerBar) / ticksPerBeat).toInt() + 1
        currentBar = (currentTick / ticksPerBar).toInt() + 1
    }
}
