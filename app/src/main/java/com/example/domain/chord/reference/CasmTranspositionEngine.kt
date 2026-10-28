
package com.example.domain.chord.reference

import com.example.domain.models.*

data class ReferenceResult(
    val originalNote: Int,
    val ntrShiftedNote: Int,
    val finalNote: Int,
    val ntr: NTR,
    val ntt: NTT,
    val sourceChannel: Int,
    val destChannel: Int,
    val limitedByNoteLimit: Boolean,
    val adjustedByHighKey: Boolean,
    val appliedRule: String
)

class CasmTranspositionEngine {
    fun transpose(
        originalNote: Int,
        sourceChannel: Int,
        destChannel: Int,
        ntr: NTR,
        ntt: NTT,
        sourceRoot: Int = 0,
        targetRoot: Int,
        chordType: ChordType,
        highKey: Int,
        noteLimitLow: Int,
        noteLimitHigh: Int
    ): ReferenceResult {
        if (ntt == NTT.BYPASS || ntr == NTR.BYPASS) {
            return ReferenceResult(originalNote, originalNote, originalNote, ntr, ntt, sourceChannel, destChannel, false, false, "Bypass")
        }

        var note = originalNote
        
        // --- 1. NTR SHIFT ---
        var ntrShifted = note
        if (ntr == NTR.ROOT_FIXED || ntr == NTR.ROOT_TRANS) {
            // Standard Yamaha Root Fixed behavior (shifts exactly by root delta)
            ntrShifted += (targetRoot - sourceRoot)
        }
        note = ntrShifted

        // --- 2. HIGH KEY ---
        var hkAdjusted = false
        val effHk = if (highKey in 0..11) highKey else 127
        if (targetRoot > effHk) {
            note -= 12
            hkAdjusted = true
        }

        // --- 3. NTT SHIFT (NAIVE) ---
        var rule = "Unknown NTT"
        if (ntt == NTT.CHORD) {
            rule = "Chord (Naive shift)"
            // Not doing VoiceLeading yet, just keeping the NTR shifted note
        } else if (ntt == NTT.DORIAN) {
            rule = "Dorian (Naive shift)"
        } else if (ntt == NTT.PENTATONIC) {
            rule = "Pentatonic (Naive shift)"
        } else if (ntt == NTT.BASS) {
            rule = "Bass (Naive shift)"
        }

        // --- 4. NOTE LIMITS ---
        var limited = false
        // Yamaha wrapping: if high is 72 and note is 80, wrap down octave by octave
        // Using positive bounds
        if (note > noteLimitHigh && noteLimitHigh > 0) {
            while (note > noteLimitHigh && note >= 12) note -= 12
            limited = true
        }
        if (note < noteLimitLow && noteLimitLow < 127) {
            while (note < noteLimitLow && note <= 115) note += 12
            limited = true
        }

        return ReferenceResult(originalNote, ntrShifted, note, ntr, ntt, sourceChannel, destChannel, limited, hkAdjusted, rule)
    }
}
