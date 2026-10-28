package com.example.domain.audio

interface OfflineRenderer {
    fun exportWav(outputPath: String, onProgress: (Float) -> Unit, onComplete: () -> Unit, onError: (Exception) -> Unit)
    fun exportMidi(outputPath: String, onProgress: (Float) -> Unit, onComplete: () -> Unit, onError: (Exception) -> Unit)
    fun bounce(outputPath: String, onProgress: (Float) -> Unit, onComplete: () -> Unit, onError: (Exception) -> Unit)
}
