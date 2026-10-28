package com.example.domain.engine

import kotlinx.coroutines.flow.StateFlow

interface AudioEngine {
    fun initialize()
    fun start()
    fun stop()
    fun release()
    
    // Future support for SoundFont/Oboe
    fun loadSoundFont(path: String)
}
