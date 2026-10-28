package com.example.domain.performance

import com.example.domain.models.Chord
import com.example.domain.models.ChordType

data class PerformanceState(
    var detectedChord: Chord = Chord(0, ChordType.Major),
    var lastMelodyNote: Int = -1,
    var lastVelocity: Int = 0
)
