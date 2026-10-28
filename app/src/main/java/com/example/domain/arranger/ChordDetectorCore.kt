package com.example.domain.arranger

import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import java.util.concurrent.ConcurrentSkipListSet

/**
 * CHORD DETECTOR CORE
 * Motor determinístico de reconhecimento de acordes e Slash Chords (baixo invertido).
 * Não depende do Compose. 
 */
class ChordDetectorCore(private val state: ArrangerCoreState) {

    // Notas físicas pressionadas no momento (abaixo do split)
    private val pressedKeys = ConcurrentSkipListSet<Int>()
    
    // Notas retidas (usadas para calcular o acorde atual, útil no LEFT HOLD)
    private val heldKeys = ConcurrentSkipListSet<Int>()

    fun processNoteOn(note: Int, velocity: Int, channel: Int = 0) {
        val isLeftHand = channel == 2 || channel == 3 || note <= state.splitPoint
        if (isLeftHand) {
            if (velocity > 0) {
                pressedKeys.add(note)
                if (state.leftHoldEnabled.get()) {
                    // Quando bate notas novas, se a mão estava vazia, limpa as held keys antigas
                    // Isso pode precisar de lógica adicional (ex: timer ou threshold de acordes novos)
                    // Por enquanto: adiciona à coleção.
                    heldKeys.add(note) 
                } else {
                    heldKeys.clear()
                    heldKeys.addAll(pressedKeys)
                }
                evaluateChord()
            } else {
                processNoteOff(note)
            }
        }
    }

    fun processNoteOff(note: Int, channel: Int = 0) {
        val isLeftHand = channel == 2 || channel == 3 || note <= state.splitPoint
        if (isLeftHand) {
            pressedKeys.remove(note)
            if (!state.leftHoldEnabled.get()) {
                heldKeys.remove(note)
                if (heldKeys.isEmpty()) {
                    // Se não for LEFT HOLD e soltou tudo, pode manter o último acorde ou silenciar.
                    // O Yamaha geralmente mantém o acorde tocando o acompanhamento.
                } else {
                    // DO NOT evaluate chord on Note Off! This prevents the chord from changing 
                    // to a wrong chord just because the user lifted their fingers unevenly.
                    // evaluateChord()
                }
            } else if (pressedKeys.isEmpty()) {
                // No LEFT HOLD, as teclas soltas permanecem no heldKeys e o acorde é mantido.
            }
        }
    }
    
    fun clearLeftHold() {
        if (!state.leftHoldEnabled.get()) {
            heldKeys.clear()
            heldKeys.addAll(pressedKeys)
            evaluateChord()
        }
    }

    private val analyzer = com.example.domain.chord.ChordAnalyzer()

    private fun evaluateChord() {
        val keys = heldKeys.toList()
        if (keys.isEmpty()) return

        // 1. Identificar a nota mais grave (Baixo / Bass)
        val bassNote = keys.first() % 12
        state.currentBassNote = bassNote

        // 2. Normalizar as notas para uma oitava só
        val normalizedNotes = keys.map { it % 12 }.distinct().sorted()

        // 3. Detecção com preservação de inversões (bassKey)
        val chord = detectChordType(keys, normalizedNotes, bassNote)
        if (chord != null) {
            state.currentChord.set(chord)
        }
    }

    private fun detectChordType(rawKeys: List<Int>, notes: List<Int>, bass: Int): Chord? {
        if (notes.isEmpty()) return null
        
        val analyzed = analyzer.detectChord(rawKeys)
        if (analyzed != null) {
            return analyzed
        }
        
        // Fallback garantindo que bassKey seja preservado
        for (root in 0..11) {
            val intervals = notes.map { (it - root + 12) % 12 }.toSet()
            if (intervals.containsAll(listOf(0, 4, 7))) {
                return Chord(root, ChordType.Major, bassKey = bass)
            }
            if (intervals.containsAll(listOf(0, 3, 7))) {
                return Chord(root, ChordType.Minor, bassKey = bass)
            }
        }
        
        return Chord(notes.first(), ChordType.Major, bassKey = bass)
    }
}
