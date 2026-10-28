package com.example.domain.audio

data class SynthStatistics(
    val activeVoices: Int,
    val cpuUsage: Float,
    val memoryUsage: Long
)

interface SynthBackend {
    fun initialize(sampleRate: Int, framesPerBuffer: Int)
    fun shutdown()
    fun loadSoundFont(path: String): Int
    fun unloadSoundFont(id: Int)
    fun bankSelect(channel: Int, bank: Int)
    fun programChange(channel: Int, program: Int)
    fun noteOn(channel: Int, note: Int, velocity: Int)
    fun noteOff(channel: Int, note: Int)
    fun pitchBend(channel: Int, value: Int)
    fun controlChange(channel: Int, controller: Int, value: Int)
    fun afterTouch(channel: Int, note: Int, value: Int)
    fun channelPressure(channel: Int, value: Int)
    fun sysEx(data: ByteArray)
    fun render(buffer: FloatArray, offset: Int, frameCount: Int)
    fun panic()
    fun reset()
    fun getStatistics(): SynthStatistics
}
