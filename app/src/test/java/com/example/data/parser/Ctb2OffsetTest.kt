package com.example.data.parser

import com.example.domain.arranger.ArrangerCoreState
import com.example.domain.arranger.ChordDetectorCore
import com.example.domain.chord.YamahaRuleFactory
import com.example.domain.models.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Ctb2OffsetTest {

    private val reader = StyReader()

    private fun findStyleFile(name: String): File {
        val candidates = listOf(
            File("testdata/github_styles/$name"),
            File("../testdata/github_styles/$name"),
            File("app/src/test/resources/testdata/github_styles/$name")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Style file not found in candidates: $name")
    }

    /**
     * TESTE 1: Anti-Regressão Ctb2 - Bug noteHighLimit == 1
     * Garante que noteHighLimit NÃO seja lido do offset de NTR (byte 22, onde o valor é 1),
     * e sim do byte 26.
     */
    @Test
    fun testRealStylesCtb2OffsetsCorrectlyParsed() {
        val styleFile = findStyleFile("Bugio.sty")
        val sty = reader.read(styleFile.readBytes(), styleFile.name)
        assertNotNull(sty)

        val mainA = sty.casm.sections.find { it.name.contains("Main A", ignoreCase = true) }
        assertNotNull("Main A section should be found in Bugio.sty", mainA)

        // Chord 1 (dest 11)
        val chord1 = mainA!!.channels.find { it.destChannel == 11 }
        assertNotNull("Chord 1 channel should be present", chord1)
        
        // BUG PREV: Anteriormente, noteHighLimit era lido como 1 (byte 22 = NTR)
        assertTrue("Chord1 noteHighLimit must NOT be 1 (was ${chord1!!.noteHighLimit})", chord1.noteHighLimit > 1)
        assertEquals("Chord1 noteHighLimit should be 127 in Bugio.sty", 127, chord1.noteHighLimit)
        assertEquals("Chord1 noteLowLimit should be 0 in Bugio.sty", 0, chord1.noteLowLimit)
        assertEquals("Chord1 highKey should be 7 (G) in Bugio.sty", 7, chord1.highKey)
        assertEquals("Chord1 NTR should be ROOT_FIXED (1)", NTR.ROOT_FIXED, chord1.ntr)

        // Bass (dest 10)
        val bass = mainA.channels.find { it.destChannel == 10 }
        assertNotNull("Bass channel should be present", bass)
        assertEquals("Bass noteLowLimit should be 28 (E0) in Bugio.sty", 28, bass!!.noteLowLimit)
        assertEquals("Bass noteHighLimit should be 127 in Bugio.sty", 127, bass.noteHighLimit)
        assertEquals("Bass highKey should be 3 (Eb) in Bugio.sty", 3, bass.highKey)
        assertEquals("Bass NTR should be ROOT_TRANS (0)", NTR.ROOT_TRANS, bass.ntr)
    }

    /**
     * TESTE 2: Garantir que nenhuma nota harmônica caia na faixa sub-grave 0..11
     */
    @Test
    fun testChordNotesNeverCollapseToSubBass() {
        val chordChannel = CasmChannel(
            sourceChannel = 11,
            destChannel = 11,
            sourceChordRoot = "C",
            sourceChordType = "Maj7",
            ntr = NTR.ROOT_FIXED,
            ntt = NTT.CHORD,
            highKey = 7,
            noteLowLimit = 0,
            noteHighLimit = 127
        )

        val sourceChord = Chord(rootKey = 0, type = ChordType.Major)
        val chordsToTest = listOf(
            Chord(0, ChordType.Major), // C
            Chord(5, ChordType.Major), // F
            Chord(7, ChordType.Major), // G
            Chord(9, ChordType.Minor), // Am
            Chord(2, ChordType.Minor), // Dm
            Chord(7, ChordType.Dominant7), // G7
            Chord(0, ChordType.Major7) // Cmaj7
        )

        val rule = YamahaRuleFactory.getRule(chordChannel.ntr, chordChannel.ntt)
        val patternNotes = listOf(52, 55, 60, 64) // E3, G3, C4, E4

        for (target in chordsToTest) {
            for (origNote in patternNotes) {
                val transposed = rule.transpose(origNote, sourceChord, target, chordChannel)
                assertTrue(
                    "Note $origNote transposed to ${target.displayName} collapsed to $transposed (must be >= 24)",
                    transposed >= 24
                )
            }
        }
    }

    /**
     * TESTE 3: Verificação de Notas MIDI para C, F, G
     */
    @Test
    fun testMidiNotesForCFG() {
        val bassChannel = CasmChannel(
            sourceChannel = 10,
            destChannel = 10,
            sourceChordRoot = "C",
            sourceChordType = "Maj7",
            ntr = NTR.ROOT_TRANS,
            ntt = NTT.BASS,
            highKey = 3,
            noteLowLimit = 28,
            noteHighLimit = 127
        )
        val chordChannel = CasmChannel(
            sourceChannel = 11,
            destChannel = 11,
            sourceChordRoot = "C",
            sourceChordType = "Maj7",
            ntr = NTR.ROOT_FIXED,
            ntt = NTT.CHORD,
            highKey = 7,
            noteLowLimit = 0,
            noteHighLimit = 127
        )

        val bassRule = YamahaRuleFactory.getRule(bassChannel.ntr, bassChannel.ntt)
        val chordRule = YamahaRuleFactory.getRule(chordChannel.ntr, chordChannel.ntt)
        val sourceChord = Chord(0, ChordType.Major)

        // C Major: C E G (pattern root C2=36, chord root C4=60)
        val cBass = bassRule.transpose(36, sourceChord, Chord(0, ChordType.Major), bassChannel)
        val cChord = chordRule.transpose(60, sourceChord, Chord(0, ChordType.Major), chordChannel)
        assertEquals(36, cBass) // C2
        assertEquals(60, cChord) // C4

        // F Major: F A C (root 5 > HK 3 -> bass drops 1 octave to F1=29)
        val fBass = bassRule.transpose(36, sourceChord, Chord(5, ChordType.Major), bassChannel)
        val fChord = chordRule.transpose(60, sourceChord, Chord(5, ChordType.Major), chordChannel)
        assertEquals(29, fBass) // F1 (43.6 Hz, audível e bem definido)
        assertEquals(60, fChord) // C4 é nota comum de F Major (F-A-C), voice leading preserva C4

        // G Major: G B D (root 7 > HK 3 -> bass drops 1 octave to G1=31)
        val gBass = bassRule.transpose(36, sourceChord, Chord(7, ChordType.Major), bassChannel)
        val gChord = chordRule.transpose(60, sourceChord, Chord(7, ChordType.Major), chordChannel)
        assertEquals(31, gBass) // G1 (49.0 Hz)
        assertTrue("G chord tone should stay around 60 (got $gChord)", gChord in 59..62)
    }

    /**
     * TESTE 4: Inversões e Slash Chords C/E, F/A, G/B
     */
    @Test
    fun testSlashChordsCE_FA_GB() {
        val bassChannel = CasmChannel(
            sourceChannel = 10,
            destChannel = 10,
            sourceChordRoot = "C",
            sourceChordType = "Maj7",
            ntr = NTR.ROOT_TRANS,
            ntt = NTT.BASS,
            highKey = 3,
            noteLowLimit = 28,
            noteHighLimit = 127
        )
        val bassRule = YamahaRuleFactory.getRule(bassChannel.ntr, bassChannel.ntt)
        val sourceChord = Chord(0, ChordType.Major)

        // C/E (bassKey = 4, E1 = 28)
        val ceBass = bassRule.transpose(36, sourceChord, Chord(0, ChordType.Major, bassKey = 4), bassChannel)
        assertEquals(28, ceBass) // E1

        // F/A (bassKey = 9, A1 = 33)
        val faBass = bassRule.transpose(36, sourceChord, Chord(5, ChordType.Major, bassKey = 9), bassChannel)
        assertEquals(33, faBass) // A1

        // G/B (bassKey = 11, B1 = 35)
        val gbBass = bassRule.transpose(36, sourceChord, Chord(7, ChordType.Major, bassKey = 11), bassChannel)
        assertEquals(35, gbBass) // B1
    }

    /**
     * TESTE 5: Am, Dm, G7, Cmaj7, 4-note chords e preservação de inversões no detector
     */
    @Test
    fun testAmDmG7Cmaj7AndDetectorInversions() {
        val chordChannel = CasmChannel(
            sourceChannel = 11,
            destChannel = 11,
            sourceChordRoot = "C",
            sourceChordType = "Maj7",
            ntr = NTR.ROOT_FIXED,
            ntt = NTT.CHORD,
            highKey = 7,
            noteLowLimit = 0,
            noteHighLimit = 127
        )
        val chordRule = YamahaRuleFactory.getRule(chordChannel.ntr, chordChannel.ntt)
        val sourceChord = Chord(0, ChordType.Major)
        val pattern4Notes = listOf(60, 64, 67, 71) // C4, E4, G4, B4

        val targets = listOf(
            Chord(9, ChordType.Minor), // Am
            Chord(2, ChordType.Minor), // Dm
            Chord(7, ChordType.Dominant7), // G7
            Chord(0, ChordType.Major7) // Cmaj7
        )

        for (target in targets) {
            val resultNotes = pattern4Notes.map { chordRule.transpose(it, sourceChord, target, chordChannel) }
            assertTrue("All notes in ${target.displayName} must be >= 40: $resultNotes", resultNotes.all { it >= 40 })
            assertEquals("All 4 chord tones must be distinct: $resultNotes", 4, resultNotes.distinct().size)
        }

        // Testar detector preservando inversões
        val state = ArrangerCoreState()
        val detector = ChordDetectorCore(state)

        // Tocar C/E: E2 (40), G2 (43), C3 (48)
        detector.processNoteOn(40, 100)
        detector.processNoteOn(43, 100)
        detector.processNoteOn(48, 100)
        val detected = state.currentChord.get()
        assertEquals(0, detected.rootKey)
        assertEquals(4, detected.bassKey) // E como bassKey
    }
}
