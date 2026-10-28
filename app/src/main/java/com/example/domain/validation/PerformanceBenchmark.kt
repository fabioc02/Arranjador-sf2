package com.example.domain.validation

data class TickProfile(
    val parserTimeUs: Long,
    val casmTimeUs: Long,
    val chordDetectionTimeUs: Long,
    val transpositionTimeUs: Long,
    val voiceLeadingTimeUs: Long,
    val midiRoutingTimeUs: Long,
    val queueTimeUs: Long,
    val totalTimeUs: Long
)

data class BenchmarkResult(
    val totalTicks: Long,
    val avgTimePerTickUs: Double,
    val maxTimePerTickUs: Long,
    val minTimePerTickUs: Long,
    val ticksOver500Us: Int,
    val avgParserTimeUs: Double,
    val avgCasmTimeUs: Double,
    val avgChordDetectionTimeUs: Double,
    val avgTranspositionTimeUs: Double,
    val avgVoiceLeadingTimeUs: Double,
    val avgMidiRoutingTimeUs: Double,
    val avgQueueTimeUs: Double
)

class PerformanceBenchmark {
    fun measure(action: () -> Unit, iterations: Int = 1): Long {
        val start = System.nanoTime()
        for (i in 0 until iterations) {
            action()
        }
        val end = System.nanoTime()
        return (end - start) / 1000 // Microseconds
    }
}
