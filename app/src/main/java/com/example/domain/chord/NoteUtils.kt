package com.example.domain.chord

object NoteUtils {
    val NOTE_NAMES = arrayOf("C (Dó)", "C# (Dó#)", "D (Ré)", "Eb (Mib)", "E (Mi)", "F (Fá)", "F# (Fá#)", "G (Sol)", "G# (Sol#)", "A (Lá)", "Bb (Sib)", "B (Si)")
    
    fun getNoteName(midiNote: Int): String {
        if (midiNote !in 0..127) return "---"
        val note = midiNote % 12
        val octave = (midiNote / 12) - 1
        return "${NOTE_NAMES[note]}$octave"
    }
}
