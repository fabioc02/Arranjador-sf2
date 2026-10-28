package com.example.domain.engine

import com.example.domain.models.StyFile
import com.example.domain.models.StyleSection
import com.example.domain.models.Chord
import kotlinx.coroutines.flow.StateFlow

interface StyleEngine {
    val currentStyle: StateFlow<StyFile?>
    val currentSection: StateFlow<StyleSection>
    val currentChord: StateFlow<Chord?>
    val isPlaying: StateFlow<Boolean>
    
    suspend fun loadStyle(path: String): Result<StyFile>
    fun play()
    fun stop()
    fun setSection(section: StyleSection)
    fun updateChord(chord: Chord)
}
