package com.example.domain.midi

enum class MidiSchedulerMode {
    AUTO,
    J5_COMPATIBILITY,
    HIGH_PRECISION;

    fun resolveEffectiveMode(): MidiSchedulerMode {
        if (this != AUTO) return this
        val cores = Runtime.getRuntime().availableProcessors()
        val isLowEnd = cores <= 4 || android.os.Build.VERSION.SDK_INT <= 24
        return if (isLowEnd) J5_COMPATIBILITY else HIGH_PRECISION
    }
}
