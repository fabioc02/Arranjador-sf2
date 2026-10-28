package com.example.domain.chord

import com.example.domain.models.Chord
import com.example.domain.models.CasmChannel
import com.example.domain.models.NTR
import com.example.domain.models.NTT
import com.example.domain.models.ChordType

interface YamahaRule {
    fun transpose(
        note: Int,
        sourceChord: Chord,
        destinationChord: Chord,
        casm: CasmChannel
    ): Int
}

class RootFixedRule : YamahaRule {
    override fun transpose(note: Int, sourceChord: Chord, destinationChord: Chord, casm: CasmChannel): Int {
        val targetIntervals = ChordTables.getIntervals(destinationChord.type)
        
        // For bass tracks, we base the shift around the bassKey (for slash chords)
        val isBass = casm.destChannel == 10 || casm.destChannel == 0x0A || casm.destChannel == 11
        
        var newNote = if (isBass && destinationChord.bassKey != destinationChord.rootKey) {
            val bassDiff = destinationChord.bassKey - sourceChord.rootKey
            var bassNote = note + bassDiff
            val effHk = if (casm.highKey in 0..11) casm.highKey else 127
            if (destinationChord.bassKey > effHk && effHk < 12) {
                bassNote -= 12
            }
            bassNote
        } else {
            val is7thSourceNote = ((note - sourceChord.rootKey) % 12 + 12) % 12 == 11
            val effectiveIntervals = if (is7thSourceNote && destinationChord.type == ChordType.Minor) {
                listOf(0, 3, 7, 10)
            } else {
                targetIntervals
            }
            VoiceLeading.findClosestChordTone(destinationChord.rootKey, effectiveIntervals, note)
        }
        
        // Apply Note Limits
        val lowLimit = casm.noteLowLimit
        val highLimit = if (casm.noteHighLimit in 12..127) casm.noteHighLimit else 127
        if (newNote > highLimit && highLimit > lowLimit) {
            while (newNote > highLimit && newNote >= lowLimit + 12) newNote -= 12
        }
        if (newNote < lowLimit && highLimit > lowLimit) {
            while (newNote < lowLimit && newNote <= highLimit - 12) newNote += 12
        }
        
        return newNote.coerceIn(0, 127)
    }
}

class RootTransRule : YamahaRule {
    override fun transpose(note: Int, sourceChord: Chord, destinationChord: Chord, casm: CasmChannel): Int {
        val isBass = casm.destChannel == 10 || casm.destChannel == 0x0A || casm.destChannel == 11
        val targetAnchor = if (isBass) destinationChord.bassKey else destinationChord.rootKey
        
        val rootDiff = targetAnchor - sourceChord.rootKey
        var parallelNote = note + rootDiff
        val targetIntervals = ChordTables.getIntervals(destinationChord.type)
        var newNote = if (isBass && destinationChord.bassKey != destinationChord.rootKey) {
            parallelNote
        } else {
            VoiceLeading.findClosestChordTone(destinationChord.rootKey, targetIntervals, parallelNote)
        }
        
        // High Key: upper root limit for Root Trans
        val effHk = if (casm.highKey in 0..11) casm.highKey else 127
        if (targetAnchor > effHk) {
            newNote -= 12
        }
        
        val lowLimit = casm.noteLowLimit
        val highLimit = if (casm.noteHighLimit in 12..127) casm.noteHighLimit else 127
        if (newNote > highLimit && highLimit > lowLimit) {
            while (newNote > highLimit && newNote >= lowLimit + 12) newNote -= 12
        }
        if (newNote < lowLimit && highLimit > lowLimit) {
            while (newNote < lowLimit && newNote <= highLimit - 12) newNote += 12
        }
        
        return newNote.coerceIn(0, 127)
    }
}

class BassRule : YamahaRule {
    // Yamaha Bass Tables
    private val bassTableMajor = intArrayOf(0, 0, 2, 4, 4, 5, 7, 7, 9, 9, 10, 11)
    private val bassTableMinor = intArrayOf(0, 0, 2, 3, 3, 5, 7, 7, 8, 8, 10, 11)
    private val bassTableDom7 = intArrayOf(0, 0, 2, 4, 4, 5, 7, 7, 9, 9, 10, 10)
    private val bassTableMin7 = intArrayOf(0, 0, 2, 3, 3, 5, 7, 7, 8, 8, 10, 10)
    private val bassTableMaj7 = intArrayOf(0, 0, 2, 4, 4, 5, 7, 7, 9, 9, 11, 11)
    private val bassTableDim = intArrayOf(0, 0, 2, 3, 3, 5, 6, 6, 9, 9, 9, 11)
    private val bassTableAug = intArrayOf(0, 0, 2, 4, 4, 5, 8, 8, 8, 8, 10, 11)
    private val bassTableSus4 = intArrayOf(0, 0, 2, 5, 5, 5, 7, 7, 9, 9, 10, 10)

