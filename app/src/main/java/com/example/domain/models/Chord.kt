package com.example.domain.models

enum class ChordType(val displayName: String) {
    Major("M"),
    Minor("m"),
    Major7("M7"),
    Minor7("m7"),
    Dominant7("7"),
    Minor7b5("m7b5"),
    Diminished("dim"),
    Augmented("aug"),
    Sus2("sus2"),
    Sus4("sus4"),
    Major6("6"),
    Major9("9"),
    Major11("11"),
    Major13("13"),
    Add9("add9")
}

data class Chord(
    val rootKey: Int, // 0 = C, 1 = C#, etc.
    val type: ChordType,
    val bassKey: Int = rootKey
) {
    val displayName: String
        get() {
            val rootName = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")[rootKey % 12]
            val bassName = if (bassKey != rootKey) {
                "/" + arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")[bassKey % 12]
            } else {
                ""
            }
            return "$rootName${type.displayName}$bassName"
        }
}
