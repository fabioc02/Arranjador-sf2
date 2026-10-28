package com.example.data.parser

import com.example.data.parser.models.*
import com.example.domain.models.*
import java.nio.ByteBuffer

class CasmParser {

    fun parse(buffer: ByteBuffer, offset: Long, size: Long): StyleCasm {
        val warnings = mutableListOf<String>()
        val sections = mutableListOf<CasmSection>()
        val rawCsegList = mutableListOf<RawCsegDump>()
        var errorsCount = 0
        val casmEnd = (offset + size).toInt()

        try {
            buffer.position(offset.toInt())
            val magic = ByteArray(4)
            if (buffer.remaining() >= 4) {
                buffer.get(magic)
                if (!magic.contentEquals("CASM".toByteArray())) {
                    return StyleCasm(valid = false, offset = offset, size = size, warnings = listOf("CASM magic not found"))
                }
            } else {
                return StyleCasm(valid = false, offset = offset, size = size, warnings = listOf("CASM chunk too small"))
            }

            if (buffer.remaining() >= 4) {
                val chunkLen = buffer.int
            }

            // Converter para ByteArray para lidar com a corrupção UTF-8 logicamente se for o caso antigo,
            // mas primariamente apenas ler os bytes puros como estão, tratando EF BF BD como 1 byte
            val rawCasmData = ByteArray(size.toInt())
            buffer.get(rawCasmData)
            
            // Corrige a leitura lógica reconstruindo os bytes, eliminando EF BF BD
            val logicalBytes = mutableListOf<Byte>()
            var i = 0
            while (i < rawCasmData.size) {
                if (i + 2 < rawCasmData.size && 
                    rawCasmData[i].toUByte().toInt() == 0xEF && 
                    rawCasmData[i+1].toUByte().toInt() == 0xBF && 
                    rawCasmData[i+2].toUByte().toInt() == 0xBD) {
                    logicalBytes.add(0xFF.toByte()) 
                    i += 3
                } else {
                    logicalBytes.add(rawCasmData[i])
                    i++
                }
            }
            
            val casmData = logicalBytes.toByteArray()
            
            var csegIdx = 0
            while (csegIdx < casmData.size - 8) {
                if (casmData[csegIdx] == 'C'.code.toByte() && casmData[csegIdx+1] == 'S'.code.toByte() &&
                    casmData[csegIdx+2] == 'E'.code.toByte() && casmData[csegIdx+3] == 'G'.code.toByte()) {
                    
                    val csegSize = getInt(casmData, csegIdx + 4)
                    val csegEndIdx = minOf(csegIdx + 8 + csegSize, casmData.size)
                    val csegData = casmData.copyOfRange(csegIdx + 8, csegEndIdx)
                    
                    var sdecIdx = 0
                    var sectionNames = mutableListOf<String>()
                    while (sdecIdx < csegData.size - 8) {
                         if (csegData[sdecIdx] == 'S'.code.toByte() && csegData[sdecIdx+1] == 'd'.code.toByte() &&
                            csegData[sdecIdx+2] == 'e'.code.toByte() && csegData[sdecIdx+3] == 'c'.code.toByte()) {
                            val sdecSize = getInt(csegData, sdecIdx + 4)
                            val sdecEnd = minOf(sdecIdx + 8 + sdecSize, csegData.size)
                            val sdecPayload = csegData.copyOfRange(sdecIdx + 8, sdecEnd)
                            val str = String(sdecPayload).trimEnd('\u0000')
                            sectionNames.addAll(str.split(","))
                            break
                        }
                        sdecIdx++
                    }
                    var sectionList = sectionNames.map { it.trim() }
                    if (sectionList.isEmpty()) {
                        sectionList = listOf("ALL") // Fallback for SFF1 styles without Sdec
                    }

                    val ctb2List = mutableListOf<CasmChannel>()
                    val rawCtb2List = mutableListOf<RawCtb2Dump>()
                    
                    var ctb2Idx = 0
                    while (ctb2Idx < csegData.size - 8) {
                        val isCtb2 = csegData[ctb2Idx] == 'C'.code.toByte() && csegData[ctb2Idx+1] == 't'.code.toByte() &&
                                     csegData[ctb2Idx+2] == 'b'.code.toByte() && csegData[ctb2Idx+3] == '2'.code.toByte()
                        val isCtab = csegData[ctb2Idx] == 'C'.code.toByte() && csegData[ctb2Idx+1] == 't'.code.toByte() &&
                                     csegData[ctb2Idx+2] == 'a'.code.toByte() && csegData[ctb2Idx+3] == 'b'.code.toByte()
                        
                        if (isCtb2 || isCtab) {
                            val chunkSize = getInt(csegData, ctb2Idx + 4)
                            val endIdx = minOf(ctb2Idx + 8 + chunkSize, csegData.size)
                            val payload = csegData.copyOfRange(ctb2Idx + 8, endIdx)
                            
                            if (payload.size >= 10) {
                                val srcCh = payload[0].toUByte().toInt()
                                val partNameBytes = payload.copyOfRange(1, minOf(9, payload.size))
                                val partName = String(partNameBytes).trimEnd('\u0000', ' ').trim()
                                val destCh = if (payload.size > 9) payload[9].toUByte().toInt() else srcCh
                                
                                val ntr: Int
                                val ntt: Int
                                val highKey: Int
                                val noteLowLimit: Int
                                val noteHighLimit: Int
                                val rtr: Int
                                val sourceRoot: Int
                                val sourceChord: Int

                                val isBassOn: Boolean
                                if (isCtb2) {
                                    // SFF1 / SFF GE Format 2 (Ctb2 chunk, 47 bytes payload):
                                    // byte 0: srcChannel, bytes 1..8: partName, byte 9: destChannel, byte 10: editable
                                    // bytes 11..12: noteMute, bytes 13..17: chordMute
                                    // byte 18: sourceChordRoot (0..11)
                                    // byte 19: sourceChordType (0..33)
                                    // byte 20: lowMidLimit, byte 21: midHighLimit
                                    // byte 22: ntr (0=Root Trans, 1=Root Fixed, 2=Guitar)
                                    // byte 23: ntt (0..11 or 0x80..0x8B with bit 7 = Bass On)
                                    // byte 24: highKey (0..11, e.g. 3 for Bass = Eb, 7 for Chords = G)
                                    // byte 25: noteLowLimit (e.g. 28 for Bass = E0, 0 for Chords)
                                    // byte 26: noteHighLimit (e.g. 127, 79, 52)
                                    // byte 27: rtr (Retrigger Rule)
                                    sourceRoot = if (payload.size > 18) payload[18].toUByte().toInt() else 0
                                    sourceChord = if (payload.size > 19) payload[19].toUByte().toInt() else 2
                                    ntr = if (payload.size > 22) payload[22].toUByte().toInt() else 0
                                    val rawNtt = if (payload.size > 23) payload[23].toUByte().toInt() else (if (destCh in 8..9) 0 else if (destCh == 10) 0x81 else 2)
                                    ntt = rawNtt
                                    isBassOn = (rawNtt and 0x80) != 0
                                    highKey = if (payload.size > 24) payload[24].toUByte().toInt() else (if (destCh == 10) 3 else 7)
                                    noteLowLimit = if (payload.size > 25) payload[25].toUByte().toInt() else (if (destCh == 10) 28 else 0)
                                    noteHighLimit = if (payload.size > 26) payload[26].toUByte().toInt() else 127
                                    rtr = if (payload.size > 27) payload[27].toUByte().toInt() else 0
                                } else {
                                    // Classical SFF1 Ctab format (Format 1):
                                    // byte 0: srcChannel, bytes 1..8: partName, byte 9: destChannel, byte 10: editable
                                    // bytes 11..12: noteMute, bytes 13..17: chordMute
                                    // byte 18: sourceChordRoot, byte 19: sourceChordType
                                    // byte 20: ntr, byte 21: ntt, byte 22: highKey, byte 23: noteLowLimit, byte 24: noteHighLimit, byte 25: rtr
                                    sourceRoot = if (payload.size > 18) payload[18].toUByte().toInt() else 0
                                    sourceChord = if (payload.size > 19) payload[19].toUByte().toInt() else 2
                                    ntr = if (payload.size > 20) payload[20].toUByte().toInt() else 0
                                    val rawNtt = if (payload.size > 21) payload[21].toUByte().toInt() else (if (destCh in 8..9) 0 else if (destCh == 10) 3 else 2)
                                    ntt = rawNtt
                                    isBassOn = (rawNtt and 0x80) != 0
                                    highKey = if (payload.size > 22) payload[22].toUByte().toInt() else 6
                                    noteLowLimit = if (payload.size > 23) payload[23].toUByte().toInt() else 0
                                    noteHighLimit = if (payload.size > 24) payload[24].toUByte().toInt() else 127
                                    rtr = if (payload.size > 25) payload[25].toUByte().toInt() else 0
                                }
                                val lowKey = 0

                                val chordTypeNames = arrayOf(
                                    "Maj", "Maj6", "Maj7", "Maj7(#11)", "Maj(9)", "Maj7(9)", "Maj6(9)", "Aug",
                                    "Min", "Min6", "Min7", "Min7b5", "Min(9)", "Min7(9)", "Min7(11)", "MinMaj7", "MinMaj7(9)",
                                    "Dim", "Dim7", "7th", "7sus4", "7b5", "7(9)", "7(#11)", "7(13)", "7(b9)", "7(b13)", "7(#9)",
                                    "Maj7aug", "7aug", "1+8", "1+5", "Sus4", "1+2+5"
                                )
                                val rootNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                                
                                ctb2List.add(
                                    CasmChannel(
                                        sourceChannel = srcCh,
                                        destChannel = destCh,
                                        sourceChordRoot = if (sourceRoot in rootNames.indices) rootNames[sourceRoot] else "C",
                                        sourceChordType = if (sourceChord in chordTypeNames.indices) chordTypeNames[sourceChord] else "Maj7",
                                        ntr = NTR.fromId(ntr),
                                        ntt = NTT.fromId(ntt),
                                        highKey = highKey,
                                        lowKey = lowKey,
                                        noteLowLimit = noteLowLimit,
                                        noteHighLimit = noteHighLimit,
                                        retriggerRule = RTR.fromId(rtr),
                                        partName = partName,
                                        isBassOn = isBassOn
                                    )
                                )
                                
                                rawCtb2List.add(
                                    RawCtb2Dump(
                                        offset = (ctb2Idx + 8).toLong(),
                                        size = chunkSize.toLong(),
                                        sourceChannel = srcCh,
                                        destChannel = destCh,
                                        partName = partName,
                                        ntr = ntr,
                                        ntt = ntt,
                                        rawBytesHex = payload.joinToString(" ") { "%02X".format(it) },
                                        unknownFieldsHex = ""
                                    )
                                )
                            }
                            ctb2Idx += 8 + chunkSize
                        } else {
                            ctb2Idx++
                        }
                    }
                    
                    rawCsegList.add(RawCsegDump((csegIdx + 8).toLong(), csegSize.toLong(), null, rawCtb2List))
                    for (secName in sectionList) {
                        sections.add(
                            CasmSection(
                                name = secName,
                                channels = ctb2List
                            )
                        )
                    }
                    
                    csegIdx += 8 + csegSize
                } else {
                    csegIdx++
                }
            }

            val rulesCount = sections.sumOf { it.channels.size }
            return StyleCasm(
                valid = true,
                offset = offset,
                size = size,
                sections = sections,
                metrics = CasmMetrics(
                    sectionsCount = sections.size,
                    channelsCount = rulesCount,
                    rulesCount = rulesCount,
                    unknownCount = 0,
                    warningsCount = warnings.size,
                    errorsCount = errorsCount
                ),
                warnings = warnings,
                rawDump = RawCasmDump(offset = offset, size = size, csegList = rawCsegList, warnings = warnings)
            )

        } catch (e: Exception) {
             ParserLogger.logError("Error parsing CASM", e)
             return StyleCasm(
                 valid = false, 
                 offset = offset, 
                 size = size, 
                 metrics = CasmMetrics(errorsCount = 1),
                 warnings = listOf("Error parsing CASM: ${e.message}")
             )
        }
    }
    
    private fun getInt(data: ByteArray, offset: Int): Int {
        if (offset + 3 >= data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 24) or
               ((data[offset+1].toInt() and 0xFF) shl 16) or
               ((data[offset+2].toInt() and 0xFF) shl 8) or
               (data[offset+3].toInt() and 0xFF)
    }
}
