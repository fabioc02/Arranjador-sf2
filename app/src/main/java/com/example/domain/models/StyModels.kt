package com.example.domain.models

enum class StyFormatVersion { SFF1, SFF2, Unknown }

data class ParserMetrics(
    val readTimeMs: Long = 0,
    val memoryUsedKb: Long = 0,
    val totalEvents: Int = 0,
    val tracksCount: Int = 0,
    val markersCount: Int = 0,
    val chunksCount: Int = 0,
    val errorsCount: Int = 0
)

data class StyleTrack(
    val eventCount: Int = 0,
    val events: List<MidiEvent> = emptyList()
)

data class SectionData(
    val name: String = "",
    val tracks: Map<Int, StyleTrack> = emptyMap(),
    val startTick: Long = 0,
    val endTick: Long = 0,
    val exists: Boolean = false
)

data class StyFile(
    val valid: Boolean = false,
    val name: String = "",
    val filePath: String = "",
    val fileSize: Long = 0,
    val formatVersion: StyFormatVersion = StyFormatVersion.Unknown,
    val defaultBpm: Double = 120.0,
    val timeSignature: String = "4/4",
    val ppq: Int = 480,
    val tracksCount: Int = 0,
    val casmOffset: Long = -1,
    val casmSize: Long = 0,
    val otsOffset: Long = -1,
    val otsSize: Long = 0,
    val markers: List<String> = emptyList(),
    val markersTimeline: List<Pair<Long, String>> = emptyList(),
    val sections: Map<StyleSection, SectionData> = emptyMap(),
    val casm: StyleCasm = StyleCasm(),
    val metrics: ParserMetrics = ParserMetrics(),
    val rawData: ByteArray = ByteArray(0),
    val errorMessage: String? = null
) {
    fun hasSection(section: StyleSection): Boolean {
        return sections[section]?.exists == true
    }
}
