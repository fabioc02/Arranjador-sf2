package com.example.domain.studio.compiler

import com.example.domain.models.*
import com.example.domain.studio.models.*

class YamahaCompiler {

    fun compile(project: YapProject): StyFile {
        val ppq = 480
        val sectionsMap = mutableMapOf<StyleSection, SectionData>()
        val casmSections = mutableListOf<CasmSection>()
        var currentStartTick = 0L

        for (yapSection in project.sections) {
            val sectionEnum = StyleSection.fromMarker(yapSection.name)
            val sectionEndTick = currentStartTick + (yapSection.lengthBars * ppq * 4) // Assuming 4/4

            val tracksMap = mutableMapOf<Int, StyleTrack>()
            val casmChannels = mutableListOf<CasmChannel>()

            for (yapTrack in yapSection.tracks) {
                val midiEvents = mutableListOf<MidiEvent>()

                for (yapEvent in yapTrack.events) {
                    val actualTick = currentStartTick + yapEvent.tick
                    when (yapEvent.type) {
                        0 -> {
                            midiEvents.add(MidiEvent.NoteOn(actualTick, yapTrack.channel, yapEvent.data1, yapEvent.data2))
                            if (yapEvent.duration > 0) {
                                midiEvents.add(MidiEvent.NoteOff(actualTick + yapEvent.duration, yapTrack.channel, yapEvent.data1, 0))
                            }
                        }
                        1 -> {
                            midiEvents.add(MidiEvent.NoteOff(actualTick, yapTrack.channel, yapEvent.data1, yapEvent.data2))
                        }
                        2 -> {
                            midiEvents.add(MidiEvent.ControlChange(actualTick, yapTrack.channel, yapEvent.data1, yapEvent.data2))
                        }
                        3 -> {
                            midiEvents.add(MidiEvent.ProgramChange(actualTick, yapTrack.channel, yapEvent.data1))
                        }
                        4 -> {
                            midiEvents.add(MidiEvent.PitchBend(actualTick, yapTrack.channel, yapEvent.data1))
                        }
                    }
                }
                
                // Sort events by tick
                midiEvents.sortBy { it.tick }
                tracksMap[yapTrack.channel] = StyleTrack(eventCount = midiEvents.size, events = midiEvents)

                // Reconstruct CASM rules
                casmChannels.add(
                    CasmChannel(
                        sourceChannel = yapTrack.channel,
                        destChannel = yapTrack.channel,
                        ntr = NTR.fromId(yapTrack.casmRules.ntr),
                        ntt = NTT.fromId(yapTrack.casmRules.ntt),
                        highKey = yapTrack.casmRules.highKey,
                        noteLowLimit = yapTrack.casmRules.noteLimitLow,
                        noteHighLimit = yapTrack.casmRules.noteLimitHigh,
                        retriggerRule = RTR.fromId(yapTrack.casmRules.rtr)
                    )
                )
            }

            sectionsMap[sectionEnum] = SectionData(
                name = yapSection.name,
                tracks = tracksMap,
                startTick = currentStartTick,
                endTick = sectionEndTick,
                exists = true
            )

            casmSections.add(
                CasmSection(
                    name = sectionEnum.sectionName,
                    channels = casmChannels
                )
            )

            currentStartTick = sectionEndTick
        }

        return StyFile(
            valid = true,
            name = project.metadata.name,
            defaultBpm = project.tempoMap.defaultBpm,
            ppq = ppq,
            sections = sectionsMap,
            casm = StyleCasm(
                valid = true,
                sections = casmSections
            )
        )
    }
}
