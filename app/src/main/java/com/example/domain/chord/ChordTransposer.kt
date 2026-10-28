package com.example.domain.chord

import com.example.domain.models.Chord
import com.example.domain.models.CasmChannel

data class TranspositionResult(
    val originalNote: Int,
    val generatedNote: Int,
    val ruleApplied: String,
    val executionTimeUs: Long
)

class ChordTransposer {
    fun transpose(originalNote: Int, sourceChord: Chord, targetChord: Chord, casmChannel: CasmChannel): TranspositionResult {
        val startTime = System.nanoTime()
        
        val rule = YamahaRuleFactory.getRule(casmChannel.ntr, casmChannel.ntt)
        val generatedNote = rule.transpose(originalNote, sourceChord, targetChord, casmChannel)
        
        val executionTimeUs = (System.nanoTime() - startTime) / 1000L
        
        return TranspositionResult(
            originalNote = originalNote,
            generatedNote = generatedNote,
            ruleApplied = "${casmChannel.ntr.displayName} / ${casmChannel.ntt.displayName}",
            executionTimeUs = executionTimeUs
        )
    }
}
