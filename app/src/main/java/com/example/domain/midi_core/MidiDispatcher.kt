package com.example.domain.midi_core

import com.example.domain.soundfont.model.SoundOutputEngine

/**
 * MIDI DISPATCHER CORE
 * Centralized routing hub supporting Yamaha PSR-S750 hardware and SoundFont internal synthesizer.
 */
object MidiDispatcher {
    
    fun sendNoteOn(channel: Int, note: Int, velocity: Int, targetTimeNs: Long = 0L) {
        val engine = com.example.MidiState.soundOutputEngine
        val isChannelSoundFont = com.example.MidiState.voiceManager?.isChannelSoundFont(channel) == true

        if ((engine == SoundOutputEngine.YAMAHA_HARDWARE || engine == SoundOutputEngine.HYBRID_BOTH) && !isChannelSoundFont) {
            com.example.MidiState.sendNoteOn(channel, note, velocity, targetTimeNs)
        }
        if (engine == SoundOutputEngine.SOUNDFONT_INTERNAL || engine == SoundOutputEngine.HYBRID_BOTH || isChannelSoundFont) {
            com.example.MidiState.soundFontSynth.noteOn(channel, note, velocity)
        }
    }

    fun sendNoteOff(channel: Int, note: Int, velocity: Int, targetTimeNs: Long = 0L) {
        val engine = com.example.MidiState.soundOutputEngine
        val isChannelSoundFont = com.example.MidiState.voiceManager?.isChannelSoundFont(channel) == true

        if ((engine == SoundOutputEngine.YAMAHA_HARDWARE || engine == SoundOutputEngine.HYBRID_BOTH) && !isChannelSoundFont) {
            com.example.MidiState.sendNoteOff(channel, note, velocity, targetTimeNs)
        }
        if (engine == SoundOutputEngine.SOUNDFONT_INTERNAL || engine == SoundOutputEngine.HYBRID_BOTH || isChannelSoundFont) {
            com.example.MidiState.soundFontSynth.noteOff(channel, note)
        }
    }

    fun sendControlChange(channel: Int, cc: Int, value: Int, targetTimeNs: Long = 0L) {
        val engine = com.example.MidiState.soundOutputEngine
        val isChannelSoundFont = com.example.MidiState.voiceManager?.isChannelSoundFont(channel) == true

        if (engine == SoundOutputEngine.YAMAHA_HARDWARE || engine == SoundOutputEngine.HYBRID_BOTH) {
            com.example.MidiState.sendControlChange(channel, cc, value, targetTimeNs)
        }
        if (engine == SoundOutputEngine.SOUNDFONT_INTERNAL || engine == SoundOutputEngine.HYBRID_BOTH || isChannelSoundFont) {
            com.example.MidiState.soundFontSynth.controlChange(channel, cc, value)
        }
    }

    fun sendProgramChange(channel: Int, bankMsb: Int, bankLsb: Int, program: Int) {
        val engine = com.example.MidiState.soundOutputEngine
        val isChannelSoundFont = com.example.MidiState.voiceManager?.isChannelSoundFont(channel) == true

        if (engine == SoundOutputEngine.YAMAHA_HARDWARE || engine == SoundOutputEngine.HYBRID_BOTH) {
            com.example.MidiState.sendProgramChange(channel, bankMsb, bankLsb, program)
        }
        if (engine == SoundOutputEngine.SOUNDFONT_INTERNAL || engine == SoundOutputEngine.HYBRID_BOTH || isChannelSoundFont) {
            com.example.MidiState.soundFontSynth.bankSelect(channel, bankMsb)
            com.example.MidiState.soundFontSynth.programChange(channel, program)
        }
    }

    fun sendPitchBend(channel: Int, lsb: Int, msb: Int, targetTimeNs: Long = 0L) {
        val engine = com.example.MidiState.soundOutputEngine
        val isChannelSoundFont = com.example.MidiState.voiceManager?.isChannelSoundFont(channel) == true

        if (engine == SoundOutputEngine.YAMAHA_HARDWARE || engine == SoundOutputEngine.HYBRID_BOTH) {
            com.example.MidiState.sendPitchBend(channel, lsb, msb, targetTimeNs)
        }
        if (engine == SoundOutputEngine.SOUNDFONT_INTERNAL || engine == SoundOutputEngine.HYBRID_BOTH || isChannelSoundFont) {
            val bendVal = (msb shl 7) or lsb
            com.example.MidiState.soundFontSynth.pitchBend(channel, bendVal)
        }
    }

    fun sendSysEx(data: ByteArray) {
        val engine = com.example.MidiState.soundOutputEngine
        if (engine == SoundOutputEngine.YAMAHA_HARDWARE || engine == SoundOutputEngine.HYBRID_BOTH) {
            com.example.MidiState.sendSysEx(data)
        }
        if (engine == SoundOutputEngine.SOUNDFONT_INTERNAL || engine == SoundOutputEngine.HYBRID_BOTH) {
            com.example.MidiState.soundFontSynth.sysEx(data)
        }
    }
}
