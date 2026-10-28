package com.example.domain.performance

data class PerformanceSettings(
    var fingerMode: FingerMode = FingerMode.Fingered,
    var splitPoint: SplitPoint = SplitPoint(),
    var touchResponse: TouchResponse = TouchResponse(),
    var pedals: MutableMap<Int, PedalState> = mutableMapOf(
        1 to PedalState(PedalFunction.Sustain),
        2 to PedalState(PedalFunction.Fill)
    ),
    var currentRegistration: Int = 1
)
