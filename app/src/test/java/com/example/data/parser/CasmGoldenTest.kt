package com.example.data.parser

import org.junit.Test
import java.io.File
import java.nio.file.Files

class CasmGoldenTest {

    @Test
    fun `test casm extraction from style`() {
        val styleFile = File("../testdata/styles/BachataSertaneja3.sty")
        if (!styleFile.exists()) {
            return
        }

        val data = Files.readAllBytes(styleFile.toPath())
        val reader = StyReader()
        val sty = reader.read(data, styleFile.name)

        val dump = sty.casm.rawDump
        if (dump == null) {
            return
        }
        
        val sb = StringBuilder()
        sb.append("Found ${dump.csegList.size} CSEG blocks\\n")
        
        dump.csegList.forEach { cseg ->
            sb.append("CSEG at ${cseg.offset}\\n")
            sb.append("  Sections: ${cseg.sdec?.sections}\\n")
            cseg.ctb2List.forEach { ctb2 ->
                sb.append("  Channel: ${ctb2.sourceChannel} -> ${ctb2.destChannel} | Part: ${ctb2.partName}\\n")
                sb.append("    NTR: ${ctb2.ntr} | NTT: ${ctb2.ntt}\\n")
                sb.append("    UNKNOWN: ${ctb2.unknownFieldsHex}\\n")
            }
        }
        
        File("test_results_casm.txt").writeText(sb.toString())
    }
}
