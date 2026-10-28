package com.example.domain.audio

import com.example.domain.midi.MidiMessage

data class DiagnosticState(
    var lastCallbackTimeUs: Long = 0,
    var lastMidiEvent: MidiMessage? = null,
    var lastChord: String = "None",
    var lastSection: String = "None",
    var lastTick: Long = 0,
    var lastBpm: Int = 120,
    var lastProgramChange: Int = -1,
    var lastSysEx: ByteArray? = null
)

class DiagnosticEngine {
    val state = DiagnosticState()

    fun logCallback(timeUs: Long) { state.lastCallbackTimeUs = timeUs }
    fun logMidiEvent(event: MidiMessage) { state.lastMidiEvent = event }
    fun logChord(chord: String) { state.lastChord = chord }
    fun logSection(section: String) { state.lastSection = section }
    fun logTick(tick: Long) { state.lastTick = tick }
    fun logBpm(bpm: Int) { state.lastBpm = bpm }
    fun logProgramChange(pc: Int) { state.lastProgramChange = pc }
    fun logSysEx(sysEx: ByteArray) { state.lastSysEx = sysEx.clone() }
    
    fun dumpCrashReport(): String {
        return """
            Crash Report:
            Last Callback: ${state.lastCallbackTimeUs} us
            Last MIDI: ${state.lastMidiEvent}
            Last Chord: ${state.lastChord}
            Last Section: ${state.lastSection}
            Last Tick: ${state.lastTick}
            Last BPM: ${state.lastBpm}
            Last PC: ${state.lastProgramChange}
        """.trimIndent()
    }
}
