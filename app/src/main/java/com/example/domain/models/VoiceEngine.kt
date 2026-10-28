package com.example.domain.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.MidiState

class ChannelVoiceState(val channel: Int) {
    var msb by mutableStateOf(0)
    var lsb by mutableStateOf(113)
    var pc by mutableStateOf(0)
    var voiceName by mutableStateOf("GrandPiano")
    var volume by mutableStateOf(100)
    var pan by mutableStateOf(64)
    var reverb by mutableStateOf(40)
    var chorus by mutableStateOf(0)
    var dsp by mutableStateOf(0)
    var isEnabled by mutableStateOf(true)

    // Interchangeable mode (PSR-S750 hardware vs SoundFonts)
    var isSoundFontMode by mutableStateOf(false)
    var soundFontVoice by mutableStateOf<com.example.domain.soundfont.model.SoundFontVoice?>(null)

    fun sendVoiceConfigToDevice() {
        if (!isEnabled) return
        if (isSoundFontMode && soundFontVoice != null) {
            val sf = soundFontVoice!!
            MidiState.soundFontSynth.setChannelVoice(channel, sf)
            MidiState.voiceManager?.setSoundFontMapping(channel, sf)
            MidiState.soundFontSynth.controlChange(channel, 7, volume)
            MidiState.soundFontSynth.controlChange(channel, 10, pan)
            MidiState.soundFontSynth.controlChange(channel, 91, reverb)
            MidiState.soundFontSynth.controlChange(channel, 93, chorus)
            MidiState.soundFontSynth.controlChange(channel, 94, dsp)
        } else {
            MidiState.voiceManager?.clearMapping(channel)
            MidiState.soundFontSynth.setChannelVoice(channel, null)
            MidiState.sendProgramChange(channel, msb, lsb, pc)
            MidiState.sendControlChange(channel, 7, volume)
            MidiState.sendControlChange(channel, 10, pan)
            MidiState.sendControlChange(channel, 91, reverb)
            MidiState.sendControlChange(channel, 93, chorus)
            MidiState.sendControlChange(channel, 94, dsp)
        }
    }

    fun sendControlChange(cc: Int, value: Int) {
        if (isSoundFontMode && soundFontVoice != null) {
            MidiState.soundFontSynth.controlChange(channel, cc, value)
        }
        MidiState.sendControlChange(channel, cc, value)
    }
}

object VoiceEngine {
    val channels = Array(16) { ChannelVoiceState(it) }

    // Variables for standard parts (configurable)
    var CH_RIGHT1 by mutableStateOf(0) // Channel 1 (0-based)
    var CH_RIGHT2 by mutableStateOf(1) // Channel 2 (0-based)
    var CH_LEFT by mutableStateOf(2)   // Channel 3 (0-based)

    init {
        // Init default voices for PSR-S750
        channels[CH_RIGHT1].msb = 0
        channels[CH_RIGHT1].lsb = 113
        channels[CH_RIGHT1].pc = 0 // GrandPiano
        channels[CH_RIGHT1].voiceName = "GrandPiano"

        channels[CH_RIGHT2].msb = 0
        channels[CH_RIGHT2].lsb = 114
        channels[CH_RIGHT2].pc = 0 // WarmGrand
        channels[CH_RIGHT2].voiceName = "WarmGrand"

        channels[CH_LEFT].msb = 0
        channels[CH_LEFT].lsb = 112
        channels[CH_LEFT].pc = 32 // AcousticBass
        channels[CH_LEFT].voiceName = "AcousticBass"
    }
    
    fun sendAllToDevice() {
        for (c in channels) {
            c.sendVoiceConfigToDevice()
        }
    }
    
    fun mutePanelParts() {
        // Safe no-op: PSR-S750 ignores CC 122 for keyboard parts, and sending CC 7/11 = 0
        // chokes synthesizer envelopes causing squawking/distorted transients.
    }
    
    fun restorePanelParts() {
        // Restore standard full expression and configured volumes
        for (i in 0..3) {
            MidiState.sendControlChange(i, 7, channels[i].volume)
            MidiState.sendControlChange(i, 11, 127)
        }
    }
}
