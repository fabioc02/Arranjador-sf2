package com.example.domain.audio

// Voice -> VoiceState -> VoiceDSP -> VoiceEnvelope -> VoiceLFO -> VoiceFilter
data class VoiceFilter(var cutoff: Float = 1.0f, var resonance: Float = 0.0f)
data class VoiceLFO(var rate: Float = 0.0f, var depth: Float = 0.0f)
data class VoiceEnvelope(var attack: Float = 0.0f, var decay: Float = 0.0f, var sustain: Float = 1.0f, var release: Float = 0.0f)
data class VoiceDSP(var eq: Float = 0.0f, val filter: VoiceFilter = VoiceFilter(), val lfo: VoiceLFO = VoiceLFO(), val env: VoiceEnvelope = VoiceEnvelope())
data class AdvancedVoiceState(
    val id: Int,
    var channel: Int = -1,
    var note: Int = -1,
    val dsp: VoiceDSP = VoiceDSP()
)

class AdvancedVoicePool(val maxVoices: Int = 128) {
    val voices = Array(maxVoices) { AdvancedVoiceState(it) }
}
