package com.example.domain.chord

import com.example.domain.models.Chord
import com.example.domain.models.CasmChannel
import com.example.domain.chord.engine.*

class ChordEngine {
    private val analyzer = ChordAnalyzer()
    
    private val legacyEngine = LegacyTranspositionEngine(ChordTransposer())
    private val referenceEngine = ReferenceTranspositionEngine()

    fun detectChord(notes: List<Int>): Chord? {
        return analyzer.detectChord(notes)
    }
    
    private fun getActiveEngine(): TranspositionEngine {
        return when (EngineConfig.transpositionEngineType) {
            TranspositionEngineType.LEGACY -> legacyEngine
            TranspositionEngineType.REFERENCE -> referenceEngine
        }
    }

    fun transpose(
        originalNote: Int,
        sourceChannel: Int,
        destChannel: Int,
        ntr: com.example.domain.models.NTR,
        ntt: com.example.domain.models.NTT,
        sourceChord: Chord,
        targetChord: Chord,
        highKey: Int,
        noteLimitLow: Int,
        noteLimitHigh: Int
    ): TranspositionResult {
        return getActiveEngine().transpose(
            originalNote, sourceChannel, destChannel, ntr, ntt, 
            sourceChord, targetChord, highKey, noteLimitLow, noteLimitHigh
        )
    }
    
    // Kept for backward compatibility if needed in tests
    fun transpose(originalNote: Int, sourceChord: Chord, targetChord: Chord, casmChannel: CasmChannel): TranspositionResult {
        return transpose(
            originalNote, 
            casmChannel.sourceChannel, 
            casmChannel.destChannel, 
            casmChannel.ntr, 
            casmChannel.ntt, 
            sourceChord, 
            targetChord, 
            casmChannel.highKey, 
            casmChannel.noteLowLimit, 
            casmChannel.noteHighLimit
        )
    }
}
