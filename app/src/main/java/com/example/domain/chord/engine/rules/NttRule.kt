package com.example.domain.chord.engine.rules

import com.example.domain.models.Chord
import com.example.domain.models.CasmChannel

interface NttRule {
    fun apply(note: Int, sourceChord: Chord, targetChord: Chord): Int
    val ruleName: String
}

class NttBypassRule : NttRule {
    override fun apply(note: Int, sourceChord: Chord, targetChord: Chord): Int {
        return note
    }
    override val ruleName = "Bypass"
}

// PROVISIONAL
class NttChordRule : NttRule {
    override fun apply(note: Int, sourceChord: Chord, targetChord: Chord): Int {
        // Provisional: we don't apply the chord clamping yet to avoid Legacy issues,
        // or we just return the note (which was already shifted by NTR).
        // This will be replaced by the authentic Yamaha tables later.
        return note 
    }
    override val ruleName = "Chord (Provisional)"
}

// PROVISIONAL
class NttDorianRule : NttRule {
    override fun apply(note: Int, sourceChord: Chord, targetChord: Chord): Int {
        return note
    }
    override val ruleName = "Dorian (Provisional)"
}

// PROVISIONAL
class NttPentatonicRule : NttRule {
    override fun apply(note: Int, sourceChord: Chord, targetChord: Chord): Int {
        return note
    }
    override val ruleName = "Pentatonic (Provisional)"
}

// PROVISIONAL
class NttBassRule : NttRule {
    override fun apply(note: Int, sourceChord: Chord, targetChord: Chord): Int {
        return note
    }
    override val ruleName = "Bass (Provisional)"
}
