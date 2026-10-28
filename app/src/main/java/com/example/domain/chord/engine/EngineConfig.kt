package com.example.domain.chord.engine

enum class TranspositionEngineType {
    LEGACY,
    REFERENCE
}

object EngineConfig {
    var transpositionEngineType = TranspositionEngineType.LEGACY
}
