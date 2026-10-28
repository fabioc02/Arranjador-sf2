package com.example

object ArrangerEngine {
    
    fun detectChord(activeNotes: List<Int>): com.example.domain.models.Chord? {
        if (activeNotes.isEmpty()) return null
        
        val uniqueNotes = activeNotes.map { it % 12 }.distinct().sorted()
        if (uniqueNotes.isEmpty()) return null
        
        val lowestNote = activeNotes.minOrNull() ?: 0
        val bass = lowestNote % 12
        
        for (root in 0..11) {
            val third = (root + 4) % 12
            val flatThird = (root + 3) % 12
            val fifth = (root + 7) % 12
            val flatSeventh = (root + 10) % 12
            val seventh = (root + 11) % 12
            
            val hasRoot = uniqueNotes.contains(root)
            val hasThird = uniqueNotes.contains(third)
            val hasFlatThird = uniqueNotes.contains(flatThird)
            val hasFifth = uniqueNotes.contains(fifth)
            val hasFlatSeventh = uniqueNotes.contains(flatSeventh)
            val hasSeventh = uniqueNotes.contains(seventh)
            
            if (hasRoot && hasThird && hasFifth && hasFlatSeventh) return com.example.domain.models.Chord(root, com.example.domain.models.ChordType.Dominant7, bass)
            if (hasRoot && hasFlatThird && hasFifth && hasFlatSeventh) return com.example.domain.models.Chord(root, com.example.domain.models.ChordType.Minor7, bass)
            if (hasRoot && hasThird && hasFifth && hasSeventh) return com.example.domain.models.Chord(root, com.example.domain.models.ChordType.Major7, bass)
            if (hasRoot && hasFlatThird && hasFifth) return com.example.domain.models.Chord(root, com.example.domain.models.ChordType.Minor, bass)
            if (hasRoot && hasThird && hasFifth) return com.example.domain.models.Chord(root, com.example.domain.models.ChordType.Major, bass)
            if (hasRoot && hasThird) return com.example.domain.models.Chord(root, com.example.domain.models.ChordType.Major, bass)
            if (hasRoot && hasFlatThird) return com.example.domain.models.Chord(root, com.example.domain.models.ChordType.Minor, bass)
        }
        
        return com.example.domain.models.Chord(bass, com.example.domain.models.ChordType.Major, bass)
    }

    // Transpose a note from CMaj7 (default style key) to the target chord
    fun transposeNote(
        originalNote: Int, 
        targetChord: com.example.domain.models.Chord, 
        isBass: Boolean = false, 
        sectionName: String = "Main A", 
        styleCasm: com.example.domain.models.StyleCasm? = null,
        trackChannel: Int = 10,
        sourceChord: com.example.domain.models.Chord = com.example.domain.models.Chord(0, com.example.domain.models.ChordType.Major7)
    ): Int {
        val domainTarget = targetChord
        val domainSource = sourceChord
        
        // Find the actual CASM settings for the given track channel in the given section
        val casmSection = styleCasm?.sections?.find { it.name.equals(sectionName, ignoreCase = true) }
        val actualCasm = casmSection?.channels?.find { it.destChannel == trackChannel }
        
        // Fallback CASM if not found
        val fallbackCasm = com.example.domain.models.CasmChannel(
            sourceChannel = trackChannel,
            destChannel = trackChannel,
            ntr = com.example.domain.models.NTR.ROOT_FIXED,
            ntt = if (isBass) com.example.domain.models.NTT.BASS else com.example.domain.models.NTT.CHORD,
            highKey = if (isBass) 0 else 127,
            noteLowLimit = 0,
            noteHighLimit = 127
        )
        
        var casmToUse = actualCasm ?: fallbackCasm
        
        // FORCE BASS RULE for Bass track, unless it's explicitly BYPASS.
        // Yamaha uses different NTTs for Bass (like 02 Chord, 08, etc) but our users 
        // expect the walking bass to not be destroyed by Chord snapping.
        if (isBass && casmToUse.ntt != com.example.domain.models.NTT.BYPASS && casmToUse.ntr != com.example.domain.models.NTR.BYPASS) {
            casmToUse = casmToUse.copy(ntt = com.example.domain.models.NTT.BASS)
        }
        
        val rule = com.example.domain.chord.YamahaRuleFactory.getRule(casmToUse.ntr, casmToUse.ntt)
        return rule.transpose(originalNote, domainSource, domainTarget, casmToUse)
    }
}
