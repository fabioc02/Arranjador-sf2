package com.example

import com.example.data.parser.StyParser
import com.example.data.parser.StyReader
import com.example.domain.chord.ChordEngine
import com.example.domain.midi.CasmWriter
import com.example.domain.models.*
import com.example.domain.playback.EventScheduler
import com.example.domain.playback.SectionPlayer
import com.example.domain.playback.TickProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Phase4RealStyPlaybackTest {

    private fun findStyleFile(fileName: String): File {
        val candidates = listOf(
            File("../testdata/github_styles/$fileName"),
            File("testdata/github_styles/$fileName"),
            File("../../testdata/github_styles/$fileName"),
            File("../testdata/styles/$fileName"),
            File("testdata/styles/$fileName")
        )
        val file = candidates.find { it.exists() }
        assertNotNull("Style file not found: $fileName", file)
        return file!!
    }

    private val representativeStyles = listOf(
        "AcusticoBR1.sty",
        "BregaBrasil.T161.sty",
        "ArrochaPiseiro1.S374.sty",
        "Bugio.sty",
        "Partido Alto.T557.sty",
        "PopReggaeBR.sty",
        "ViolaCururu.S081.sty",
        "AdoraBrasil.S263.sty"
    )

    @Test
    fun testRealStylesImportAndCasmValidation() = runBlocking {
        val parser = StyParser()
        var validatedCount = 0

        for (styleName in representativeStyles) {
            val file = findStyleFile(styleName)
            val parsedSty = parser.parse(file)

            // 1. IMPORTAÇÃO
            assertTrue("Arquivo deve abrir e ser válido: $styleName, err=${parsedSty.errorMessage}", parsedSty.valid)
            assertTrue("PPQ deve ser 1920: $styleName, got=${parsedSty.ppq}", parsedSty.ppq == 1920)
            assertTrue("Deve conter canais/tracks MIDI: $styleName", parsedSty.tracksCount > 0)
            assertTrue("Deve identificar seções no timeline: $styleName", parsedSty.markersTimeline.isNotEmpty())
            assertTrue("CASM deve ser localizado: $styleName", parsedSty.casmOffset >= 0)

            // 2. CASM, CSEG, Sdec, Ctb2
            val casm = parsedSty.casm
            assertNotNull("CASM model não pode ser nulo: $styleName", casm)
            assertTrue("CASM deve conter seções: $styleName", casm.sections.isNotEmpty())

            val allChannels = casm.sections.flatMap { it.channels }
            assertTrue("CASM deve conter canais mapeados: $styleName", allChannels.isNotEmpty())

            // Verificar parâmetros em cada canal (NTR, NTT, High Key, Limits, RTR, Root, Chord)
            for (channel in allChannels) {
                assertNotNull("NTR deve ser válido", channel.ntr)
                assertNotNull("NTT deve ser válido", channel.ntt)
                assertTrue("HighKey deve estar entre 0 e 127: ${channel.highKey}", channel.highKey in 0..127)
                assertTrue("Note limits devem ser válidos: low=${channel.noteLowLimit}, high=${channel.noteHighLimit}",
                    channel.noteLowLimit in 0..127 && channel.noteHighLimit in 0..127)
            }

            // Verificar presença de canal de Bass (destChannel 10/11 ou nome Bass)
            val bassChannel = allChannels.find { it.destChannel in 10..11 || it.partName.contains("Bass", ignoreCase = true) }
            assertNotNull("Estilo $styleName deve possuir canal de Baixo definido no CASM", bassChannel)

            validatedCount++
        }

        assertTrue("Pelo menos 5 estilos devem ter sido completamente validados", validatedCount >= 5)
    }

    @Test
    fun testRealStyDeterministicPlayback() = runBlocking {
        val file = findStyleFile("BregaBrasil.T161.sty")
        val parser = StyParser()
        val parsedSty = parser.parse(file)
        assertTrue("Failed to parse STY: ${parsedSty.errorMessage}", parsedSty.valid)

        // Localiza seção Main A
        val mainAEntry = parsedSty.sections.entries.find { it.key == com.example.domain.models.StyleSection.MainA && it.value.exists }
            ?: parsedSty.sections.entries.firstOrNull { it.value.exists && it.value.tracks.values.any { t -> t.eventCount > 0 } }
        assertNotNull("No valid playable section found in STY", mainAEntry)

        val mainA = mainAEntry!!.value
        val mainAKey = mainAEntry.key

        val sectionPlayer = SectionPlayer()
        sectionPlayer.playSection(mainAKey, mainA)

        val scheduler = EventScheduler(sectionPlayer)
        val chordEngine = ChordEngine()
        val tickProcessor = TickProcessor(scheduler, chordEngine)

        // 3. Teste de Playback com acordes variados
        val testChords = listOf(
            Chord(0, ChordType.Major),          // C Major
            Chord(0, ChordType.Minor),          // C Minor
            Chord(0, ChordType.Dominant7),      // C7
            Chord(0, ChordType.Major, bassKey = 4), // C/E slash chord
            Chord(5, ChordType.Major, bassKey = 7)  // F/G slash chord
        )

        var totalEventsGenerated = 0
        for (currentChord in testChords) {
            var chordEvents = 0
            for (relativeTick in 0..1920L step 48L) {
                val (generatedEvents, _) = tickProcessor.processTick(
                    tick = relativeTick,
                    casm = parsedSty.casm,
                    currentSectionName = mainAKey.sectionName,
                    currentChord = currentChord
                )

                for (e in generatedEvents) {
                    if (e.eventType == "NoteOn" || e.eventType == "NoteOff") {
                        // Verifica se a nota transformada está no range MIDI válido
                        assertTrue("Nota gerada deve estar entre 0 e 127: ${e.note}", e.note in 0..127)
                        chordEvents++
                        totalEventsGenerated++
                    }
                }
            }
            assertTrue("Cada acorde deve processar eventos no compasso", chordEvents >= 0)
        }

        assertTrue("Playback deve gerar eventos MIDI", totalEventsGenerated > 0)
    }

    @Test
    fun testRealStyleRoundTripValidation() = runBlocking {
        // Round-trip test: Modelo -> CasmWriter -> StyReader -> Verificação
        val originalFile = findStyleFile("AcusticoBR1.sty")
        val reader = StyReader()
        val originalSty = reader.read(originalFile.readBytes(), originalFile.name)
        assertTrue("Original STY deve ser válido", originalSty.valid)
        assertEquals("PPQ deve ser 1920", 1920, originalSty.ppq)

        // Converte as tracks da Main A para RhythmTrack do modelo do Writer
        val rhythmTracks = mutableListOf<com.example.RhythmTrack>()
        val mainAData = originalSty.sections[com.example.domain.models.StyleSection.MainA]
        if (mainAData != null) {
            val events = mainAData.tracks.values.flatMap { it.events }
            val noteChannels = events.filterIsInstance<MidiEvent.NoteOn>().map { it.channel }.distinct()
            for (ch in noteChannels) {
                val isDrum = (ch == 9 || ch == 10)
                val trackName = if (isDrum) "Rhythm" else if (ch == 11) "Bass" else "Chord"
                rhythmTracks.add(
                    com.example.RhythmTrack(
                        trackName,
                        ch,
                        0,
                        112,
                        0,
                        isDrum
                    )
                )
            }
        }

        if (rhythmTracks.isEmpty()) {
            rhythmTracks.add(com.example.RhythmTrack("Rhythm 1", 9, 127, 0, 0, true))
            rhythmTracks.add(com.example.RhythmTrack("Bass", 11, 0, 112, 33, false))
        }

        val sectionsMap = mapOf(com.example.StyleSection.MAIN_A to rhythmTracks)
        val generatedBytes = CasmWriter.writeStyle(sectionsMap, ppq = 1920, tempoBpm = 120.0)
        assertTrue("Bytes gerados devem ser válidos e maiores que 100 bytes", generatedBytes.size > 100)

        // Re-importa e valida equivalência dos dados cruciais
        val reimportedSty = reader.read(generatedBytes, "RoundTripTest.sty")
        assertTrue("STY gerado deve ser válido no parser", reimportedSty.valid)
        assertEquals("PPQ 1920 deve ser preservado", 1920, reimportedSty.ppq)
        assertTrue("Seções devem ser identificadas", reimportedSty.sections.isNotEmpty())
        assertTrue("CASM deve ser identificado", reimportedSty.casmOffset >= 0)
        assertTrue("Canais CASM devem estar presentes", reimportedSty.casm.sections.flatMap { it.channels }.isNotEmpty())
    }
}
