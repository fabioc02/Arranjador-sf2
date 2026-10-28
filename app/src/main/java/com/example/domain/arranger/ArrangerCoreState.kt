package com.example.domain.arranger

import com.example.domain.models.StyleSection
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.StyFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * CORE STATE
 * 100% Kotlin Puro. Nenhum MutableState do Compose aqui.
 * Apenas leitura e gravação Thread-Safe (Atomic e Volatile).
 */
class ArrangerCoreState {
    
    // Transporte e Relógio
    val isPlaying = AtomicBoolean(false)
    val isPaused = AtomicBoolean(false)
    
    @Volatile var bpm: Float = 120f
    val currentTick = AtomicInteger(0)
    
    // Seção Atual
    val currentSection = AtomicReference<StyleSection>(StyleSection.MainA)
    val nextSection = AtomicReference<StyleSection?>(null) // Para agendar viradas
    
    // Música e Estrutura
    @Volatile var timeSigNumerator: Int = 4
    @Volatile var timeSigDenominator: Int = 4
    val loadedStyle = AtomicReference<StyFile?>(null)
    
    // Acorde e Baixo
    val currentChord = AtomicReference<Chord>(Chord(0, ChordType.Major)) // C Major default
    @Volatile var currentBassNote: Int = 0 // Raiz original do baixo (Slash chord)
    
    // LEFT / LEFT HOLD
    @Volatile var splitPoint: Int = 54 // F#2
    val leftHoldEnabled = AtomicBoolean(false)
    
    // Metrônomo
    val metronomeEnabled = AtomicBoolean(false)
    
    // Active Notes (Para evitar Note On preso)
    // Chave: Canal, Valor: Lista de Notas (Note, OffTick)
    val activePlayingNotes = ConcurrentHashMap<Int, MutableList<PlayingNote>>()
    
    data class PlayingNote(val note: Int, val velocity: Int, val absoluteOffTick: Long)
    
    fun resetTick() {
        currentTick.set(0)
    }
}
