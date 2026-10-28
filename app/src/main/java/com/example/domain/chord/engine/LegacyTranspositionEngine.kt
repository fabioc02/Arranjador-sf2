package com.example.domain.chord.engine

import com.example.domain.models.Chord
import com.example.domain.models.CasmChannel
import com.example.domain.models.NTR
import com.example.domain.models.NTT
import com.example.domain.chord.ChordTransposer
import com.example.domain.chord.TranspositionResult

class LegacyTranspositionEngine(private val oldTransposer: ChordTransposer) : TranspositionEngine {
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
        val rootName = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")[sourceChord.rootKey % 12]
        // Mock CasmChannel for old transposer
        val dummyChannel = CasmChannel(
            sourceChannel = sourceChannel,
            destChannel = destChannel,
            ntr = ntr,
            ntt = ntt,
            highKey = highKey,
            noteLowLimit = noteLimitLow,
            noteHighLimit = noteLimitHigh,
            sourceChordRoot = rootName,
            sourceChordType = sourceChord.type.name
        )
        return oldTransposer.transpose(originalNote, sourceChord, targetChord, dummyChannel)
    }
}
