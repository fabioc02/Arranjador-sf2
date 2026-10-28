package com.example.domain.chord.reference.tables

import com.example.domain.models.Chord
import com.example.domain.models.NTR
import com.example.domain.models.NTT

object ReferencePipeline {
    
    fun process(
        originalNote: Int, 
        sourceChord: Chord, 
        targetChord: Chord, 
        ntr: NTR, 
        ntt: NTT, 
        highKey: Int, 
        lowLimit: Int, 
        highLimit: Int,
        isBassChannel: Boolean = false
    ): Int {
        var note = originalNote
        if (ntr == NTR.BYPASS) {
            return originalNote
        }
        
        // 1. NTR Base Shift
        // If it's a bass channel, use targetChord.bassKey instead of rootKey for base shift
        val baseTargetKey = if (isBassChannel) targetChord.bassKey else targetChord.rootKey
        
        if (ntr == NTR.ROOT_FIXED || ntr == NTR.ROOT_TRANS) {
            note += (baseTargetKey - sourceChord.rootKey)
        }
        
        // 2. NTT
        if (ntt != NTT.BYPASS) {
            val table = when(ntt) {
                NTT.CHORD -> ChordTable()
                NTT.DORIAN -> DorianTable()
                NTT.PENTATONIC -> PentatonicTable()
                else -> null
            }
            if (table != null) {
                val res = table.mapNote(note, originalNote, sourceChord, targetChord)
                note = res.mappedNote
            }
        }
        
        // 3. High Key
        // Based on Yamaha spec, high key applies to ROOT_TRANS and bass tracks/ROOT_FIXED in arranger
        if (ntr == NTR.ROOT_TRANS || ntr == NTR.ROOT_FIXED || isBassChannel) {
            val effHk = if (highKey in 0..11) highKey else 127
            if (baseTargetKey > effHk) {
                note -= 12
            }
        }
        
        // 4. Note Limits
        if (note > highLimit && highLimit > 0) {
            while (note > highLimit && note >= 12) note -= 12
        }
        if (note < lowLimit && lowLimit < 127) {
            while (note < lowLimit && note <= 115) note += 12
        }
        
        return note
    }
}
