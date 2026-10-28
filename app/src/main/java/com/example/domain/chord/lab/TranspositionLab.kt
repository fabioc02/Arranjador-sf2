package com.example.domain.chord.lab

import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.NTR
import com.example.domain.models.NTT

data class LabResult(
    val originalNote: Int,
    val finalNote: Int,
    val appliedInterval: Int,
    val ruleName: String,
    val isSourceChordTone: Boolean,
    val isTargetChordTone: Boolean,
    val isPassingTone: Boolean,
    val octaveAdjustment: Int,
    val appliedHighKey: Boolean,
    val appliedNoteLimits: Boolean
)

object TranspositionLab {
    
    // Simple degree extraction. Root = 0.
    fun isChordTone(note: Int, chord: Chord): Boolean {
        val normalized = note % 12
        val chordNotes = getChordTones(chord)
        return chordNotes.contains(normalized)
    }
    
    fun getChordTones(chord: Chord): List<Int> {
        val root = chord.rootKey
        val tones = mutableListOf(root)
        when (chord.type) {
            ChordType.Major -> {
                tones.add((root + 4) % 12)
                tones.add((root + 7) % 12)
            }
            ChordType.Minor -> {
                tones.add((root + 3) % 12)
                tones.add((root + 7) % 12)
            }
            ChordType.Major7 -> {
                tones.add((root + 4) % 12)
                tones.add((root + 7) % 12)
                tones.add((root + 11) % 12)
            }
            ChordType.Minor7 -> {
                tones.add((root + 3) % 12)
                tones.add((root + 7) % 12)
                tones.add((root + 10) % 12)
            }
            ChordType.Dominant7 -> {
                tones.add((root + 4) % 12)
                tones.add((root + 7) % 12)
                tones.add((root + 10) % 12)
            }
            ChordType.Minor7b5 -> {
                tones.add((root + 3) % 12)
                tones.add((root + 6) % 12)
                tones.add((root + 10) % 12)
            }
            ChordType.Diminished -> {
                tones.add((root + 3) % 12)
                tones.add((root + 6) % 12)
            }
            ChordType.Augmented -> {
                tones.add((root + 4) % 12)
                tones.add((root + 8) % 12)
            }
            ChordType.Sus2 -> {
                tones.add((root + 2) % 12)
                tones.add((root + 7) % 12)
            }
            ChordType.Sus4 -> {
                tones.add((root + 5) % 12)
                tones.add((root + 7) % 12)
            }
            ChordType.Major6 -> {
                tones.add((root + 4) % 12)
                tones.add((root + 7) % 12)
                tones.add((root + 9) % 12)
            }
            ChordType.Major9 -> {
                tones.add((root + 4) % 12)
                tones.add((root + 7) % 12)
                tones.add((root + 11) % 12)
                tones.add((root + 2) % 12)
            }
            ChordType.Major11 -> {
                tones.add((root + 4) % 12)
                tones.add((root + 7) % 12)
                tones.add((root + 11) % 12)
                tones.add((root + 2) % 12)
                tones.add((root + 5) % 12)
            }
            ChordType.Major13 -> {
                tones.add((root + 4) % 12)
                tones.add((root + 7) % 12)
                tones.add((root + 11) % 12)
                tones.add((root + 2) % 12)
                tones.add((root + 5) % 12)
                tones.add((root + 9) % 12)
            }
            ChordType.Add9 -> {
                tones.add((root + 4) % 12)
                tones.add((root + 7) % 12)
                tones.add((root + 2) % 12)
            }
        }
        return tones
    }
    
    // Experimental transposition mapping
    fun experiment(
        originalNote: Int,
        sourceChord: Chord,
        targetChord: Chord,
        ntr: NTR,
        ntt: NTT,
        highKey: Int,
        noteLimitLow: Int,
        noteLimitHigh: Int
    ): LabResult {
        var note = originalNote
        
        // 1. NTR
        if (ntr == NTR.ROOT_FIXED || ntr == NTR.ROOT_TRANS) {
            note += (targetChord.rootKey - sourceChord.rootKey)
        }
        
        val isSource = isChordTone(originalNote, sourceChord)
        
        // 2. NTT (Provisional hypotheses)
        val ruleName = ntt.name
        
        // 3. High Key
        var appliedHk = false
        val effHk = if (highKey in 0..11) highKey else 127
        if (targetChord.rootKey > effHk) {
            note -= 12
            appliedHk = true
        }
        
        // 4. Note Limits
        var appliedLimits = false
        if (note > noteLimitHigh && noteLimitHigh > 0) {
            while (note > noteLimitHigh && note >= 12) note -= 12
            appliedLimits = true
        }
        if (note < noteLimitLow && noteLimitLow < 127) {
            while (note < noteLimitLow && note <= 115) note += 12
            appliedLimits = true
        }
        
        val isTarget = isChordTone(note, targetChord)
        
        return LabResult(
            originalNote = originalNote,
            finalNote = note,
            appliedInterval = note - originalNote,
            ruleName = ruleName,
            isSourceChordTone = isSource,
            isTargetChordTone = isTarget,
            isPassingTone = !isSource, // Simplified
            octaveAdjustment = (note - originalNote) / 12, // approx
            appliedHighKey = appliedHk,
            appliedNoteLimits = appliedLimits
        )
    }
}
