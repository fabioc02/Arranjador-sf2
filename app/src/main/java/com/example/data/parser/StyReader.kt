package com.example.data.parser

import com.example.domain.models.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StyReader {
    private val headerParser = HeaderParser()
    private val trackParser = MidiTrackParser()
    private val chunkReader = ChunkReader()

    fun read(data: ByteArray, fileName: String): StyFile {
        val startTime = System.currentTimeMillis()
        val startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        val cleanData = sanitizeData(data)
        val buffer = ByteBuffer.wrap(cleanData).order(ByteOrder.BIG_ENDIAN)
        val fileSize = cleanData.size.toLong()
        var totalEvents = 0
        var maxTick = 0L
        var errors = 0
        
        return try {
            ParserLogger.logInfo("Starting parsing for $fileName (Size: $fileSize bytes)")
            
            val header = headerParser.parse(buffer)
            ParserLogger.logInfo("Header parsed: Format ${header.format}, Tracks ${header.tracks}, Division ${header.division}")
            
            val markerParser = MarkerParser()
            val trackEventsList = mutableMapOf<Int, List<MidiEvent>>()
            
            for (i in 0 until header.tracks) {
                try {
                    val events = trackParser.parseTrackEvents(buffer, i, markerParser)
                    totalEvents += events.size
                    trackEventsList[i] = events
                    val endTick = events.lastOrNull()?.tick ?: 0L
                    if (endTick > maxTick) maxTick = endTick
                } catch (e: Exception) {
                    ParserLogger.logError("Error parsing track $i", e)
                    errors++
                }
            }
            
            var casmOffset = -1L
            var casmSize = 0L
            var otsOffset = -1L
            var otsSize = 0L
            var formatVersion = StyFormatVersion.Unknown
            
            val currentPos = buffer.position()
            val chunks = try {
                chunkReader.findChunks(buffer, currentPos)
            } catch (e: Exception) {
                ParserLogger.logError("Error finding chunks", e)
                errors++
                emptyList()
            }
            
            for (chunk in chunks) {
                if (chunk.name == "CASM") {
                    casmOffset = chunk.offset
                    casmSize = chunk.size
                    formatVersion = StyFormatVersion.SFF1 
                } else if (chunk.name == "OTSc") {
                    otsOffset = chunk.offset
                    otsSize = chunk.size
                }
            }

            if (casmOffset < 0) {
                val casmIdx = indexOf(cleanData, "CASM".toByteArray(Charsets.US_ASCII))
                if (casmIdx >= 0 && casmIdx + 8 <= cleanData.size) {
                    casmOffset = casmIdx.toLong()
                    casmSize = getInt(cleanData, casmIdx + 4).toLong()
                    formatVersion = StyFormatVersion.SFF1
                }
            }
            if (otsOffset < 0) {
                val otsIdx = indexOf(cleanData, "OTSc".toByteArray(Charsets.US_ASCII))
                if (otsIdx >= 0 && otsIdx + 8 <= cleanData.size) {
                    otsOffset = otsIdx.toLong()
                    otsSize = getInt(cleanData, otsIdx + 4).toLong()
                }
            }
            
            val fileDivision = if (header.division > 0) header.division else 480
            val ppqScale = 1920.0 / fileDivision
            fun scaleTick(t: Long): Long = if (fileDivision == 1920) t else Math.round(t * ppqScale).toLong()

            // Build sections structure
            val sectionsMap = mutableMapOf<StyleSection, SectionData>()
            var markers = markerParser.markersTimeline
            
            // Fallback para arquivos MIDI/STY sem marcadores
            if (markers.isEmpty()) {
                markers = listOf(Pair(0L, "Main A")).toMutableList()
            }
            
            for (i in markers.indices) {
                val current = markers[i]
                val nextTick = if (i + 1 < markers.size) markers[i + 1].first else maxTick
                val sectionEnum = StyleSection.fromMarker(current.second)
                
                val tracksMap = mutableMapOf<Int, StyleTrack>()
                for (t in 0 until header.tracks) {
                    val allEvents = trackEventsList[t] ?: emptyList()
                    val sectionEvents = allEvents.filter { it.tick in current.first until nextTick }
                    
                    // Ajusta o tick relativo para que todas as seções comecem do tick 0 e escala para 1920 PPQ
                    val relativeEvents = sectionEvents.map { ev ->
                        val relTick = scaleTick(ev.tick - current.first)
                        when(ev) {
                            is MidiEvent.NoteOn -> ev.copy(tick = relTick)
                            is MidiEvent.NoteOff -> ev.copy(tick = relTick)
                            is MidiEvent.ControlChange -> ev.copy(tick = relTick)
                            is MidiEvent.ProgramChange -> ev.copy(tick = relTick)
                            is MidiEvent.PitchBend -> ev.copy(tick = relTick)
                            is MidiEvent.SysEx -> ev.copy(tick = relTick)
                            is MidiEvent.Meta -> ev.copy(tick = relTick)
                        }
                    }
                    
                    tracksMap[t] = StyleTrack(
                        eventCount = relativeEvents.size,
                        events = relativeEvents
                    )
                }
                
                sectionsMap[sectionEnum] = SectionData(
                    name = current.second,
                    tracks = tracksMap,
                    startTick = scaleTick(current.first),
                    endTick = scaleTick(nextTick),
                    exists = true
                )
            }
            
            val casmParser = CasmParser()
            val casmInfo = if (casmOffset >= 0) {
                casmParser.parse(buffer, casmOffset, casmSize)
            } else {
                val dummyRules = (0..15).map { ch -> 
                    com.example.domain.models.CasmChannel(
                        sourceChannel = ch, 
                        destChannel = ch, 
                        ntr = if (ch == 8 || ch == 9) com.example.domain.models.NTR.BYPASS else com.example.domain.models.NTR.ROOT_FIXED,
                        ntt = if (ch == 8 || ch == 9) com.example.domain.models.NTT.BYPASS else if (ch == 10) com.example.domain.models.NTT.BASS else com.example.domain.models.NTT.CHORD
                    )
                }
                StyleCasm(
                    valid = true,
                    sections = markers.map { m -> com.example.domain.models.CasmSection(name = m.second, channels = dummyRules) }
                )
            }
            
            // Se o CASM foi lido mas está vazio (por erro ou corrupção), injeta um dummy
            val finalCasm = if (casmInfo.sections.isEmpty()) {
                val dummyRules = (0..15).map { ch -> 
                    com.example.domain.models.CasmChannel(
                        sourceChannel = ch, 
                        destChannel = ch, 
                        ntr = if (ch == 8 || ch == 9) com.example.domain.models.NTR.BYPASS else com.example.domain.models.NTR.ROOT_FIXED,
                        ntt = if (ch == 8 || ch == 9) com.example.domain.models.NTT.BYPASS else if (ch == 10) com.example.domain.models.NTT.BASS else com.example.domain.models.NTT.CHORD
                    )
                }
                casmInfo.copy(sections = markers.map { m -> com.example.domain.models.CasmSection(name = m.second, channels = dummyRules) })
            } else {
                casmInfo
            }

            
            val endTime = System.currentTimeMillis()
            val endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val memUsedKb = ((endMem - startMem) / 1024).coerceAtLeast(0)
            
            val metrics = ParserMetrics(
                readTimeMs = endTime - startTime,
                memoryUsedKb = memUsedKb,
                totalEvents = totalEvents,
                tracksCount = header.tracks,
                markersCount = markers.size,
                chunksCount = chunks.size,
                errorsCount = errors + casmInfo.metrics.errorsCount
            )
            
            ParserLogger.logInfo("Finished parsing successfully in ${metrics.readTimeMs}ms")
            
            StyFile(
                valid = true,
                name = fileName,
                fileSize = fileSize,
                formatVersion = formatVersion,                
                defaultBpm = markerParser.tempoBpm,
                timeSignature = markerParser.timeSignature,
                ppq = 1920,
                tracksCount = header.tracks,
                casmOffset = casmOffset,
                casmSize = casmSize,
                otsOffset = otsOffset,
                otsSize = otsSize,
                markers = markerParser.markers,
                markersTimeline = markers,
                sections = sectionsMap,
                casm = finalCasm,
                metrics = metrics,
                rawData = data
            )
        } catch (e: Exception) {
            ParserLogger.logError("Failed to parse STY file", e)
            val endTime = System.currentTimeMillis()
            StyFile(
                valid = false,
                name = fileName,
                fileSize = fileSize,
                metrics = ParserMetrics(readTimeMs = endTime - startTime, errorsCount = errors + 1),
                rawData = data,
                errorMessage = e.message ?: "Unknown parsing error"
            )
        }
    }

    private fun sanitizeData(raw: ByteArray): ByteArray {
        var hasCorruption = false
        for (i in 0 until raw.size - 2) {
            if ((raw[i].toInt() and 0xFF) == 0xEF && 
                (raw[i + 1].toInt() and 0xFF) == 0xBF && 
                (raw[i + 2].toInt() and 0xFF) == 0xBD) {
                hasCorruption = true
                break
            }
        }
        if (!hasCorruption) return raw
        
        val result = mutableListOf<Byte>()
        var i = 0
        while (i < raw.size) {
            if (i + 2 < raw.size && 
                (raw[i].toInt() and 0xFF) == 0xEF && 
                (raw[i + 1].toInt() and 0xFF) == 0xBF && 
                (raw[i + 2].toInt() and 0xFF) == 0xBD) {
                result.add(0xFF.toByte())
                i += 3
            } else {
                result.add(raw[i])
                i++
            }
        }
        return result.toByteArray()
    }

    private fun indexOf(data: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || data.size < target.size) return -1
        for (i in 0..data.size - target.size) {
            var found = true
            for (j in target.indices) {
                if (data[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun getInt(data: ByteArray, offset: Int): Int {
        if (offset + 3 >= data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 24) or
               ((data[offset + 1].toInt() and 0xFF) shl 16) or
               ((data[offset + 2].toInt() and 0xFF) shl 8) or
               (data[offset + 3].toInt() and 0xFF)
    }
}
