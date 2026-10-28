package com.example.domain.audio

data class ProfilerStats(
    var renderTimeUs: Long = 0,
    var synthTimeUs: Long = 0,
    var mixerTimeUs: Long = 0,
    var voiceCount: Int = 0,
    var polyphony: Int = 0,
    var cpuUsagePercent: Float = 0f,
    var bufferUsagePercent: Float = 0f,
    var xRuns: Int = 0,
    var audioDropouts: Int = 0,
    var gcEvents: Int = 0
)

class PerformanceProfiler {
    private val stats = ProfilerStats()

    fun updateRenderTime(timeUs: Long) { stats.renderTimeUs = timeUs }
    fun updateSynthTime(timeUs: Long) { stats.synthTimeUs = timeUs }
    fun updateMixerTime(timeUs: Long) { stats.mixerTimeUs = timeUs }
    fun updateVoiceCount(count: Int) { stats.voiceCount = count }
    fun recordXRun() { stats.xRuns++ }
    fun recordAudioDropout() { stats.audioDropouts++ }
    fun recordGcEvent() { stats.gcEvents++ }
    
    fun getStats(): ProfilerStats = stats.copy()
}
