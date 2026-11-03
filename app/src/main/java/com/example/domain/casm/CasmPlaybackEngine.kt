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
        val cleanSection = sectionName.trim()
        val casmSection = currentCasmSections.find { it.name.trim().equals(cleanSection, ignoreCase = true) }
            ?: currentCasmSections.find { it.name.replace(" ", "").equals(cleanSection.replace(" ", ""), ignoreCase = true) }
            ?: if (cleanSection.equals("Break", ignoreCase = true)) currentCasmSections.find { it.name.trim().equals("Fill In BA", ignoreCase = true) } else null
            ?: if (cleanSection.equals("Fill In BA", ignoreCase = true)) currentCasmSections.find { it.name.trim().equals("Break", ignoreCase = true) } else null
            ?: currentCasmSections.firstOrNull()

        val rule = casmSection?.channels?.find { it.sourceChannel == channel }
            ?: casmSection?.channels?.find { it.destChannel == channel }
            ?: com.example.domain.models.CasmChannel(
                sourceChannel = channel,
                destChannel = channel,
                sourceChordRoot = "C",
                sourceChordType = "Maj",
                ntr = if (channel == 8 || channel == 9) com.example.domain.models.NTR.BYPASS else com.example.domain.models.NTR.ROOT_FIXED,
                ntt = when (channel) {
                    8, 9 -> com.example.domain.models.NTT.BYPASS // Drums
                    10 -> com.example.domain.models.NTT.BASS // Bass
                    else -> com.example.domain.models.NTT.CHORD // Chords
                }
            )

        // Yamaha rule: Any track routed to drum channels (MIDI ch 9 & 10, 0-indexed 8 & 9) is strictly BYPASS
        val isDrumTrack = channel == 8 || channel == 9 || rule.destChannel == 8 || rule.destChannel == 9 ||
                          rule.partName.contains("drum", ignoreCase = true) || rule.partName.contains("rhythm", ignoreCase = true)
        if (isDrumTrack || rule.ntr == com.example.domain.models.NTR.BYPASS || rule.ntt == com.example.domain.models.NTT.BYPASS) {
            return originalNote.coerceIn(0, 127)
        }
        
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
        
        // Effective destination chord with on-bass note if specified
        val effectiveDestChord = if (currentBassNote in 0..127 && (rule.isBassOn || rule.destChannel == 10)) {
            currentChord.copy(bassKey = currentBassNote % 12)
        } else {
            currentChord
        }

        val yamahaRule = YamahaRuleFactory.getRule(rule.ntr, rule.ntt)
        return yamahaRule.transpose(originalNote, sourceChord, effectiveDestChord, rule)
    }
}
