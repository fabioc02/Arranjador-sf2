package com.example.domain.casm

import com.example.domain.arranger.ArrangerCoreState
import com.example.domain.models.Chord
import com.example.domain.models.CasmSection
import com.example.domain.models.CasmChannel
import com.example.domain.chord.YamahaRuleFactory
import com.example.domain.models.NTR
import com.example.domain.models.NTT

/**
 * CASM PLAYBACK ENGINE
 * Componente que acopla as regras matemáticas (NTR, NTT) do CASM ao relógio de tempo real.
 */
class CasmPlaybackEngine(
    private val state: ArrangerCoreState
) {
    private var currentCasmSections = emptyList<CasmSection>()

    
    fun updateStyle(sections: List<CasmSection>) {
        currentCasmSections = sections
    }
    
    fun processNote(
        originalNote: Int, 
        channel: Int, 
        sectionName: String, 
        currentChord: Chord,
        currentBassNote: Int,
        overrideSourceRootKey: Int? = null
    ): Int {
        val casmSection = currentCasmSections.find { it.name == sectionName }

        val rule = casmSection?.channels?.find { it.sourceChannel == channel }
            ?: com.example.domain.models.CasmChannel(
                sourceChannel = channel,
                destChannel = channel,
                sourceChordRoot = "C",
                sourceChordType = "Maj",
                ntr = if (channel == 8 || channel == 9) com.example.domain.models.NTR.BYPASS else com.example.domain.models.NTR.ROOT_FIXED,
                ntt = when (channel) {
                    8, 9 -> com.example.domain.models.NTT.BYPASS // Drums (Ch 9 & 10 = channels 8 & 9)
                    10 -> com.example.domain.models.NTT.BASS // Bass (Ch 11 = channel 10)
                    else -> com.example.domain.models.NTT.CHORD // Chords
                }
            )

        // Yamaha engine explicitly forces BYPASS for any track routed to drum channels (MIDI ch 9 & 10, 0-indexed 8 & 9)
        val isDrumTrack = rule.destChannel == 8 || rule.destChannel == 9
        var effNtr = if (isDrumTrack) com.example.domain.models.NTR.BYPASS else rule.ntr
        var effNtt = if (isDrumTrack) com.example.domain.models.NTT.BYPASS else rule.ntt
        
        val sourceRootKey = overrideSourceRootKey ?: arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B").indexOf(rule.sourceChordRoot).takeIf { it >= 0 } ?: 0
        val sourceChordType = when (rule.sourceChordType.lowercase()) {
            "maj7", "major7", "m7" -> com.example.domain.models.ChordType.Major7
            "min", "minor", "m" -> com.example.domain.models.ChordType.Minor
            "min7", "minor7" -> com.example.domain.models.ChordType.Minor7
            "7", "7th", "dom7" -> com.example.domain.models.ChordType.Dominant7
            "dim", "dim7" -> com.example.domain.models.ChordType.Diminished
            else -> com.example.domain.models.ChordType.Major
        }
        val sourceChord = Chord(rootKey = sourceRootKey, type = sourceChordType, bassKey = sourceRootKey)
        
        // If Bass On bit was set in Ctb2 for the bass part, use Bass NTT
        if (rule.destChannel == 10 && rule.isBassOn && effNtt != com.example.domain.models.NTT.BYPASS) {
            effNtt = com.example.domain.models.NTT.BASS
        }

        val yamahaRule = YamahaRuleFactory.getRule(effNtr, effNtt)
        return yamahaRule.transpose(originalNote, sourceChord, currentChord, rule)
    }
}
