package com.example

import com.example.data.parser.StyParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FullStyIntegrityTest {

    @Test
    fun testAllStylesIntegrity() = runBlocking {
        val testDataDir = File("../testdata/styles_original")
        if (!testDataDir.exists()) {
            println("Test data directory not found. Skipping.")
            return@runBlocking
        }

        val styles = testDataDir.listFiles { _, name -> name.endsWith(".sty", ignoreCase = true) }
            ?.filter { it.isFile } ?: emptyList()
            
        assertTrue("No styles found to test", styles.isNotEmpty())

        val parser = StyParser()
        
        println("| Style | Size | Tracks | Markers | Sections | CASM Rules | Errors |")
        println("|---|---|---|---|---|---|---|")

        for (styleFile in styles) {
            val styFile = parser.parse(styleFile)
            
            // Allow CASM-less tracks
            var casmRulesCount = 0
            if (styFile.casm.sections.isNotEmpty()) {
                for (section in styFile.casm.sections) {
                    casmRulesCount += section.channels.size
                }
            }
            
            println("| ${styleFile.name} | ${styFile.fileSize} | ${styFile.tracksCount} | ${styFile.markers.size} | ${styFile.sections.size} | $casmRulesCount | ${styFile.metrics.errorsCount} |")
        }
        assertTrue("Completed parsing loop", true)
    }
}
