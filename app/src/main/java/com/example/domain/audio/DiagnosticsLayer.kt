package com.example.domain.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioDiagnostics(
    val averageCallbackUs: Long = 0,
    val worstCallbackUs: Long = 0,
    val xRuns: Int = 0,
    val peakLevelDb: Float = -100f,
    val rmsLevelDb: Float = -100f,
    val cpuUsagePercent: Float = 0f,
    val activeVoices: Int = 0
)

class DiagnosticsLayer {
    private val _stats = MutableStateFlow(AudioDiagnostics())
    val stats: StateFlow<AudioDiagnostics> = _stats.asStateFlow()

    fun updateStats(newStats: AudioDiagnostics) {
        _stats.value = newStats
    }
}
