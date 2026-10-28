package com.example.domain.audio

data class VoiceState(
    val voiceId: Int,
    var channel: Int,
    var note: Int,
    var velocity: Int,
    var isPlaying: Boolean,
    var isSustained: Boolean,
    var isMegaVoice: Boolean
)

class VoiceManager(private val maxPolyphony: Int = 128) {
    private val voices = Array(maxPolyphony) { VoiceState(it, -1, -1, 0, false, false, false) }
    
    // Polyphony / Mono Mode
    var monoModeEnabled = false
    var legatoEnabled = false
    var sustainPedalEngaged = false
    
    fun allocateVoice(channel: Int, note: Int, velocity: Int, isMegaVoice: Boolean = false): Int {
        // Voice Stealing logic based on Voice Priority
        val availableVoice = voices.firstOrNull { !it.isPlaying && !it.isSustained }
        
        if (availableVoice != null) {
            availableVoice.channel = channel
            availableVoice.note = note
            availableVoice.velocity = velocity
            availableVoice.isPlaying = true
            availableVoice.isMegaVoice = isMegaVoice
            availableVoice.isSustained = false
            return availableVoice.voiceId
        }
        
        // Steal the oldest voice if no free voice found
        val voiceToSteal = voices.first() 
        voiceToSteal.channel = channel
        voiceToSteal.note = note
        voiceToSteal.velocity = velocity
        voiceToSteal.isPlaying = true
        voiceToSteal.isMegaVoice = isMegaVoice
        voiceToSteal.isSustained = false
        
        return voiceToSteal.voiceId
    }

    fun releaseVoice(channel: Int, note: Int) {
        val voice = voices.firstOrNull { it.channel == channel && it.note == note && it.isPlaying }
        if (voice != null) {
            if (sustainPedalEngaged) {
                voice.isSustained = true
            } else {
                voice.isPlaying = false
                voice.isSustained = false
            }
        }
    }
    
    fun onSustainPedal(engaged: Boolean) {
        sustainPedalEngaged = engaged
        if (!engaged) {
            // Release all sustained voices that aren't physically held anymore
            voices.forEach { 
                if (it.isSustained) {
                    it.isPlaying = false
                    it.isSustained = false
                }
            }
        }
    }
}
