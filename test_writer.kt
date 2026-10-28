import com.example.domain.midi.CasmWriter
import com.example.MidiRhythmState
import com.example.StyleSection
import com.example.RhythmTrack
import com.example.RecordedNote
import com.example.data.parser.StyReader

fun main() {
    val track1 = RhythmTrack("Drums", 9, 0, 112, 0, true)
    track1.notes = listOf(RecordedNote(0, 36, 100, 480))
    val sections = mapOf(StyleSection.MAIN_A to listOf(track1))
    val bytes = CasmWriter.writeStyle(sections, 1920)
    
    val parser = StyReader()
    val styFile = parser.parse(bytes, "Test.sty")
    
    val section = styFile.sections[StyleSection.MAIN_A]
    println("Section exists: \${section?.exists}")
    println("Section start tick: \${section?.startTick}")
    
    section?.tracks?.forEach { (t, styleTrack) ->
        println("Track \$t event count: \${styleTrack.eventCount}")
        styleTrack.events.forEach { ev ->
            println("Event: \$ev")
        }
    }
}
