package com.example.domain.playback

import com.example.domain.chord.ChordEngine
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.MidiEvent
import com.example.domain.models.StyleCasm
import com.example.domain.models.CasmChannel
import com.example.domain.models.CasmSection

class TickProcessor(
    private val scheduler: EventScheduler,
    private val chordEngine: ChordEngine
) {
    private data class ActiveNoteKey(val channel: Int, val originalNote: Int)
    private val activeNotes = mutableMapOf<ActiveNoteKey, Int>()

    fun reset() {
        activeNotes.clear()
    }

    private fun getSourceChord(casmChannel: CasmChannel): Chord {
        val root = when (casmChannel.sourceChordRoot.uppercase()) {
            "C" -> 0
            "C#", "DB" -> 1
            "D" -> 2
            "D#", "EB" -> 3
            "E" -> 4
            "F" -> 5
            "F#", "GB" -> 6
            "G" -> 7
            "G#", "AB" -> 8
            "A" -> 9
            "A#", "BB" -> 10
            "B" -> 11
            else -> 0
        }
        val type = when (casmChannel.sourceChordType) {
            "Maj" -> ChordType.Major
            "Maj6" -> ChordType.Major6
            "Maj7" -> ChordType.Major7
            "Maj7(#11)" -> ChordType.Major7
            "Min" -> ChordType.Minor
            "Min6" -> ChordType.Minor
            "Min7" -> ChordType.Minor7
            "Min7b5" -> ChordType.Minor7b5
            "7th" -> ChordType.Dominant7
            "7(#11)" -> ChordType.Dominant7
            "7(b9)" -> ChordType.Dominant7
            "7(b13)" -> ChordType.Dominant7
            "7(#9)" -> ChordType.Dominant7
            "Maj7aug" -> ChordType.Augmented
            "7aug" -> ChordType.Augmented
            "1+8" -> ChordType.Major
            "1+5" -> ChordType.Major
            "Sus4" -> ChordType.Sus4
            "1+2+5" -> ChordType.Sus2
            else -> ChordType.Major7
        }
        return Chord(root, type)
    }

    private fun resolveSectionSafe(casm: StyleCasm, sectionName: String): CasmSection? {
        // 1. exact name
        casm.sections.find { it.name.equals(sectionName, ignoreCase = true) }?.let { return it }
        
        // 2. known aliases (if we want to add any in the future)
        // e.g. "Intro" -> "Intro A"
        
        // 3. Fallback seguro
        // Returns null so we bypass without muting the channel.
        return null
    }

    fun processTick(
        tick: Long,
        casm: StyleCasm,
        currentSectionName: String,
        currentChord: Chord
    ): Pair<List<GeneratedMidiEvent>, PlaybackMetrics> {
        val startTickUs = System.nanoTime() / 1000
        val metrics = PlaybackMetrics()
        val generatedEvents = mutableListOf<GeneratedMidiEvent>()

        val startScheduler = System.nanoTime() / 1000
        val trackEvents = scheduler.getEventsForTick(tick)
        metrics.schedulerUs = (System.nanoTime() / 1000) - startScheduler

        var totalChordEngineUs = 0L

        for ((track, events) in trackEvents) {
            val casmSection = resolveSectionSafe(casm, currentSectionName)

            for (event in events) {
                val eventChannel = when (event) {
                    is MidiEvent.NoteOn -> event.channel
                    is MidiEvent.NoteOff -> event.channel
                    is MidiEvent.ControlChange -> event.channel
                    is MidiEvent.ProgramChange -> event.channel
                    is MidiEvent.PitchBend -> event.channel
                    else -> track.casmChannelIndex
                }

                val casmChannel = casmSection?.channels?.find { it.sourceChannel == eventChannel }
                val destChannel = casmChannel?.destChannel ?: eventChannel

                if (destChannel == 0xFF) {
                    continue // Muted by CASM routing
                }

                val sourceChord = if (casmChannel != null) getSourceChord(casmChannel) else currentChord

                when (event) {
                    is MidiEvent.NoteOn -> {
                        if (casmChannel != null) {
                            val startChord = System.nanoTime() / 1000
                            val transposed = chordEngine.transpose(
                                originalNote = event.note,
                                sourceChannel = eventChannel,
                                destChannel = destChannel,
                                ntr = casmChannel.ntr,
                                ntt = casmChannel.ntt,
                                sourceChord = sourceChord,
                                targetChord = currentChord,
                                highKey = casmChannel.highKey,
                                noteLimitLow = casmChannel.noteLowLimit,
                                noteLimitHigh = casmChannel.noteHighLimit
                            )
                            totalChordEngineUs += (System.nanoTime() / 1000) - startChord

                            val activeKey = ActiveNoteKey(eventChannel, event.note)
                            val finalNote = if (event.velocity > 0) {
                                activeNotes[activeKey] = transposed.generatedNote
                                transposed.generatedNote
                            } else {
                                // Velocity 0 acts as NoteOff
                                activeNotes.remove(activeKey) ?: transposed.generatedNote
                            }

                            generatedEvents.add(
                                GeneratedMidiEvent(
                                    tick = tick,
                                    trackName = track.name,
                                    channel = destChannel,
                                    eventType = "NoteOn",
                                    note = finalNote,
                                    originalNote = event.note,
                                    velocity = event.velocity,
                                    ntrApplied = casmChannel.ntr.displayName,
                                    nttApplied = casmChannel.ntt.displayName
                                )
                            )
                        } else {
                            // Bypass
                            val activeKey = ActiveNoteKey(eventChannel, event.note)
                            val finalNote = if (event.velocity > 0) {
                                activeNotes[activeKey] = event.note
                                event.note
                            } else {
                                activeNotes.remove(activeKey) ?: event.note
                            }
                            generatedEvents.add(
                                GeneratedMidiEvent(
                                    tick = tick,
                                    trackName = track.name,
                                    channel = destChannel,
                                    eventType = "NoteOn",
                                    note = finalNote,
                                    originalNote = event.note,
                                    velocity = event.velocity
                                )
                            )
                        }
                    }
                    is MidiEvent.NoteOff -> {
                        val activeKey = ActiveNoteKey(eventChannel, event.note)
                        val noteOffValue = activeNotes.remove(activeKey)

                        if (casmChannel != null) {
                            val finalNoteOff = noteOffValue ?: chordEngine.transpose(
                                originalNote = event.note,
                                sourceChannel = eventChannel,
                                destChannel = destChannel,
                                ntr = casmChannel.ntr,
                                ntt = casmChannel.ntt,
                                sourceChord = sourceChord,
                                targetChord = currentChord,
                                highKey = casmChannel.highKey,
                                noteLimitLow = casmChannel.noteLowLimit,
                                noteLimitHigh = casmChannel.noteHighLimit
                            ).generatedNote
                            generatedEvents.add(
                                GeneratedMidiEvent(
                                    tick = tick,
                                    trackName = track.name,
                                    channel = destChannel,
                                    eventType = "NoteOff",
                                    note = finalNoteOff,
                                    originalNote = event.note,
                                    velocity = event.velocity,
                                    ntrApplied = casmChannel.ntr.displayName,
                                    nttApplied = casmChannel.ntt.displayName
                                )
                            )
                        } else {
                            generatedEvents.add(
                                GeneratedMidiEvent(
                                    tick = tick,
                                    trackName = track.name,
                                    channel = destChannel,
                                    eventType = "NoteOff",
                                    note = noteOffValue ?: event.note,
                                    originalNote = event.note,
                                    velocity = event.velocity
                                )
                            )
                        }
                    }
                    is MidiEvent.ControlChange -> {
                        generatedEvents.add(
                            GeneratedMidiEvent(
                                tick = tick,
                                trackName = track.name,
                                channel = destChannel,
                                eventType = "CC",
                                ccController = event.controller,
                                ccValue = event.value
                            )
                        )
                    }
                    is MidiEvent.ProgramChange -> {
                        generatedEvents.add(
                            GeneratedMidiEvent(
                                tick = tick,
                                trackName = track.name,
                                channel = destChannel,
                                eventType = "ProgramChange",
                                program = event.program
                            )
                        )
                    }
                    is MidiEvent.PitchBend -> {
                        generatedEvents.add(
                            GeneratedMidiEvent(
                                tick = tick,
                                trackName = track.name,
                                channel = destChannel,
                                eventType = "PitchBend",
                                pitchBendValue = event.value
                            )
                        )
                    }
                    is MidiEvent.SysEx -> {
                        generatedEvents.add(
                            GeneratedMidiEvent(
                                tick = tick,
                                trackName = track.name,
                                channel = destChannel,
                                eventType = "SysEx",
                                sysExData = event.data
                            )
                        )
                    }
                    else -> {}
                }
            }
        }

        metrics.playbackEngineUs = (System.nanoTime() / 1000) - startTickUs
        metrics.chordDetectionUs = totalChordEngineUs
        metrics.generatedEventsCount = generatedEvents.size
        metrics.totalTickUs = (System.nanoTime() / 1000) - startTickUs

        return Pair(generatedEvents, metrics)
    }
}
