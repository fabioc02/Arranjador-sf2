package com.example.domain.audio

class MasterBus {
    var masterVolume: Float = 1.0f
    var limiterThreshold: Float = 0.98f
    var isLimiterEnabled: Boolean = true
    
    var leftPeak: Float = 0.0f
    var rightPeak: Float = 0.0f
    var currentRms: Float = 0.0f
    var clipDetected: Boolean = false
    var latencyMs: Float = 0.0f

    fun process(buffer: FloatArray, frameCount: Int) {
        // Placeholder for master bus processing
        // Apply master volume, run through limiter, calculate peak/rms/clip
    }
    
    fun resetClip() {
        clipDetected = false
    }
}
