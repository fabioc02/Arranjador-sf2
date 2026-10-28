package com.example.domain.midi

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.example.StyleSection as EditorSection
import com.example.domain.models.StyleSection as DomainSection
import com.example.RhythmTrack
import com.example.RecordedNote
import com.example.data.parser.StyReader
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CasmWriterTest {
    @Test
    fun testWriter() {
        val track1 = RhythmTrack("Drums", 9, 0, 112, 0, true)
        track1.notes = listOf(RecordedNote(0, 36, 100, 480))
        val sections = mapOf(EditorSection.MAIN_A to listOf(track1))
        val bytes = CasmWriter.writeStyle(sections, 1920)
        
        val parser = StyReader()
        val styFile = parser.read(bytes, "Test.sty")
        
        val section = styFile.sections[DomainSection.MainA]
        val sb = StringBuilder()
        sb.append("Section exists: ${section?.exists}\n")
        sb.append("Section start tick: ${section?.startTick}\n")
        sb.append("Section name: ${section?.name}\n")
        
        section?.tracks?.forEach { (t, styleTrack) ->
            sb.append("Track $t event count: ${styleTrack.eventCount}\n")
            styleTrack.events.filter { it is com.example.domain.models.MidiEvent.NoteOn }.forEach { ev ->
                sb.append("NoteOn: $ev\n")
            }
        }
        File("test_output5.txt").writeText(sb.toString())
    }
}
