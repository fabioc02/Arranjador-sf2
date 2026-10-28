package com.example.domain.audio

interface AudioBackend {
    fun start(sampleRate: Int, framesPerBuffer: Int)
    fun stop()
    fun setRenderer(renderer: AudioRenderer)
}
