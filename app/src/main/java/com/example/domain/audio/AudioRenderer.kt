package com.example.domain.audio

interface AudioRenderer {
    fun render(buffer: FloatArray, frameCount: Int)
}
