package com.example.domain.playback

data class PlaybackMetrics(
    var midiInputUs: Long = 0,
    var chordDetectionUs: Long = 0,
    var playbackEngineUs: Long = 0,
    var schedulerUs: Long = 0,
    var totalTickUs: Long = 0,
    var generatedEventsCount: Int = 0
)
