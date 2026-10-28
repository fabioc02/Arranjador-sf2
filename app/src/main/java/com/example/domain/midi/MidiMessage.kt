package com.example.domain.midi

data class MidiMessage(
    val timestamp: Long,
    val type: MidiMessageType,
    val channel: Int,
    val data1: Int,
    val data2: Int = 0,
    val sysExData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MidiMessage

        if (timestamp != other.timestamp) return false
        if (type != other.type) return false
        if (channel != other.channel) return false
        if (data1 != other.data1) return false
        if (data2 != other.data2) return false
        if (sysExData != null) {
            if (other.sysExData == null) return false
            if (!sysExData.contentEquals(other.sysExData)) return false
        } else if (other.sysExData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + channel
        result = 31 * result + data1
        result = 31 * result + data2
        result = 31 * result + (sysExData?.contentHashCode() ?: 0)
        return result
    }
}

enum class MidiMessageType {
    NoteOn, NoteOff, ControlChange, ProgramChange, PitchBend, SysEx, Clock, Start, Continue, Stop
}
