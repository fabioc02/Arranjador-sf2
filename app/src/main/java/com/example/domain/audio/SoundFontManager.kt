package com.example.domain.audio

class SoundFontManager(private val synth: SynthBackend) {
    private val loadedSoundFonts = mutableMapOf<Int, String>()
    private val favorites = mutableListOf<String>()
    private val recent = mutableListOf<String>()
    
    fun loadSoundFont(path: String): Int {
        val id = synth.loadSoundFont(path)
        loadedSoundFonts[id] = path
        updateRecent(path)
        return id
    }
    
    fun unloadSoundFont(id: Int) {
        synth.unloadSoundFont(id)
        loadedSoundFonts.remove(id)
    }
    
    private fun updateRecent(path: String) {
        recent.remove(path)
        recent.add(0, path)
        if (recent.size > 10) {
            recent.removeAt(recent.size - 1)
        }
    }
}
