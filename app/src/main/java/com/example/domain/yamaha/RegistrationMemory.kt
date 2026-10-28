package com.example.domain.yamaha



data class RegistrationData(
    val name: String = "Bank 1 - 1",
    val stylePath: String = "",
    val tempo: Int = 120,
    val currentOts: Int = 1,
    val transpose: Int = 0,
    val splitPoint: Int = 54, // F#2
    val right1Voice: Int = 0,
    val right2Voice: Int = 0,
    val right3Voice: Int = 0,
    val leftVoice: Int = 0,
    val right1Volume: Int = 100,
    val leftVolume: Int = 80,
    val pedals: Map<Int, Int> = emptyMap(), // Pedal functions
    val harmonyEnabled: Boolean = false,
    val dspPresetId: Int = 0
)

class RegistrationMemory {
    var currentRegistration: RegistrationData = RegistrationData()
    
    fun saveToMemory(index: Int, data: RegistrationData) {
        // In real life, serialize and persist this
        currentRegistration = data
    }
    
    fun recallFromMemory(index: Int): RegistrationData {
        // Return from persist storage
        return currentRegistration
    }
}
