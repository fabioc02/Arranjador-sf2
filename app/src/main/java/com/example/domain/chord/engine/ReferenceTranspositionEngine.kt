package com.example.domain.chord.engine

import com.example.domain.models.Chord
import com.example.domain.models.NTR
import com.example.domain.models.NTT
import com.example.domain.chord.TranspositionResult
import com.example.domain.chord.reference.tables.ReferencePipeline

class ReferenceTranspositionEngine : TranspositionEngine {
    override fun transpose(
        originalNote: Int,
        sourceChannel: Int,
        destChannel: Int,
        ntr: NTR,
        ntt: NTT,
        sourceChord: Chord,
        targetChord: Chord,
        highKey: Int,
        noteLimitLow: Int,
        noteLimitHigh: Int
    ): TranspositionResult {
        val startTime = System.nanoTime()
        
        val finalNote = ReferencePipeline.process(
            originalNote = originalNote,
            sourceChord = sourceChord,
            targetChord = targetChord,
            ntr = ntr,
            ntt = ntt,
            highKey = highKey,
            lowLimit = noteLimitLow,
            highLimit = noteLimitHigh,
            isBassChannel = destChannel == 10
        )

        return TranspositionResult(
            originalNote = originalNote,
            generatedNote = finalNote,
            ruleApplied = "Reference / ${ntt.displayName}",
            executionTimeUs = (System.nanoTime() - startTime) / 1000L
        )
    }
}
