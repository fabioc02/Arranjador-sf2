package com.example.domain.yamaha

import com.example.domain.models.StyFile
import com.example.domain.models.StyFormatVersion

data class ValidationReport(
    val compatibilityScore: Int,
    val formatVersion: StyFormatVersion,
    val tempo: Int,
    val timeSignature: String,
    val markers: Int,
    val hasCasm: Boolean,
    val otsCount: Int,
    val megaVoiceCount: Int,
    val sysExCount: Int,
    val programChangesCount: Int,
    val controlChangesCount: Int,
    val nrpnCount: Int,
    val rpnCount: Int,
    val dspCount: Int,
    val unsupportedFeatures: List<String>,
    val warnings: List<String>,
    val suggestedFixes: Int
)

class StyleValidator {
    fun validate(style: StyFile): ValidationReport {
        var score = 100
        val unsupported = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var fixes = 0
        
        val format = style.formatVersion
        if (format == StyFormatVersion.SFF1) {
            warnings.add("SFF1 format can be converted to SFF2")
            fixes++
        }
        
        val hasCasm = style.casm.valid
        if (!hasCasm) {
            unsupported.add("CASM Missing or Invalid")
            score -= 20
        }
        
        if (style.markersTimeline.isEmpty()) {
            warnings.add("Markers timeline is empty")
            fixes++
        }
        
        var sysExCount = 0
        var programChangesCount = 0
        var controlChangesCount = 0
        val megaVoiceCount = 0 // Placeholder
        
        style.sections.values.forEach { section ->
            section.tracks.values.forEach { track ->
                // Placeholder counting
                sysExCount += 0
                programChangesCount += 0
                controlChangesCount += 0
            }
        }
        
        return ValidationReport(
            compatibilityScore = score,
            formatVersion = format,
            tempo = 120, // Placeholder
            timeSignature = "4/4", // Placeholder
            markers = style.markersTimeline.size,
            hasCasm = hasCasm,
            otsCount = 4, // Placeholder
            megaVoiceCount = megaVoiceCount,
            sysExCount = sysExCount,
            programChangesCount = programChangesCount,
            controlChangesCount = controlChangesCount,
            nrpnCount = 0,
            rpnCount = 0,
            dspCount = 0,
            unsupportedFeatures = unsupported,
            warnings = warnings,
            suggestedFixes = fixes
        )
    }
}
