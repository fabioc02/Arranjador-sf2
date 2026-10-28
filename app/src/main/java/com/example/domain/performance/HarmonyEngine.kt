package com.example.domain.performance

enum class HarmonyType {
    Duet, Trio, Block, Country, Octave
}

class HarmonyEngine {
    var enabled: Boolean = false
    var type: HarmonyType = HarmonyType.Duet
    
    // Generates harmony notes based on melody and current chord
    fun generateHarmony(melodyNote: Int, currentChord: com.example.domain.models.Chord): List<Int> {
        if (!enabled) return emptyList()
        // Placeholder
        return emptyList()
    }
}
