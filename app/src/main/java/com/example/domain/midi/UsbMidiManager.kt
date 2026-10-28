package com.example.domain.midi

import android.media.midi.MidiManager
import android.media.midi.MidiDeviceInfo

class UsbMidiManager(private val midiManager: MidiManager?) {
    fun getAvailableDevices(): List<MidiDeviceInfo> {
        return midiManager?.devices?.toList() ?: emptyList()
    }
    
    // Placeholder for actual connection logic using android.media.midi
    fun connectDevice(device: MidiDeviceInfo) {
        
    }
}
