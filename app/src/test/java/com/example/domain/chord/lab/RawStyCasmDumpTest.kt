package com.example.domain.chord.lab

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files

class RawStyCasmDumpTest {

    @Test
    fun dumpRawCasmAndCtb2() {
        val dir = File("../testdata/styles/")
        val files = dir.listFiles { f -> f.extension.equals("sty", true) || f.extension.matches(Regex("S\\d+")) } ?: return
        
        val outFile = File("docs/reports/RAW_CASM_DUMP.txt")
        outFile.parentFile.mkdirs()
        val out = outFile.bufferedWriter()
        
        var totalStyles = files.size
        var totalCasm = 0
        var totalCtb2 = 0
        var totalChannels = 0
        
        val ntrMap = mutableMapOf<Int, Int>()
        val nttMap = mutableMapOf<Int, Int>()
        val highKeyMap = mutableMapOf<Int, Int>()
        
        var totalNtt0A = 0
        
        for (file in files) {
            out.write("==================================================\n")
            out.write("STYLE: ${file.name}\n")
            
            val bytes = Files.readAllBytes(file.toPath())
            var casmIdx = indexOf(bytes, "CASM".toByteArray())
            if (casmIdx == -1) {
                out.write("CASM OFFSET: NOT FOUND\n")
                continue
            }
            
            totalCasm++
            out.write("CASM OFFSET: $casmIdx\n")
            
            val casmSize = getInt(bytes, casmIdx + 4)
            val casmData = bytes.copyOfRange(casmIdx + 8, casmIdx + 8 + casmSize)
            
            var csegIdx = 0
            while (csegIdx < casmData.size - 8) {
                if (casmData[csegIdx] == 'C'.code.toByte() && casmData[csegIdx+1] == 'S'.code.toByte() &&
                    casmData[csegIdx+2] == 'E'.code.toByte() && casmData[csegIdx+3] == 'G'.code.toByte()) {
                    
                    val csegSize = getInt(casmData, csegIdx + 4)
                    val csegEndIdx = minOf(csegIdx + 8 + csegSize, casmData.size)
                    val csegData = casmData.copyOfRange(csegIdx + 8, csegEndIdx)
                    
                    var ctb2Idx = 0
                    while (ctb2Idx < csegData.size - 8) {
                        if (csegData[ctb2Idx] == 'C'.code.toByte() && csegData[ctb2Idx+1] == 't'.code.toByte() &&
                            csegData[ctb2Idx+2] == 'b'.code.toByte() && csegData[ctb2Idx+3] == '2'.code.toByte()) {
                            
                            totalCtb2++
                            val ctb2Size = getInt(csegData, ctb2Idx + 4)
                            val endIdx = minOf(ctb2Idx + 8 + ctb2Size, csegData.size)
                            val payload = csegData.copyOfRange(ctb2Idx + 8, endIdx)
                            
                            out.write("\nCTB2 OFFSET: ${casmIdx + 8 + csegIdx + 8 + ctb2Idx}\n")
                            out.write("CTB2 SIZE: $ctb2Size\n")
                            
                            // Reconstruct original offsets by treating EF BF BD as 1 byte
                            val originalBytes = mutableListOf<Int>()
                            var i = 0
                            while (i < payload.size) {
                                if (i + 2 < payload.size && 
                                    payload[i].toUByte().toInt() == 0xEF && 
                                    payload[i+1].toUByte().toInt() == 0xBF && 
                                    payload[i+2].toUByte().toInt() == 0xBD) {
                                    originalBytes.add(0xFF) // placeholder for corrupted byte
                                    i += 3
                                } else {
                                    originalBytes.add(payload[i].toUByte().toInt())
                                    i++
                                }
                            }
                            
                            if (originalBytes.size >= 23) {
                                val srcCh = originalBytes[0]
                                val destCh = originalBytes[9]
                                val ntr = originalBytes[18]
                                val ntt = originalBytes[19]
                                val highKey = originalBytes[20]
                                val low = originalBytes[21]
                                val high = originalBytes[22]
                                
                                totalChannels++
                                ntrMap[ntr] = (ntrMap[ntr] ?: 0) + 1
                                nttMap[ntt] = (nttMap[ntt] ?: 0) + 1
                                highKeyMap[highKey] = (highKeyMap[highKey] ?: 0) + 1
                                
                                if (ntt == 0x0A) {
                                    totalNtt0A++
                                    out.write("!!! FOUND NTT 0A !!!\n")
                                }
                                
                                out.write("Source=$srcCh Dest=$destCh NTR=$ntr NTT=$ntt HighKey=$highKey Low=$low High=$high\n")
                                out.write("ORIGINAL_LOGICAL_BYTES:\n")
                                for (j in originalBytes.indices step 8) {
                                    val chunk = originalBytes.subList(j, minOf(j + 8, originalBytes.size))
                                    out.write(String.format("%02X: ", j))
                                    out.write(chunk.joinToString(" ") { String.format("%02X", it) })
                                    out.write("\n")
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
        out.write("\nLOW LIMIT DISTRIBUTION:\n")
        
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
