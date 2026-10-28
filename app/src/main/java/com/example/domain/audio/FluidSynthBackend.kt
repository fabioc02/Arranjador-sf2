package com.example.domain.audio

class FluidSynthBackend : SynthBackend {
    override fun initialize(sampleRate: Int, framesPerBuffer: Int) {}
    override fun shutdown() {}
    override fun loadSoundFont(path: String): Int = 1
    override fun unloadSoundFont(id: Int) {}
    override fun bankSelect(channel: Int, bank: Int) {}
    override fun programChange(channel: Int, program: Int) {}
    override fun noteOn(channel: Int, note: Int, velocity: Int) {}
    override fun noteOff(channel: Int, note: Int) {}
    override fun pitchBend(channel: Int, value: Int) {}
    override fun controlChange(channel: Int, controller: Int, value: Int) {}
    override fun afterTouch(channel: Int, note: Int, value: Int) {}
    override fun channelPressure(channel: Int, value: Int) {}
    override fun sysEx(data: ByteArray) {}
    override fun render(buffer: FloatArray, offset: Int, frameCount: Int) {}
    override fun panic() {}
    override fun reset() {}
    override fun getStatistics(): SynthStatistics = SynthStatistics(0, 0f, 0L)
}
