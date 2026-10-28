package com.example.data.parser

import com.example.domain.models.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CasmReportGeneratorTest {
    @Test
    fun generateDetailedReport() {
        val stylesDir = File("../testdata/styles")
        val styles = stylesDir.listFiles { _, name -> name.lowercase().endsWith(".sty") }?.sortedBy { it.name } ?: return
        
        val sb = StringBuilder()
        sb.append("=== DETAILED CASM REPORT FOR ALL TESTED STYLES ===\n\n")
        
        for (file in styles) {
            val data = Files.readAllBytes(file.toPath())
            val reader = StyReader()
            val sty = reader.read(data, file.name)
            
            sb.append("--------------------------------------------------\n")
            sb.append("Style: ${file.name}\n")
            sb.append("Size: ${data.size} bytes\n")
            
            val casm = sty.casm
            if (!casm.valid) {
                sb.append("CASM Parsing Failed!\n\n")
                continue
            }
            
            val dump = casm.rawDump
            if (dump == null) {
                sb.append("No Raw Dump Available.\n\n")
                continue
            }
            
            var csegIndex = 1
            for (cseg in dump.csegList) {
                val secNames = cseg.sdec?.sections?.joinToString(",") ?: "Unknown"
                sb.append("\n  CSEG $csegIndex | Offset: ${cseg.offset} | Sections: $secNames\n")
                
                for (ctb2 in cseg.ctb2List) {
                    val destStr = if (ctb2.destChannel == 255) "FF" else "%02X".format(ctb2.destChannel)
                    sb.append("    Src: %02X -> Dest: %s | Part: %-8s | NTR: %02X NTT: %02X | Unk: %s\n".format(
                        ctb2.sourceChannel, destStr, ctb2.partName, ctb2.ntr, ctb2.ntt, ctb2.unknownFieldsHex
                    ))
                }
                csegIndex++
            }
            sb.append("\n")
        }
        
        File("../casm_detailed_report_all.txt").writeText(sb.toString())
    }
}
