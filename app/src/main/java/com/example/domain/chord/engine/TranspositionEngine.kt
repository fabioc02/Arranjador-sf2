package com.example.domain.chord.engine

import com.example.domain.models.Chord
import com.example.domain.models.NTR
import com.example.domain.models.NTT
import com.example.domain.chord.TranspositionResult

interface TranspositionEngine {
    fun transpose(
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
    ): TranspositionResult
}
