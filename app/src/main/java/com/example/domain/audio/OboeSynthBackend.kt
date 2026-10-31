package com.example.domain.audio

/**
 * SynthBackend implementation interfacing with the C++ SynthCore via NativeAudioBridge.
 * Supports hot-swappable fallback to Kotlin software synthesis.
 */
class OboeSynthBackend(
    private val fallbackSynth: SynthBackend? = null
) : SynthBackend {
    private val tag = "OboeSynthBackend"
    private var sampleRate: Int = 44100
    private var framesPerBuffer: Int = 192

    private fun logI(msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    override fun initialize(sampleRate: Int, framesPerBuffer: Int) {
        this.sampleRate = sampleRate
        this.framesPerBuffer = framesPerBuffer
        if (NativeAudioBridge.isLoaded) {
            NativeAudioBridge.init(sampleRate, framesPerBuffer)
        }
        fallbackSynth?.initialize(sampleRate, framesPerBuffer)
    }

    override fun shutdown() {
        if (NativeAudioBridge.isLoaded) {
            NativeAudioBridge.stop()
            NativeAudioBridge.clearAllSamples()
        }
        fallbackSynth?.shutdown()
    }

    override fun loadSoundFont(path: String): Int {
        logI("Loading SoundFont: $path")
        return fallbackSynth?.loadSoundFont(path) ?: 1
    }

    override fun unloadSoundFont(id: Int) {
        fallbackSynth?.unloadSoundFont(id)
    }

    override fun bankSelect(channel: Int, bank: Int) {
        NativeAudioBridge.controlChange(channel, 0, (bank shr 7) and 0x7F)
        NativeAudioBridge.controlChange(channel, 32, bank and 0x7F)
        fallbackSynth?.bankSelect(channel, bank)
    }

    override fun programChange(channel: Int, program: Int) {
        NativeAudioBridge.programChange(channel, program)
        fallbackSynth?.programChange(channel, program)
    }

    override fun noteOn(channel: Int, note: Int, velocity: Int) {
        NativeAudioBridge.noteOn(channel, note, velocity)
        fallbackSynth?.noteOn(channel, note, velocity)
    }

    override fun noteOff(channel: Int, note: Int) {
        NativeAudioBridge.noteOff(channel, note)
        fallbackSynth?.noteOff(channel, note)
    }

    override fun pitchBend(channel: Int, value: Int) {
        NativeAudioBridge.pitchBend(channel, value)
        fallbackSynth?.pitchBend(channel, value)
    }

    override fun controlChange(channel: Int, controller: Int, value: Int) {
        NativeAudioBridge.controlChange(channel, controller, value)
        fallbackSynth?.controlChange(channel, controller, value)
    }

    override fun afterTouch(channel: Int, note: Int, value: Int) {
        fallbackSynth?.afterTouch(channel, note, value)
    }

    override fun channelPressure(channel: Int, value: Int) {
        fallbackSynth?.channelPressure(channel, value)
    }

    override fun sysEx(data: ByteArray) {
        fallbackSynth?.sysEx(data)
    }

    override fun render(buffer: FloatArray, offset: Int, frameCount: Int) {
        // Native audio callback renders directly to Oboe stream.
        // Fallback synth is invoked if buffer rendering is needed explicitly.
        fallbackSynth?.render(buffer, offset, frameCount)
    }

    override fun panic() {
        NativeAudioBridge.allNotesOff(-1)
        fallbackSynth?.panic()
    }

    override fun reset() {
        NativeAudioBridge.allNotesOff(-1)
        fallbackSynth?.reset()
    }

    override fun getStatistics(): SynthStatistics {
        val nativeVoices = NativeAudioBridge.getActiveVoiceCount()
        val fallbackStats = fallbackSynth?.getStatistics()
        return SynthStatistics(
            activeVoices = if (nativeVoices > 0) nativeVoices else (fallbackStats?.activeVoices ?: 0),
            cpuUsage = fallbackStats?.cpuUsage ?: 0.05f,
            memoryUsage = fallbackStats?.memoryUsage ?: 1024L
        )
    }
}
