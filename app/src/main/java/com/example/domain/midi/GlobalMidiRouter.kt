package com.example.domain.midi

import com.example.MidiState
import com.example.domain.models.VoiceEngine

object GlobalMidiRouter {
    /**
     * For hardware workstations like the Yamaha PSR-S750, Local Control is ON, so the keyboard
     * generates sound natively. Automatic MIDI echo to output produces double notes, comb filtering,
     * and phase distortion. Therefore, live MIDI echo is disabled by default.
     */
    var enableLiveMidiEcho: Boolean = false

    var overrideRightHandChannel: Int? = null

    fun start() {
        MidiState.midiListeners.add { status, data1, data2 ->
            // Deliver event to Chord Engine (Arranger) - Passive flow, no MIDI OUT is generated here.

            val command = status and 0xF0
            val channel = status and 0x0F
            val isNoteOn = command == 0x90 && data2 > 0
            val isNoteOff = command == 0x80 || (command == 0x90 && data2 == 0)
            
            if (isNoteOn || isNoteOff || command == 0xB0 || command == 0xE0 || command == 0xD0) {
                if (MidiState.isArrangerMode && (isNoteOn || isNoteOff)) {
                    val physicalNote = data1 - MidiState.keyboardTranspose
                    val isLeftHand = channel == 2 || channel == 3 || (channel <= 1 && physicalNote <= MidiState.splitPoint)

                    if (isNoteOff) {
                        val idx = com.example.MidiRhythmState.activeArrangerNotes.indexOf(data1)
                        if (idx != -1) com.example.MidiRhythmState.activeArrangerNotes.removeAt(idx)
                    } else if (isNoteOn && isLeftHand) {
                        if (!com.example.MidiRhythmState.activeArrangerNotes.contains(data1)) com.example.MidiRhythmState.activeArrangerNotes.add(data1)
                        if (com.example.MidiRhythmState.activeArrangerNotes.isNotEmpty()) {
                            val newChord = com.example.ArrangerEngine.detectChord(com.example.MidiRhythmState.activeArrangerNotes)
                            if (newChord != null) {
                                com.example.MidiRhythmState.detectedChord = newChord
                            }
                        }
                    }

                }

                val recCh = overrideRightHandChannel
                val isTrackSoundFont = recCh != null && (
                    com.example.MidiState.voiceManager?.isChannelSoundFont(recCh) == true ||
                    com.example.MidiRhythmState.tracks.any { it.channel - 1 == recCh && it.soundFontVoice != null }
                )
                val isSoundFontMode = MidiState.soundOutputEngine == com.example.domain.soundfont.model.SoundOutputEngine.SOUNDFONT_INTERNAL ||
                    MidiState.soundOutputEngine == com.example.domain.soundfont.model.SoundOutputEngine.HYBRID_BOTH ||
                    isTrackSoundFont

                if (recCh != null && (enableLiveMidiEcho || com.example.MidiRhythmState.isLocalControlOff || isSoundFontMode)) {
                    // When Local Control is OFF on the PSR-S750 or in SoundFont mode or track is SoundFont,
                    // the app routes incoming notes to the selected track channel so the user hears the track voice live!
                    if (channel != recCh || isSoundFontMode) {
                        if (isNoteOn) com.example.domain.midi_core.MidiDispatcher.sendNoteOn(recCh, data1, data2)
                        else if (isNoteOff) com.example.domain.midi_core.MidiDispatcher.sendNoteOff(recCh, data1, data2)
                        else if (command == 0xB0) com.example.domain.midi_core.MidiDispatcher.sendControlChange(recCh, data1, data2)
                        else if (command == 0xE0) com.example.domain.midi_core.MidiDispatcher.sendPitchBend(recCh, data1, data2)
                        else if (command == 0xD0) MidiState.sendChannelPressure(recCh, data1)
                    }
                } else {
                    // When a part/channel is assigned to a SoundFont (e.g. Right 1, Right 2, Left),
                    // or in SoundFont mode with Local Control OFF, dispatch incoming notes to SoundFont synth!
                    val isChannelSoundFont = com.example.MidiState.voiceManager?.isChannelSoundFont(channel) == true ||
                        com.example.MidiRhythmState.tracks.any { it.channel - 1 == channel && it.soundFontVoice != null }
                    if (isChannelSoundFont || (isSoundFontMode && (enableLiveMidiEcho || com.example.MidiRhythmState.isLocalControlOff))) {
                        if (isNoteOn) com.example.domain.midi_core.MidiDispatcher.sendNoteOn(channel, data1, data2)
                        else if (isNoteOff) com.example.domain.midi_core.MidiDispatcher.sendNoteOff(channel, data1, data2)
                        else if (command == 0xB0) com.example.domain.midi_core.MidiDispatcher.sendControlChange(channel, data1, data2)
                        else if (command == 0xE0) com.example.domain.midi_core.MidiDispatcher.sendPitchBend(channel, data1, data2)
                        else if (command == 0xD0) MidiState.sendChannelPressure(channel, data1)
                    }
                }
            }
        }
    }
}
