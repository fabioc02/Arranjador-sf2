package com.example.domain.yamaha

enum class ArticulationType {
    Noise, Muted, Normal, Hammer, Slide, Release, Unknown
}

data class MegaVoiceRule(
    val minVelocity: Int,
    val maxVelocity: Int,
    val articulation: ArticulationType
)

class MegaVoiceEngine {
    private val rules = mutableMapOf<Int, List<MegaVoiceRule>>()
    
    init {
        // Guitar placeholder
        rules[1] = listOf(
            MegaVoiceRule(0, 20, ArticulationType.Noise),
            MegaVoiceRule(21, 40, ArticulationType.Muted),
            MegaVoiceRule(41, 70, ArticulationType.Normal),
            MegaVoiceRule(71, 90, ArticulationType.Hammer),
            MegaVoiceRule(91, 110, ArticulationType.Slide),
            MegaVoiceRule(111, 127, ArticulationType.Release)
        )
        // Bass placeholder
        rules[2] = listOf(
            MegaVoiceRule(0, 30, ArticulationType.Muted),
            MegaVoiceRule(31, 80, ArticulationType.Normal),
            MegaVoiceRule(81, 127, ArticulationType.Slide)
        )
    }
    
    fun getArticulation(program: Int, bankMsb: Int, bankLsb: Int, velocity: Int): ArticulationType {
        // Combine MSB, LSB, Program into an ID, or simply use Program for now
        val programRules = rules[program] ?: return ArticulationType.Normal
        
        for (rule in programRules) {
            if (velocity in rule.minVelocity..rule.maxVelocity) {
                return rule.articulation
            }
        }
        return ArticulationType.Normal
    }
    
    fun processNote(program: Int, bankMsb: Int, bankLsb: Int, note: Int, velocity: Int): Pair<Int, Int> {
        val articulation = getArticulation(program, bankMsb, bankLsb, velocity)
        // Convert articulation to specific note offset and velocity
        // For now, return original
        return Pair(note, velocity)
    }
}
