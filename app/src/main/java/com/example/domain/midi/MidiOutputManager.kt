package com.example.domain.midi

import com.example.domain.playback.GeneratedMidiEvent

class MidiOutputManager(private val midiEventQueue: MidiEventQueue? = null) {
    fun sendGeneratedEvents(events: List<GeneratedMidiEvent>) {
        if (midiEventQueue == null) return
        
        for (event in events) {
            val midiType = when(event.eventType) {
                "NoteOn" -> MidiMessageType.NoteOn
                "NoteOff" -> MidiMessageType.NoteOff
                "CC" -> MidiMessageType.ControlChange
                "ProgramChange" -> MidiMessageType.ProgramChange
                "PitchBend" -> MidiMessageType.PitchBend
                "SysEx" -> MidiMessageType.SysEx
                else -> null
            }

            if (midiType != null) {
                val data1 = when (midiType) {
                    MidiMessageType.ControlChange -> event.ccController
                    MidiMessageType.ProgramChange -> event.program
                    MidiMessageType.PitchBend -> event.pitchBendValue
                    else -> event.note
                }
                val data2 = when (midiType) {
                    MidiMessageType.ControlChange -> event.ccValue
                    MidiMessageType.ProgramChange -> 0
                    MidiMessageType.PitchBend -> 0
                    else -> event.velocity
                }

                val msg = MidiMessage(System.nanoTime(), midiType, event.channel, data1, data2, sysExData = event.sysExData)
                midiEventQueue.offer(msg)
            }
        }
    }
}
