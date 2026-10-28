package com.example.domain.yamaha

data class XGChannelState(
    var program: Int = 0,
    var bankMsb: Int = 0,
    var bankLsb: Int = 0,
    var volume: Int = 100,
    var expression: Int = 127,
    var pan: Int = 64,
    var reverb: Int = 40,
    var chorus: Int = 0,
    var variation: Int = 0,
    var dspSend: Int = 0,
    var pitchBend: Int = 8192,
    var modulation: Int = 0,
    var channelPressure: Int = 0,
    var polyAftertouch: MutableMap<Int, Int> = mutableMapOf(),
    var sustain: Int = 0,
    var rpnMsb: Int = 127,
    var rpnLsb: Int = 127,
    var nrpnMsb: Int = 127,
    var nrpnLsb: Int = 127,
    var dataEntryMsb: Int = 0,
    var dataEntryLsb: Int = 0
)

class XGEngine {
    var masterVolume: Int = 127
    var masterTune: Int = 0
    val channels = Array(16) { XGChannelState() }
    
    fun reset() {
        masterVolume = 127
        masterTune = 0
        for (i in 0 until 16) {
            channels[i] = XGChannelState()
        }
    }
    
    fun processProgramChange(channel: Int, program: Int) {
        channels[channel].program = program
    }
    
    fun processBankSelect(channel: Int, msb: Int, lsb: Int) {
        channels[channel].bankMsb = msb
        channels[channel].bankLsb = lsb
    }
    
    fun processControlChange(channel: Int, cc: Int, value: Int) {
        when (cc) {
            1 -> channels[channel].modulation = value
            6 -> channels[channel].dataEntryMsb = value
            7 -> channels[channel].volume = value
            10 -> channels[channel].pan = value
            11 -> channels[channel].expression = value
            38 -> channels[channel].dataEntryLsb = value
            64 -> channels[channel].sustain = value
            91 -> channels[channel].reverb = value
            93 -> channels[channel].chorus = value
            94 -> channels[channel].variation = value
            98 -> channels[channel].nrpnLsb = value
            99 -> channels[channel].nrpnMsb = value
            100 -> channels[channel].rpnLsb = value
            101 -> channels[channel].rpnMsb = value
        }
    }
    
    fun processPitchBend(channel: Int, value: Int) {
        channels[channel].pitchBend = value
    }
    
    fun processChannelPressure(channel: Int, value: Int) {
        channels[channel].channelPressure = value
    }
    
    fun processPolyAftertouch(channel: Int, note: Int, value: Int) {
        channels[channel].polyAftertouch[note] = value
    }
}
