package com.example.domain.analyzer

import com.example.domain.models.StyFile

data class CompatibilityReport(
    val format: String,
    val tracksCount: Int,
    val sectionsCount: Int,
    val eventsCount: Int,
    val usedNtrs: Set<String>,
    val usedNtts: Set<String>,
    val unsupportedFeatures: List<String>,
    val estimatedCompatibility: Int, // 0-100
    
    // New metrics
    val programChangesCount: Int = 0,
    val controlChangesCount: Int = 0,
    val sysExCount: Int = 0,
    val nrpnCount: Int = 0,
    val rpnCount: Int = 0,
    val tempoChangesCount: Int = 0,
    val timeSignatureChangesCount: Int = 0,
    val markerCount: Int = 0,
    val casmCoverage: Int = 100, // percentage
    val otsCoverage: Int = 0 // percentage
)

class YamahaCompatibilityAnalyzer {

    fun analyze(style: StyFile): CompatibilityReport {
        val sectionsCount = style.sections.size
        var tracksCount = 0
        var eventsCount = 0
        
        var programChangesCount = 0
        var controlChangesCount = 0
        var sysExCount = 0
        var nrpnCount = 0
        var rpnCount = 0
        var tempoChangesCount = 0
        var timeSignatureChangesCount = 0
        var markerCount = 0

        val usedNtrs = mutableSetOf<String>()
        val usedNtts = mutableSetOf<String>()
        val unsupportedFeatures = mutableListOf<String>()

        // Analyze MIDI events
        style.sections.values.forEach { sectionData ->
            tracksCount += sectionData.tracks.size
            sectionData.tracks.values.forEach { track ->
                eventsCount += track.events.size
                
                track.events.forEach { event ->
                    when (event) {
                        is com.example.domain.models.MidiEvent.ProgramChange -> programChangesCount++
                        is com.example.domain.models.MidiEvent.ControlChange -> {
                            controlChangesCount++
                            // Check for RPN/NRPN (CC 98, 99, 100, 101)
                            if (event.controller == 98 || event.controller == 99) nrpnCount++
                            if (event.controller == 100 || event.controller == 101) rpnCount++
                        }
                        is com.example.domain.models.MidiEvent.SysEx -> sysExCount++
                        is com.example.domain.models.MidiEvent.Meta -> {
                            when (event.type) {
                                0x51 -> tempoChangesCount++ // Tempo
                                0x58 -> timeSignatureChangesCount++ // Time Signature
                                0x06 -> markerCount++ // Marker
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        // Analyze CASM
        style.casm.sections.forEach { casmSection ->
            casmSection.channels.forEach { channel ->
                usedNtrs.add(channel.ntr.displayName)
                usedNtts.add(channel.ntt.displayName)

                // Add some basic checks for unsupported features
                if (channel.ntt.name == "GUITAR" || channel.ntr.name == "GUITAR") {
                    if (!unsupportedFeatures.contains("Guitar Mode (Partial Support)")) {
                        unsupportedFeatures.add("Guitar Mode (Partial Support)")
                    }
                }
            }
        }

        // SFF1 vs SFF2
        val format = if (style.casm.sections.any { it.name.startsWith("Sdec") }) "SFF2 (GEP)" else "SFF1"

        // Base compatibility
        var compatibility = 100
        if (unsupportedFeatures.isNotEmpty()) {
            compatibility -= unsupportedFeatures.size * 5
        }
        
        // CASM Coverage
        val casmCoverage = if (style.casm.sections.isNotEmpty()) 100 else 0
        
        // OTS Coverage (Assuming OTS is not yet parsed, setting to 0)
        val otsCoverage = 0

        return CompatibilityReport(
            format = format,
            tracksCount = tracksCount,
            sectionsCount = sectionsCount,
            eventsCount = eventsCount,
            usedNtrs = usedNtrs,
            usedNtts = usedNtts,
            unsupportedFeatures = unsupportedFeatures,
            estimatedCompatibility = compatibility.coerceIn(0, 100),
            
            programChangesCount = programChangesCount,
            controlChangesCount = controlChangesCount,
            sysExCount = sysExCount,
            nrpnCount = nrpnCount,
            rpnCount = rpnCount,
            tempoChangesCount = tempoChangesCount,
            timeSignatureChangesCount = timeSignatureChangesCount,
            markerCount = markerCount,
            casmCoverage = casmCoverage,
            otsCoverage = otsCoverage
        )
    }
}
