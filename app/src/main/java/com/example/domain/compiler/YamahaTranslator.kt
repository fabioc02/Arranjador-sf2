package com.example.domain.studio.compiler

import com.example.domain.models.*
import com.example.domain.studio.models.*
import java.util.UUID

class YamahaTranslator {

    fun translate(styFile: StyFile): YapProject {
        val project = YapProject(
            id = UUID.randomUUID().toString(),
            metadata = YapMetadata(name = styFile.name),
            tempoMap = YapTempoMap(defaultBpm = styFile.defaultBpm)
        )

        for ((sectionEnum, sectionData) in styFile.sections) {
            if (!sectionData.exists) continue

            val yapSection = YapSection(
                name = sectionEnum.sectionName,
                lengthBars = calculateLengthBars(sectionData.startTick, sectionData.endTick, styFile.ppq)
            )

            // Find CASM section matching the section name
            val casmSection = styFile.casm.sections.find { it.name.equals(sectionEnum.sectionName, ignoreCase = true) }

            for ((channel, trackData) in sectionData.tracks) {
                val yapTrack = YapTrack(
                    channel = channel,
                    name = getChannelName(channel)
                )

                // Try to find casm rules for this channel in this section
                val casmChannel = casmSection?.channels?.find { it.sourceChannel == channel || it.destChannel == channel }
                if (casmChannel != null) {
                    yapTrack.casmRules = YapCasmRules(
                        ntr = casmChannel.ntr.ordinal,
                        ntt = casmChannel.ntt.ordinal,
                        highKey = casmChannel.highKey,
                        noteLimitLow = casmChannel.noteLowLimit,
                        noteLimitHigh = casmChannel.noteHighLimit,
                        rtr = casmChannel.retriggerRule.id
                    )
                }

                // Process events
                val activeNotes = mutableMapOf<Int, YapMidiEvent>()
                for (event in trackData.events) {
                    val adjustedTick = event.tick - sectionData.startTick
                    if (adjustedTick < 0) continue

                    when (event) {
                        is MidiEvent.NoteOn -> {
                            if (event.velocity > 0) {
                                val yapEvent = YapMidiEvent(
                                    tick = adjustedTick,
                                    type = 0,
                                    data1 = event.note,
                                    data2 = event.velocity
                                )
                                yapTrack.events.add(yapEvent)
                                activeNotes[event.note] = yapEvent
                            } else {
                                // NoteOff expressed as NoteOn with 0 velocity
                                activeNotes[event.note]?.duration = adjustedTick - (activeNotes[event.note]?.tick ?: adjustedTick)
                                activeNotes.remove(event.note)
                            }
                        }
                        is MidiEvent.NoteOff -> {
                            activeNotes[event.note]?.duration = adjustedTick - (activeNotes[event.note]?.tick ?: adjustedTick)
                            activeNotes.remove(event.note)
                        }
                        is MidiEvent.ControlChange -> {
                            yapTrack.events.add(YapMidiEvent(
                                tick = adjustedTick, type = 2, data1 = event.controller, data2 = event.value
                            ))
                        }
                        is MidiEvent.ProgramChange -> {
                            yapTrack.events.add(YapMidiEvent(
                                tick = adjustedTick, type = 3, data1 = event.program, data2 = 0
                            ))
                        }
                        is MidiEvent.PitchBend -> {
                            yapTrack.events.add(YapMidiEvent(
                                tick = adjustedTick, type = 4, data1 = event.value, data2 = 0
                            ))
                        }
                        else -> {}
                    }
                }

                // Close dangling notes
                val sectionEndAdjusted = sectionData.endTick - sectionData.startTick
                for ((_, yapEvent) in activeNotes) {
                    yapEvent.duration = sectionEndAdjusted - yapEvent.tick
                }

                yapSection.tracks.add(yapTrack)
            }
            project.sections.add(yapSection)
        }

        return project
    }

    private fun calculateLengthBars(startTick: Long, endTick: Long, ppq: Int): Int {
        val totalTicks = endTick - startTick
        val ticksPerBar = ppq * 4 // Assuming 4/4
        if (ticksPerBar == 0) return 1
        return (totalTicks / ticksPerBar).toInt().coerceAtLeast(1)
    }

    private fun getChannelName(channel: Int): String {
        return when (channel) {
            9 -> "Drums"
            10 -> "Bass"
            11 -> "Chord 1"
            12 -> "Chord 2"
            13 -> "Pad"
            14 -> "Phrase 1"
            15 -> "Phrase 2"
            else -> "Ch ${channel + 1}"
        }
    }
}
