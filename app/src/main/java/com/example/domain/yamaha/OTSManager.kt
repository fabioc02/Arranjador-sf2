package com.example.domain.yamaha

data class OTSSection(
    val right1Voice: Int = 0,
    val right2Voice: Int = 0,
    val right3Voice: Int = 0,
    val leftVoice: Int = 0,
    val harmonyEnabled: Boolean = false,
    val tempo: Int = 120,
    val transpose: Int = 0,
    val dspEnabled: Boolean = true,
    val right1Volume: Int = 100,
    val leftVolume: Int = 80
)

class OTSManager {
    val otsList = mutableMapOf<Int, OTSSection>() // OTS 1, 2, 3, 4
    
    fun loadOTS(index: Int): OTSSection? {
        return otsList[index]
    }
    
    fun setOTS(index: Int, section: OTSSection) {
        otsList[index] = section
    }
}
