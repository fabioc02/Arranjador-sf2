package com.example.domain.audio

class LatencyMonitor {
    var lastRenderTimeUs: Long = 0
    var voiceCount: Int = 0
    var cpuUsagePercent: Float = 0f
    var underruns: Int = 0
    var activePolyphony: Int = 0
    var bufferUsagePercent: Float = 0f
    
    var mixerTimeUs: Long = 0
    var masterTimeUs: Long = 0
    
    fun update(renderTimeUs: Long, mixerUs: Long, masterUs: Long, voices: Int) {
        this.lastRenderTimeUs = renderTimeUs
        this.mixerTimeUs = mixerUs
        this.masterTimeUs = masterUs
        this.voiceCount = voices
    }
}
