package com.example.domain.validation

import com.example.domain.models.StyFile

data class YamahaModel(
    val name: String,
    val supportedFeatures: List<String>,
    val unsupportedFeatures: List<String>
)

data class StyleCompatibilityReport(
    val modelName: String,
    val compatibilityPercentage: Int,
    val unsupportedFeaturesFound: List<String>
)

class CompatibilityDatabase {
    val models = listOf(
        YamahaModel("PSR-3000", listOf("SFF1", "MegaVoice", "XG", "SysEx"), listOf("SFF2", "S.Art2")),
        YamahaModel("PSR-S710", listOf("SFF1", "SFF2", "MegaVoice", "XG"), listOf("S.Art2")),
        YamahaModel("PSR-S910", listOf("SFF1", "SFF2", "MegaVoice", "S.Art", "XG"), listOf("S.Art2")),
        YamahaModel("PSR-S970", listOf("SFF1", "SFF2", "MegaVoice", "S.Art", "XG", "AudioStyle"), listOf("S.Art2")),
        YamahaModel("PSR-SX600", listOf("SFF1", "SFF2", "MegaVoice", "XG"), listOf("S.Art2", "RevoDrums")),
        YamahaModel("PSR-SX700", listOf("SFF1", "SFF2", "MegaVoice", "S.Art", "XG"), listOf("S.Art2", "RevoDrums")),
        YamahaModel("PSR-SX900", listOf("SFF1", "SFF2", "MegaVoice", "S.Art", "XG"), listOf("S.Art2", "RevoDrums")),
        YamahaModel("Genos", listOf("SFF1", "SFF2", "MegaVoice", "S.Art", "S.Art2", "RevoDrums", "XG"), listOf()),
        YamahaModel("Genos 2", listOf("SFF1", "SFF2", "MegaVoice", "S.Art", "S.Art2", "RevoDrums", "DX7", "XG"), listOf())
    )

    fun checkCompatibility(style: StyFile, model: YamahaModel): StyleCompatibilityReport {
        // Placeholder check
        val unsupportedFound = mutableListOf<String>()
        var score = 100
        
        if (style.formatVersion.name == "SFF2" && !model.supportedFeatures.contains("SFF2")) {
            unsupportedFound.add("SFF2")
            score -= 50
        }
        
        return StyleCompatibilityReport(
            modelName = model.name,
            compatibilityPercentage = score,
            unsupportedFeaturesFound = unsupportedFound
        )
    }
}