    override fun transpose(note: Int, sourceChord: Chord, destinationChord: Chord, casm: CasmChannel): Int {
        val noteClassInSource = (note - sourceChord.rootKey + 120) % 12

        val table = when (destinationChord.type) {
            ChordType.Minor -> bassTableMinor
            ChordType.Dominant7 -> bassTableDom7
            ChordType.Minor7 -> bassTableMin7
            ChordType.Major7 -> bassTableMaj7
            ChordType.Diminished -> bassTableDim
            ChordType.Augmented -> bassTableAug
            ChordType.Sus4 -> bassTableSus4
            else -> bassTableMajor
        }

        // targetNoteClass represents the interval from the destination root.
        val targetNoteClass = table[noteClassInSource]
        val destRoot = destinationChord.rootKey
        val destBass = destinationChord.bassKey
        
        // Calculate raw destination note without octave consideration yet
        var destNoteClassRaw = destRoot + targetNoteClass
        
        // Handle Slash Chord Inversions: if this is the root note, play the bass note instead!
        if (targetNoteClass == 0 && destBass != destRoot) {
            destNoteClassRaw = destBass
        }
        
        // Calculate the difference between the source note's scale degree and the destination note's scale degree
        val sourceNoteClassRaw = sourceChord.rootKey + noteClassInSource
        
        // We apply the difference to the original note to preserve the exact same octave relationships
        var newNote = note + (destNoteClassRaw - sourceNoteClassRaw)
        
        // Adjust for High Key (if destination base anchor is above HighKey, drop an octave)
        // HighKey is usually between 0-11, representing C to B.
        val effectiveHighKey = if (casm.highKey in 0..11) casm.highKey else 127
        val anchorForHighKey = if (destBass != destRoot) destBass else destRoot
        if (anchorForHighKey > effectiveHighKey) {
            newNote -= 12
        }
        
        // Note Limits for Bass (typically E0 = 28 to 127/79)
        val lowLimit = if (casm.noteLowLimit in 0..127) casm.noteLowLimit else 28
        val highLimit = if (casm.noteHighLimit in 12..127) casm.noteHighLimit else 127
        if (newNote > highLimit && highLimit > lowLimit) {
            while (newNote > highLimit && newNote >= lowLimit + 12) newNote -= 12
        }
        if (newNote < lowLimit && highLimit > lowLimit) {
            while (newNote < lowLimit && newNote <= highLimit - 12) newNote += 12
        }

        return newNote.coerceIn(0, 127)
    }
}

class MelodyRule : YamahaRule {
    override fun transpose(note: Int, sourceChord: Chord, destinationChord: Chord, casm: CasmChannel): Int {
        val rootDiff = destinationChord.rootKey - sourceChord.rootKey
        var newNote = note + rootDiff

        // Apply High Key upper root limit if specified
        val effHk = if (casm.highKey in 0..11) casm.highKey else 127
        if (destinationChord.rootKey > effHk) {
            newNote -= 12
        }

        // Apply Note Limits
        val lowLimit = casm.noteLowLimit
        val highLimit = if (casm.noteHighLimit in 12..127) casm.noteHighLimit else 127
        if (newNote > highLimit && highLimit > lowLimit) {
            while (newNote > highLimit && newNote >= lowLimit + 12) newNote -= 12
        }
        if (newNote < lowLimit && highLimit > lowLimit) {
            while (newNote < lowLimit && newNote <= highLimit - 12) newNote += 12
        }

        return newNote.coerceIn(0, 127)
    }
}

class GuitarRule : YamahaRule {
    override fun transpose(note: Int, sourceChord: Chord, destinationChord: Chord, casm: CasmChannel): Int {
        return (note + (destinationChord.rootKey - sourceChord.rootKey)).coerceIn(0, 127)
    }
}

class BypassRule : YamahaRule {
    override fun transpose(note: Int, sourceChord: Chord, destinationChord: Chord, casm: CasmChannel): Int {
        return note.coerceIn(0, 127)
    }
}

object YamahaRuleFactory {
    private val bypassRule = BypassRule()
    private val rootFixedRule = RootFixedRule()
    private val rootTransRule = RootTransRule()
    private val bassRule = BassRule()
    private val guitarRule = GuitarRule()
    private val melodyRule = MelodyRule()
    
    fun getRule(ntr: NTR, ntt: NTT): YamahaRule {
        if (ntr == NTR.BYPASS || ntt == NTT.BYPASS) return bypassRule
        if (ntt == NTT.MELODY) return melodyRule
        // If it's a bass table, or explicitly requested
        if (ntt == NTT.BASS || ntt == NTT.DORIAN) return bassRule
        if (ntr == NTR.GUITAR) return guitarRule
        
        return when (ntr) {
            NTR.ROOT_FIXED -> rootFixedRule
            NTR.ROOT_TRANS -> rootTransRule
            else -> rootFixedRule
        }
    }
}
