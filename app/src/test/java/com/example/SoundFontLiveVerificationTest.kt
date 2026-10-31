package com.example

import com.example.domain.soundfont.parser.Sf2Parser
import com.example.domain.soundfont.parser.SfzParser
import com.example.domain.soundfont.repository.OnDemandSoundFontRepository
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

class SoundFontLiveVerificationTest {

    @Test
    fun testRealTritonSoundFonts() {
        val rootDir = File("/tmp/repo_sf2/soundfonts/Standard_Kit_2")
        if (!rootDir.exists()) {
            println("Path not found: ${rootDir.absolutePath}")
            return
        }

        println("=== LIVE TESTING TRITON SOUNDFONTS ===")
        val repo = OnDemandSoundFontRepository()
        val scanned = repo.scanDirectory(rootDir)
        println("Scanned SoundFont files count: ${scanned.size}")
        println("Total available voices discovered: ${repo.availableVoices.size}")

        for (voice in repo.availableVoices) {
            println("-> Voice: [${voice.category}] '${voice.name}' | File: ${File(voice.filePath).name} | Bank: ${voice.bank} Prog: ${voice.program} | Stereo: ${voice.isStereo} | Size: ${voice.fileSizeFormatted}")
        }

        // Test loading samples for SFZ (Standard_Kit_2)
        val sfzMeta = scanned.find { it.filePath.endsWith(".sfz") }
        if (sfzMeta != null) {
            println("\nTesting on-demand PCM loading for SFZ: ${sfzMeta.name}")
            val preset = repo.getOrLoadPreset(sfzMeta, 0, 0)
            println("Loaded SFZ preset: ${preset?.name}, Total zones: ${preset?.zones?.size}")
            val loadedSamples = preset?.zones?.count { it.sampleBuffer != null } ?: 0
            println("Zones with valid PCM audio buffers: $loadedSamples / ${preset?.zones?.size}")
            assertTrue("SFZ should load PCM audio samples", loadedSamples > 0)
        }

        // Test loading samples for one of the harmonic SF2 files (e.g. Piano or Rhodes)
        val sf2Meta = scanned.find { it.filePath.endsWith("EXBA006_Piano_String_Layer_A.sf2") }
        if (sf2Meta != null) {
            println("\nTesting on-demand PCM loading for SF2: ${sf2Meta.name}")
            val preset = repo.getOrLoadPreset(sf2Meta, sf2Meta.presets.first().bank, sf2Meta.presets.first().program)
            println("Loaded SF2 preset: ${preset?.name}, Total zones: ${preset?.zones?.size}")
            val loadedSamples = preset?.zones?.count { it.sampleBuffer != null } ?: 0
            println("SF2 zones with valid PCM audio buffers: $loadedSamples / ${preset?.zones?.size}")
            println("Estimated RAM in use: ${repo.getLoadedRamMb()} MB")
            assertTrue("SF2 should load PCM audio samples on-demand", loadedSamples > 0)
        }
    }
}
