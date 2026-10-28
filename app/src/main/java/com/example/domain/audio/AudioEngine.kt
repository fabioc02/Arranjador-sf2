package com.example.domain.audio

interface AudioEngine {
    fun start()
    fun stop()
    
    val monitor: LatencyMonitor
    val settings: AudioSettings
    val mixer: AudioMixer
    val masterBus: MasterBus
    val soundFontManager: SoundFontManager
    val instrumentManager: InstrumentManager
    val profiler: PerformanceProfiler
    val voiceManager: VoiceManager
}
