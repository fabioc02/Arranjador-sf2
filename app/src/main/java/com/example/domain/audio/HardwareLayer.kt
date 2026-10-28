package com.example.domain.audio

interface HardwareLayer {
    // MIDI Interfaces
    fun connectUsbMidi(deviceId: String): Boolean
    fun disconnectUsbMidi(deviceId: String)
    
    fun connectBluetoothMidi(deviceId: String): Boolean
    fun disconnectBluetoothMidi(deviceId: String)

    // Audio Interfaces
    fun connectUsbAudioInterface(deviceId: String): Boolean
    fun disconnectUsbAudioInterface(deviceId: String)

    // Hardware Controllers
    fun onExpressionPedalMoved(value: Int)
    fun onFootSwitchPressed(pedalId: Int)
    fun onFootSwitchReleased(pedalId: Int)
    fun onAssignableKnobTurned(knobId: Int, value: Int)
}
