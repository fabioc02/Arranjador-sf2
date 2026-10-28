package com.example.domain.performance

enum class PedalFunction {
    Sustain,
    Fill,
    Variation,
    Break,
    StartStop,
    Assignable
}

data class PedalState(
    var function: PedalFunction = PedalFunction.Sustain,
    var isPressed: Boolean = false
)
