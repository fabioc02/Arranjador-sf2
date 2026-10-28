package com.example.domain.performance

import com.example.domain.chord.ChordAnalyzer

class PerformanceEngine {
    val settings = PerformanceSettings()
    val state = PerformanceState()
    val transposeEngine = TransposeEngine()
    val harmonyEngine = HarmonyEngine()
    private val chordAnalyzer = ChordAnalyzer()
    
    private val activeKeysLeft = mutableSetOf<Int>()
    private val activeKeysRight = mutableSetOf<Int>()

    fun processMidiNoteOn(note: Int, velocity: Int) {
        val transposedNote = transposeEngine.applyTransposition(note)
        val finalVelocity = settings.touchResponse.curve.apply(velocity)

        state.lastVelocity = finalVelocity
        
        if (settings.fingerMode == FingerMode.FullKeyboard || settings.fingerMode == FingerMode.AiFullKeyboard) {
            activeKeysLeft.add(transposedNote)
            activeKeysRight.add(transposedNote)
            evaluateChord()
        } else {
            if (transposedNote <= settings.splitPoint.splitKey) {
                activeKeysLeft.add(transposedNote)
                evaluateChord()
            } else {
                activeKeysRight.add(transposedNote)
                state.lastMelodyNote = transposedNote
            }
        }
    }

    fun processMidiNoteOff(note: Int) {
        val transposedNote = transposeEngine.applyTransposition(note)
        activeKeysLeft.remove(transposedNote)
        activeKeysRight.remove(transposedNote)
        
        // Full keyboard evaluation logic if needed
        if (settings.fingerMode == FingerMode.FullKeyboard) {
             evaluateChord()
        }
    }

    private fun evaluateChord() {
        if (activeKeysLeft.size >= 3) { // Minimum threshold for Fingered mode initially
            val detected = chordAnalyzer.detectChord(activeKeysLeft.toList())
            if (detected != null && detected.rootKey != -1) {
                state.detectedChord = detected
            }
        }
    }
}
