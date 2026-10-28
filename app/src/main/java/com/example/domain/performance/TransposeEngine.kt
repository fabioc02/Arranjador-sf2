package com.example.domain.performance

class TransposeEngine {
    var transposeAmount: Int = 0 // semitones

    fun applyTransposition(note: Int): Int {
        return (note + transposeAmount).coerceIn(0, 127)
    }
}
