package com.example.domain.midi_core

import com.example.domain.soundfont.model.SoundOutputEngine

/**
 * MIDI DISPATCHER CORE
 * Centralized routing hub supporting Yamaha PSR-S750 hardware and SoundFont internal synthesizer.
 */
object MidiDispatcher {
    
    private fun isSoundFontChannel(channel: Int): Boolean {
        if (com.example.MidiState.soundOutputEngine == SoundOutputEngine.YAMAHA_HARDWARE) {
            return false
        }
        if (com.example.MidiState.soundOutputEngine == SoundOutputEngine.SOUNDFONT_INTERNAL) {
            return true
        }
        // In HYBRID_BOTH mode:
        return com.example.MidiState.soundFontSynth.isChannelSoundFont(channel) ||
            com.example.MidiState.voiceManager?.isChannelSoundFont(channel) == true ||
            com.example.MidiRhythmState.tracks.any { it.channel - 1 == channel && it.soundFontVoice != null }
    }

    fun sendNoteOn(channel: Int, note: Int, velocity: Int, targetTimeNs: Long = 0L) {
        val engine = com.example.MidiState.soundOutputEngine

        when (engine) {
            SoundOutputEngine.YAMAHA_HARDWARE -> {
                // Modo Yamaha PSR-S750: Sempre envia para o teclado físico via USB-MIDI
                com.example.MidiState.sendNoteOn(channel, note, velocity, targetTimeNs)
            }
            SoundOutputEngine.SOUNDFONT_INTERNAL -> {
                // Modo SoundFont: Sempre envia para o sintetizador interno / C++ Oboe
                com.example.MidiState.soundFontSynth.noteOn(channel, note, velocity)
                com.example.domain.audio.NativeAudioBridge.noteOn(channel, note, velocity)
            }
            SoundOutputEngine.HYBRID_BOTH -> {
                val isSf = isSoundFontChannel(channel)
                if (isSf) {
                    com.example.MidiState.soundFontSynth.noteOn(channel, note, velocity)
                    com.example.domain.audio.NativeAudioBridge.noteOn(channel, note, velocity)
                } else {
                    com.example.MidiState.sendNoteOn(channel, note, velocity, targetTimeNs)
                }
            }
        }
    }

    fun sendNoteOff(channel: Int, note: Int, velocity: Int, targetTimeNs: Long = 0L) {
        val engine = com.example.MidiState.soundOutputEngine

        when (engine) {
            SoundOutputEngine.YAMAHA_HARDWARE -> {
                com.example.MidiState.sendNoteOff(channel, note, velocity, targetTimeNs)
            }
            SoundOutputEngine.SOUNDFONT_INTERNAL -> {
                com.example.MidiState.soundFontSynth.noteOff(channel, note)
                com.example.domain.audio.NativeAudioBridge.noteOff(channel, note)
            }
            SoundOutputEngine.HYBRID_BOTH -> {
                val isSf = isSoundFontChannel(channel)
                if (isSf) {
                    com.example.MidiState.soundFontSynth.noteOff(channel, note)
                    com.example.domain.audio.NativeAudioBridge.noteOff(channel, note)
                } else {
                    com.example.MidiState.sendNoteOff(channel, note, velocity, targetTimeNs)
                }
            }
        }
    }

    fun sendControlChange(channel: Int, cc: Int, value: Int, targetTimeNs: Long = 0L) {
        val engine = com.example.MidiState.soundOutputEngine

        when (engine) {
            SoundOutputEngine.YAMAHA_HARDWARE -> {
                com.example.MidiState.sendControlChange(channel, cc, value, targetTimeNs)
                if (isSoundFontChannel(channel)) {
                    com.example.MidiState.soundFontSynth.controlChange(channel, cc, value)
                    com.example.domain.audio.NativeAudioBridge.controlChange(channel, cc, value)
                }
            }
            SoundOutputEngine.SOUNDFONT_INTERNAL -> {
                com.example.MidiState.soundFontSynth.controlChange(channel, cc, value)
                com.example.domain.audio.NativeAudioBridge.controlChange(channel, cc, value)
            }
            SoundOutputEngine.HYBRID_BOTH -> {
                com.example.MidiState.sendControlChange(channel, cc, value, targetTimeNs)
                com.example.MidiState.soundFontSynth.controlChange(channel, cc, value)
                com.example.domain.audio.NativeAudioBridge.controlChange(channel, cc, value)
            }
        }
    }

    fun sendProgramChange(channel: Int, bankMsb: Int, bankLsb: Int, program: Int) {
        val engine = com.example.MidiState.soundOutputEngine

        when (engine) {
            SoundOutputEngine.YAMAHA_HARDWARE -> {
                com.example.MidiState.sendProgramChange(channel, bankMsb, bankLsb, program)
                if (isSoundFontChannel(channel)) {
                    com.example.MidiState.soundFontSynth.bankSelect(channel, bankMsb)
                    com.example.MidiState.soundFontSynth.programChange(channel, program)
                    com.example.domain.audio.NativeAudioBridge.programChange(channel, program)
                }
            }
            SoundOutputEngine.SOUNDFONT_INTERNAL -> {
                com.example.MidiState.soundFontSynth.bankSelect(channel, bankMsb)
                com.example.MidiState.soundFontSynth.programChange(channel, program)
                com.example.domain.audio.NativeAudioBridge.programChange(channel, program)
            }
            SoundOutputEngine.HYBRID_BOTH -> {
                com.example.MidiState.sendProgramChange(channel, bankMsb, bankLsb, program)
                com.example.MidiState.soundFontSynth.bankSelect(channel, bankMsb)
                com.example.MidiState.soundFontSynth.programChange(channel, program)
                com.example.domain.audio.NativeAudioBridge.programChange(channel, program)
            }
        }
    }

    fun sendPitchBend(channel: Int, lsb: Int, msb: Int, targetTimeNs: Long = 0L) {
        val engine = com.example.MidiState.soundOutputEngine
        val bendVal = (msb shl 7) or lsb

        when (engine) {
            SoundOutputEngine.YAMAHA_HARDWARE -> {
                com.example.MidiState.sendPitchBend(channel, lsb, msb, targetTimeNs)
                if (isSoundFontChannel(channel)) {
                    com.example.MidiState.soundFontSynth.pitchBend(channel, bendVal)
                    com.example.domain.audio.NativeAudioBridge.pitchBend(channel, bendVal)
                }
            }
            SoundOutputEngine.SOUNDFONT_INTERNAL -> {
                com.example.MidiState.soundFontSynth.pitchBend(channel, bendVal)
                com.example.domain.audio.NativeAudioBridge.pitchBend(channel, bendVal)
            }
            SoundOutputEngine.HYBRID_BOTH -> {
                com.example.MidiState.sendPitchBend(channel, lsb, msb, targetTimeNs)
                com.example.MidiState.soundFontSynth.pitchBend(channel, bendVal)
                com.example.domain.audio.NativeAudioBridge.pitchBend(channel, bendVal)
            }
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
