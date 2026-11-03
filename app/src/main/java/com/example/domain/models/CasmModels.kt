package com.example.domain.models

enum class NTR(val id: Int, val displayName: String) {
    ROOT_TRANS(0, "Root Trans"),
    ROOT_FIXED(1, "Root Fixed"),
    GUITAR(2, "Guitar"),
    BASS(3, "Bass"),
    BYPASS(4, "Bypass"),
    UNKNOWN(-1, "Unknown");

    companion object {
        fun fromId(id: Int): NTR = when (id) {
            0 -> ROOT_TRANS
            1 -> ROOT_FIXED
            2 -> GUITAR
            3 -> BASS
            4 -> BYPASS
            else -> UNKNOWN
        }
    }
}

enum class NTT(val id: Int, val displayName: String) {
    BYPASS(0, "Bypass"),
    MELODY(1, "Melody"),
    CHORD(2, "Chord"),
    BASS(3, "Bass"),
    MELODIC_MINOR(4, "Melodic Minor"),
    HARMONIC_MINOR(5, "Harmonic Minor"),
    HARMONIC_MINOR_5TH(6, "Harmonic Minor 5th"),
    NATURAL_MINOR(7, "Natural Minor"),
    NATURAL_MINOR_5TH(8, "Natural Minor 5th (Bass Table)"),
    DORIAN(9, "Dorian"),
    DORIAN_5TH(10, "Dorian 5th"),
    PENTATONIC(11, "Pentatonic"),
    UNKNOWN(-1, "Unknown");

    companion object {
        fun fromId(id: Int): NTT {
            // Bit 7 (0x80) is the Yamaha Bass On flag in CASM Ctb2/Cntt
            val baseId = id and 0x7F
            return when (baseId) {
                0 -> BYPASS
                1 -> MELODY
                2 -> CHORD
                3 -> BASS
                4 -> MELODIC_MINOR
                5 -> HARMONIC_MINOR
                6 -> HARMONIC_MINOR_5TH
                7 -> NATURAL_MINOR
                8 -> NATURAL_MINOR_5TH // Official Yamaha Bass Table (0x08 / 0x88)
                9 -> DORIAN
                10 -> DORIAN_5TH
                11 -> PENTATONIC
                else -> if (baseId in 12..127) CHORD else UNKNOWN
            }
        }
    }
}

enum class RTR(val id: Int, val displayName: String) {
    STOP(0, "Stop"),
    PITCH_SHIFT(1, "Pitch Shift"),
    PITCH_SHIFT_TO_ROOT(2, "Pitch Shift to Root"),
    RETRIGGER(3, "Retrigger"),
    RETRIGGER_TO_ROOT(4, "Retrigger to Root"),
    NOTE_GENERATOR(5, "Note Generator");

    companion object {
        fun fromId(id: Int): RTR = when(id) {
            0 -> STOP
            1 -> PITCH_SHIFT
            2 -> PITCH_SHIFT_TO_ROOT
            3 -> RETRIGGER
            4 -> RETRIGGER_TO_ROOT
            5 -> NOTE_GENERATOR
            else -> STOP
        }
    }
}

data class CasmChannel(
    val sourceChannel: Int = 0,
    val destChannel: Int = 0,
    val sourceChordRoot: String = "C",
    val sourceChordType: String = "Maj7",
    val ntr: NTR = NTR.BYPASS,
    val ntt: NTT = NTT.BYPASS,
    val highKey: Int = 6,
    val lowKey: Int = 0,
    val noteLowLimit: Int = 0,
    val noteHighLimit: Int = 127,
    val velocityLowLimit: Int = 1,
    val velocityHighLimit: Int = 127,
    val retriggerRule: RTR = RTR.STOP,
    val muteFlags: Int = 0,
    val partName: String = "",
    val isBassOn: Boolean = false
)

data class CasmSection(
    val name: String = "Main",
    val channels: List<CasmChannel> = emptyList()
) {
    fun findChannel(channel: Int): CasmChannel? {
        return channels.find { it.sourceChannel == channel }
            ?: channels.find { it.destChannel == channel }
    }
}

data class CasmMetrics(
    val sectionsCount: Int = 0,
    val channelsCount: Int = 0,
    val rulesCount: Int = 0,
    val unknownCount: Int = 0,
    val warningsCount: Int = 0,
    val errorsCount: Int = 0
)

data class StyleCasm(
    val valid: Boolean = false,
    val offset: Long = -1,
    val size: Long = 0,
    val sections: List<CasmSection> = emptyList(),
    val metrics: CasmMetrics = CasmMetrics(),
    val warnings: List<String> = emptyList(),
    val rawDump: com.example.data.parser.models.RawCasmDump? = null
) {
    fun findSection(sectionName: String): CasmSection? {
        val clean = sectionName.trim()
        // 1. Exact match
        sections.find { it.name.trim().equals(clean, ignoreCase = true) }?.let { return it }
        // 2. Space-insensitive match ("Main A" vs "MainA")
        val noSpace = clean.replace(" ", "")
        sections.find { it.name.replace(" ", "").equals(noSpace, ignoreCase = true) }?.let { return it }
        // 3. Yamaha Break <-> Fill In BA alias
        if (clean.equals("Break", ignoreCase = true)) {
            sections.find { it.name.trim().equals("Fill In BA", ignoreCase = true) }?.let { return it }
        }
        if (clean.equals("Fill In BA", ignoreCase = true)) {
            sections.find { it.name.trim().equals("Break", ignoreCase = true) }?.let { return it }
        }
        // 4. Prefix match
        sections.find { it.name.startsWith(clean, ignoreCase = true) || clean.startsWith(it.name, ignoreCase = true) }?.let { return it }
        return sections.firstOrNull()
    }
}
