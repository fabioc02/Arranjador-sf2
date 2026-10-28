package com.example.domain.playback

import com.example.domain.chord.ChordEngine
import com.example.domain.models.Chord
import com.example.domain.models.StyFile
import com.example.domain.models.StyleSection

class StylePlaybackEngine(
    private val style: StyFile,
    private val chordEngine: ChordEngine
) {
    val clock = StyleClock(style.ppq)
    val sectionPlayer = SectionPlayer()
    private val scheduler = EventScheduler(sectionPlayer)
    private val tickProcessor = TickProcessor(scheduler, chordEngine)
    private val loopController = LoopController()
    
    var currentChord: Chord = Chord(0, com.example.domain.models.ChordType.Major)
    
    var isFinished: Boolean = false
        private set
        
    fun reset() {
        clock.reset()
        sectionPlayer.reset()
        tickProcessor.reset()
        isFinished = false
    }

    fun diagnoseSection(section: StyleSection): String {
        if (!style.hasSection(section)) return "Section not found in Style"
        
        val sb = StringBuilder()
        sb.append("SECTION: ${section.name}\n\n")
        
        style.casm.rawDump?.csegList?.forEach { cseg ->
            if (cseg.sdec?.sections?.contains(section.name) == true) {
                sb.append("FOUND IN CSEG (offset: ${cseg.offset}, size: ${cseg.size})\n")
                cseg.ctb2List.forEach { ctb2 ->
                    sb.append("  CHANNEL ${ctb2.sourceChannel} -> DEST ${ctb2.destChannel}\n")
                    sb.append("    Part Name: '${ctb2.partName}'\n")
                    sb.append("    NTR: ${ctb2.ntr}\n")
                    sb.append("    NTT: ${ctb2.ntt}\n")
                    sb.append("    UNKNOWN: ${ctb2.unknownFieldsHex}\n")
                }
            }
        }
        
        return sb.toString()
    }

    fun playSection(section: StyleSection) {
        if (style.hasSection(section)) {
            clock.reset()
            isFinished = false
            sectionPlayer.playSection(section, style.sections[section]!!)
        }
    }
        
    fun getNextEventTick(): Long? = sectionPlayer.peekNextEventTick()

    fun processTick(targetTick: Long): Pair<List<GeneratedMidiEvent>, PlaybackMetrics> {
        val result = tickProcessor.processTick(targetTick, style.casm, sectionPlayer.currentSection.sectionName, currentChord)
        clock.setTick(targetTick)
        val sectionData = sectionPlayer.sectionData
        if (sectionData != null) {
            val lengthInTicks = sectionData.endTick - sectionData.startTick
            if (targetTick >= lengthInTicks) {
                var nextSection = loopController.determineNextSection(sectionPlayer.currentSection)
                if (lengthInTicks <= 0L && nextSection == sectionPlayer.currentSection) {
                    nextSection = StyleSection.Unknown
                }
                if (nextSection != StyleSection.Unknown && style.hasSection(nextSection)) {
                    playSection(nextSection)
                } else {
                    isFinished = true
                }
            }
        }
        return result
    }

    fun processNextTick(): Pair<List<GeneratedMidiEvent>, PlaybackMetrics> {
        val result = tickProcessor.processTick(clock.currentTick, style.casm, sectionPlayer.currentSection.sectionName, currentChord)
            
        val sectionData = sectionPlayer.sectionData
        if (sectionData != null) {
            val lengthInTicks = sectionData.endTick - sectionData.startTick
            if (clock.currentTick >= lengthInTicks) {
                // Section ended
                var nextSection = loopController.determineNextSection(sectionPlayer.currentSection)
                    
                // Anti-infinite loop protection for zero-length sections
                if (lengthInTicks <= 0L && nextSection == sectionPlayer.currentSection) {
                    nextSection = StyleSection.Unknown // Stop playback to avoid infinite recursion
                }
                    
                if (nextSection != StyleSection.Unknown && style.hasSection(nextSection)) {
                    playSection(nextSection)
                } else {
                    isFinished = true
                }
            } else {
                clock.tick()
            }
        }
            
        return result
    }
}