package com.example.domain.yamaha

enum class DSPType {
    Hall, Room, Stage, Plate, Delay, StereoDelay, Chorus, Flanger, Rotary, Compressor, MasterEQ, Insertion, Unknown
}

data class DSPPreset(
    val id: Int,
    val name: String,
    val type: DSPType
)

class DSPPresetLoader {
    val presets = mutableListOf<DSPPreset>()
    
    init {
        presets.add(DSPPreset(1, "Concert Hall", DSPType.Hall))
        presets.add(DSPPreset(2, "Small Room", DSPType.Room))
        presets.add(DSPPreset(3, "Vocal Plate", DSPType.Plate))
        presets.add(DSPPreset(4, "Standard Chorus", DSPType.Chorus))
        presets.add(DSPPreset(5, "Vintage Rotary", DSPType.Rotary))
        presets.add(DSPPreset(6, "Master EQ Flat", DSPType.MasterEQ))
        // Many more presets can exist
    }
    
    fun loadPreset(id: Int): DSPPreset? {
        return presets.find { it.id == id }
    }
}
