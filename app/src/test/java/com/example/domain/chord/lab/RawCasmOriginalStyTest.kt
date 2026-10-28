package com.example.domain.chord.lab

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files

class RawCasmOriginalStyTest {

    @Test
    fun analyzeOriginalStyles() {
        val dir = File("../testdata/styles_original/")
        val files = dir.listFiles { f -> f.extension.equals("sty", true) || f.extension.matches(Regex("S\\d+")) } ?: return
        
        val outFile = File("docs/reports/PHASE_3H_RAW_DUMP.txt")
        outFile.parentFile.mkdirs()
        val out = outFile.bufferedWriter()
        
        var totalStyles = files.size
        var totalCasm = 0
        var totalCtb2 = 0
        var totalChannels = 0
        var totalNtt0A = 0
        
        val ntrMap = mutableMapOf<Int, Int>()
        val nttMap = mutableMapOf<Int, Int>()
        val highKeyMap = mutableMapOf<Int, Int>()
        
        val ntt0AInstances = mutableListOf<String>()
        val highKeyInstances = mutableListOf<String>()
        
        for (file in files) {
            val bytes = Files.readAllBytes(file.toPath())
            var casmIdx = indexOf(bytes, "CASM".toByteArray())
            if (casmIdx == -1) continue
            
            totalCasm++
            
            val casmSize = getInt(bytes, casmIdx + 4)
            val casmEnd = minOf(casmIdx + 8 + casmSize, bytes.size)
            val casmData = bytes.copyOfRange(casmIdx + 8, casmEnd)
            
            var csegIdx = 0
            while (csegIdx < casmData.size - 8) {
                if (casmData[csegIdx] == 'C'.code.toByte() && casmData[csegIdx+1] == 'S'.code.toByte() &&
                    casmData[csegIdx+2] == 'E'.code.toByte() && casmData[csegIdx+3] == 'G'.code.toByte()) {
                    
                    val csegSize = getInt(casmData, csegIdx + 4)
                    val csegEndIdx = minOf(csegIdx + 8 + csegSize, casmData.size)
                    val csegData = casmData.copyOfRange(csegIdx + 8, csegEndIdx)
                    
                    // Encontrar o Sdec para pegar o nome da section, se possível
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
                    val sectionStr = if (sectionNames.isNotEmpty()) sectionNames.joinToString(",") else "UNKNOWN"

                    var ctb2Idx = 0
                    while (ctb2Idx < csegData.size - 8) {
                        if (csegData[ctb2Idx] == 'C'.code.toByte() && csegData[ctb2Idx+1] == 't'.code.toByte() &&
                            csegData[ctb2Idx+2] == 'b'.code.toByte() && csegData[ctb2Idx+3] == '2'.code.toByte()) {
                            
                            totalCtb2++
                            val ctb2Size = getInt(csegData, ctb2Idx + 4)
                            val endIdx = minOf(ctb2Idx + 8 + ctb2Size, csegData.size)
                            val payload = csegData.copyOfRange(ctb2Idx + 8, endIdx)
                            
                            if (payload.size >= 23) {
                                val srcCh = payload[0].toUByte().toInt()
                                val destCh = payload[9].toUByte().toInt()
                                val ntr = payload[18].toUByte().toInt()
                                val ntt = payload[19].toUByte().toInt()
                                val highKey = payload[20].toUByte().toInt()
                                val low = payload[21].toUByte().toInt()
                                val high = payload[22].toUByte().toInt()
                                
                                val partNameBytes = payload.copyOfRange(1, 9)
                                val partName = String(partNameBytes).trim()
                                
                                totalChannels++
                                ntrMap[ntr] = (ntrMap[ntr] ?: 0) + 1
                                nttMap[ntt] = (nttMap[ntt] ?: 0) + 1
                                highKeyMap[highKey] = (highKeyMap[highKey] ?: 0) + 1
                                
                                val dumpLine = "STY: ${file.name} | SEC: $sectionStr | PART: $partName | SRC: $srcCh | DEST: $destCh | NTR: %02X | NTT: %02X | HK: %02X | LOW: $low | HIGH: $high\n".format(ntr, ntt, highKey)
                                out.write(dumpLine)
                                
                                if (ntt == 0x0A) {
                                    totalNtt0A++
                                    ntt0AInstances.add(dumpLine)
                                }
                                if (highKey != 0) {
                                    highKeyInstances.add(dumpLine)
                                }
                            }
                            ctb2Idx += 8 + ctb2Size
                        } else {
                            ctb2Idx++
                        }
                    }
                    csegIdx += 8 + csegSize
                } else {
                    csegIdx++
                }
            }
        }
        
        out.write("\n==================================================\n")
        out.write("SUMMARY\n")
        out.write("TOTAL_STY_FILES = $totalStyles\n")
        out.write("TOTAL_CASM = $totalCasm\n")
        out.write("TOTAL_CTB2 = $totalCtb2\n")
        out.write("TOTAL_CHANNELS = $totalChannels\n")
        out.write("TOTAL_NTT_0A = $totalNtt0A\n")
        
        out.write("\nNTR DISTRIBUTION:\n")
        ntrMap.entries.sortedBy { it.key }.forEach { out.write(String.format("%02X: %d\n", it.key, it.value)) }
        
        out.write("\nNTT DISTRIBUTION:\n")
        nttMap.entries.sortedBy { it.key }.forEach { out.write(String.format("%02X: %d\n", it.key, it.value)) }
        
        out.write("\nHIGHKEY DISTRIBUTION:\n")
        highKeyMap.entries.sortedBy { it.key }.forEach { out.write(String.format("%02X: %d\n", it.key, it.value)) }
        
        out.write("\nNTT 0A INSTANCES:\n")
        ntt0AInstances.forEach { out.write(it) }
        
        out.write("\nHIGHKEY != 0 INSTANCES:\n")
        highKeyInstances.forEach { out.write(it) }
        
        out.close()
        assertTrue(true)
    }
    
    private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        for (i in 0..data.size - pattern.size) {
            var found = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }
    
    private fun getInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
               ((data[offset+1].toInt() and 0xFF) shl 16) or
               ((data[offset+2].toInt() and 0xFF) shl 8) or
               (data[offset+3].toInt() and 0xFF)
    }
}
