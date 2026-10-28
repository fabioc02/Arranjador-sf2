package com.example.domain.yamaha

import com.example.domain.models.StyFile
import com.example.domain.models.StyFormatVersion

data class ReferenceAnalysisReport(
    val formatVersion: StyFormatVersion,
    val tempo: Double,
    val timeSignature: String,
    val sections: List<String>,
    val otsCount: Int,
    val totalMidiEvents: Int,
    val channelsUsed: List<Int>,
    val hasCasm: Boolean,
    val ntrModes: List<String>,
    val nttModes: List<String>,
    val programChangeCount: Int,
    val controlChangeCount: Int,
    val sysExCount: Int
)

class YamahaReferenceAnalyzer {
    fun analyze(style: StyFile): ReferenceAnalysisReport {
        val sections = style.sections.keys.map { it.sectionName }
        
        var totalEvents = 0
        val channels = mutableSetOf<Int>()
        var pcCount = 0
        var ccCount = 0
        var sysExCount = 0
        
        style.sections.values.forEach { sectionData ->
            sectionData.tracks.values.forEach { track ->
                totalEvents += track.events.size
                track.events.forEach { event ->
                    // Normally we'd inspect the actual MidiEvent here
                    // This is a placeholder logic
                }
            }
        }
        
        val ntrModes = mutableSetOf<String>()
        val nttModes = mutableSetOf<String>()
        
        if (style.casm.valid) {
            style.casm.sections.forEach { casmSection ->
                casmSection.channels.forEach { casmChannel ->
                    channels.add(casmChannel.sourceChannel)
                    ntrModes.add(casmChannel.ntr.name)
                    nttModes.add(casmChannel.ntt.name)
                }
            }
        }

        return ReferenceAnalysisReport(
            formatVersion = style.formatVersion,
            tempo = style.defaultBpm,
            timeSignature = style.timeSignature,
            sections = sections,
            otsCount = 4, // Placeholder for actual OTS extraction
            totalMidiEvents = totalEvents,
            channelsUsed = channels.toList().sorted(),
            hasCasm = style.casm.valid,
            ntrModes = ntrModes.toList(),
            nttModes = nttModes.toList(),
            programChangeCount = pcCount,
            controlChangeCount = ccCount,
            sysExCount = sysExCount
        )
    }
}
