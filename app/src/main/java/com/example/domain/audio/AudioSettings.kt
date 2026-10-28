package com.example.domain.audio

data class AudioSettings(
    var sampleRate: Int = 44100,
    var framesPerBuffer: Int = 256,
    var polyphony: Int = 128
)
