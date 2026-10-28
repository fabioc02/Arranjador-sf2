
package com.example.domain.chord.reference

import org.junit.Test
import org.junit.Assert.*
import com.example.domain.models.*
import java.io.File
import java.nio.file.Files

class FallbackSecurityTest {
    @Test
    fun testFallbackBehavior() {
        println("--- TEST 8: CASM FALLBACK SECURITY ---")
        val file = File("../testdata/styles/BregaCalypso.sty")
        val sty = com.example.data.parser.StyReader().read(Files.readAllBytes(file.toPath()), file.name)
        
        // Simulating the current TickProcessor behavior
        val sectionName = "IntroX" // Invalid name
        val casmSection = sty.casm?.sections?.find { it.name.equals(sectionName, ignoreCase = true) } 
            ?: sty.casm?.sections?.firstOrNull()
            
        assertNotNull("Fallback occurred to Main A", casmSection)
        println("Requested: $sectionName | Resolved to: ${casmSection?.name}")
        
        // In Main A, Aux channels (02-07) are often muted (Dest 0xFF) or changed.
        val auxChannel = casmSection?.channels?.find { it.sourceChannel == 2 }
        println("Aux Channel 2 in ${casmSection?.name} routes to Dest: ${String.format("%02X", auxChannel?.destChannel ?: 0)}")
    }
}
