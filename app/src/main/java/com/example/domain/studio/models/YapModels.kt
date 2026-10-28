package com.example.domain.studio.models

import java.util.UUID

data class YapProject(
    val id: String = UUID.randomUUID().toString(),
    val metadata: YapMetadata = YapMetadata(),
    val tempoMap: YapTempoMap = YapTempoMap(),
    val sections: MutableList<YapSection> = mutableListOf(),
    val drumKits: MutableList<String> = mutableListOf(),
    val instruments: MutableList<String> = mutableListOf()
)

data class YapMetadata(
    var name: String = "Untitled",
    var author: String = "Unknown",
    var createdAt: Long = System.currentTimeMillis()
)

data class YapTempoMap(
    var defaultBpm: Double = 120.0,
    var timeSignatureNumerator: Int = 4,
    var timeSignatureDenominator: Int = 4
)

data class YapSection(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Main A", // e.g., Main A, Fill In B
    var lengthBars: Int = 1,
    val tracks: MutableList<YapTrack> = mutableListOf()
)

data class YapTrack(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Track",
    var channel: Int = 0,
    var instrumentRef: String? = null,
    val events: MutableList<YapMidiEvent> = mutableListOf(),
    var casmRules: YapCasmRules = YapCasmRules()
)

data class YapMidiEvent(
    val id: String = UUID.randomUUID().toString(),
    var tick: Long,
    var type: Int, // 0: NoteOn, 1: NoteOff, 2: CC, 3: ProgramChange, 4: PitchBend
    var data1: Int, // Note, CC number, etc.
    var data2: Int, // Velocity, CC value, etc.
    var duration: Long = 0, // For notes
    // Extended properties
    var muted: Boolean = false,
    var probability: Float = 1.0f
)

data class YapCasmRules(
    // Extracted from original CASM for round-tripping
    var sourceChordRoot: Int = 0, // 0 = C
    var sourceChordType: Int = 34, // Major 7 usually
    var ntr: Int = 0,
    var ntt: Int = 0,
    var highKey: Int = 127,
    var noteLimitLow: Int = 0,
    var noteLimitHigh: Int = 127,
    var rtr: Int = 0
)
