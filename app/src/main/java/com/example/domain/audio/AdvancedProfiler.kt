package com.example.domain.audio

data class AdvancedProfilerStats(
    var renderTimeUs: Long = 0,
    var synthTimeUs: Long = 0,
    var mixerTimeUs: Long = 0,
    var voiceAllocationTimeUs: Long = 0,
    var voiceStealingCount: Int = 0,
    var queueSize: Int = 0,
    var midiEventsPerSec: Int = 0,
    var notesPerSec: Int = 0,
    var schedulerTimeUs: Long = 0,
    var casmTimeUs: Long = 0,
    var chordEngineTimeUs: Long = 0,
    var timelineTimeUs: Long = 0,
    var dspTimeUs: Long = 0,
    var peakCpuPercent: Float = 0f,
    var avgCpuPercent: Float = 0f,
    var worstCallbackUs: Long = 0,
    var bufferMissCount: Int = 0,
    var underrunCount: Int = 0,
    var overrunCount: Int = 0,
    var gcCount: Int = 0,
    var xRuns: Int = 0
)

class AdvancedProfiler {
    val stats = AdvancedProfilerStats()
}
