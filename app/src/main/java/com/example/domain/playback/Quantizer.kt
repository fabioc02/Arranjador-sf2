package com.example.domain.playback

class Quantizer {
    // To be implemented in the future if needed for realtime humanize/quantize features
    fun quantizeTick(tick: Long, resolution: Int): Long {
        val halfRes = resolution / 2
        return ((tick + halfRes) / resolution) * resolution
    }
}
