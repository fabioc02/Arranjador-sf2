package com.example.domain.audio

class SimpleKotlinSynth : SynthBackend {
    
    private val maxVoices = 32
    private var sampleRate = 48000f
    
    class Voice {
        var active = false
        var channel = 0
        var note = 0
        var phase = 0f
        var phaseInc = 0f
        var velocity = 0f
    }
    
    private val voices = Array(maxVoices) { Voice() }

    override fun initialize(sampleRate: Int, framesPerBuffer: Int) {
        this.sampleRate = sampleRate.toFloat()
    }

    override fun shutdown() {
        reset()
    }

    override fun loadSoundFont(path: String): Int = 1
    override fun unloadSoundFont(id: Int) {}
    override fun bankSelect(channel: Int, bank: Int) {}
    override fun programChange(channel: Int, program: Int) {}

    override fun noteOn(channel: Int, note: Int, velocity: Int) {
        if (velocity == 0) {
            noteOff(channel, note)
            return
        }
        
        // Find a free voice
        val voice = voices.find { !it.active } ?: voices.first()
        voice.active = true
        voice.channel = channel
        voice.note = note
        voice.velocity = velocity / 127f
        
        // Drum channel (10 or 9 zero-indexed) - make it short noise or high pitch
        val freq = if (channel == 9) {
            when (note) {
                35, 36 -> 60f // Kick
                38, 40 -> 200f // Snare
                42, 44, 46 -> 800f // Hihat
                else -> 440f
            }
        } else {
            440f * Math.pow(2.0, (note - 69) / 12.0).toFloat()
        }
        
        voice.phaseInc = (2.0 * Math.PI * freq / sampleRate).toFloat()
        voice.phase = 0f
    }

    override fun noteOff(channel: Int, note: Int) {
        voices.filter { it.active && it.channel == channel && it.note == note }.forEach {
            it.active = false
        }
    }

    override fun pitchBend(channel: Int, value: Int) {}
    override fun controlChange(channel: Int, controller: Int, value: Int) {}
    override fun afterTouch(channel: Int, note: Int, value: Int) {}
    override fun channelPressure(channel: Int, value: Int) {}
    override fun sysEx(data: ByteArray) {}

    override fun render(buffer: FloatArray, offset: Int, frameCount: Int) {
        // Clear buffer
        for (i in 0 until frameCount * 2) {
            buffer[offset + i] = 0f
        }
        
        // Render voices
        for (voice in voices) {
            if (voice.active) {
                for (i in 0 until frameCount) {
                    val phaseNorm = (voice.phase / (2.0 * Math.PI)).toFloat()
                    
                    var sample = 0f
                    if (voice.channel == 9) { 
                        // Drums: Square / Noise
                        sample = if (phaseNorm < 0.5f) 1f else -1f
                    } else if (voice.channel == 10 || voice.channel == 11) { 
                        // Bass (Channels 11 and 12 zero-indexed 10,11): Sawtooth
                        sample = 2f * phaseNorm - 1f
                    } else { 
                        // Others: Triangle
                        sample = if (phaseNorm < 0.5f) {
                            4f * phaseNorm - 1f
                        } else {
                            3f - 4f * phaseNorm
                        }
                    }

                    // Lower volume slightly so it doesn't clip when multiple voices play
                    sample *= voice.velocity * 0.15f
                    
                    // Boost bass so we can hear chord changes better on mobile speakers
                    if (voice.channel == 10 || voice.channel == 11) {
                        sample *= 1.5f 
                    }

                    buffer[offset + i * 2] += sample
                    buffer[offset + i * 2 + 1] += sample
                    
                    voice.phase += voice.phaseInc
                    if (voice.phase > 2.0 * Math.PI) {
                        voice.phase -= (2.0 * Math.PI).toFloat()
                    }
                }
            }
        }
    }

    override fun panic() { reset() }
    
    override fun reset() {
        for (voice in voices) {
            voice.active = false
        }
    }

    override fun getStatistics(): SynthStatistics {
        val activeCount = voices.count { it.active }
        return SynthStatistics(activeCount, 0f, 0L)
    }
}
