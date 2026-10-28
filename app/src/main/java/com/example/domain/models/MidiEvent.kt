package com.example.domain.models

sealed class MidiEvent {
    abstract val tick: Long

    data class NoteOn(override val tick: Long, val channel: Int, val note: Int, val velocity: Int) : MidiEvent()
    data class NoteOff(override val tick: Long, val channel: Int, val note: Int, val velocity: Int) : MidiEvent()
    data class ControlChange(override val tick: Long, val channel: Int, val controller: Int, val value: Int) : MidiEvent()
    data class ProgramChange(override val tick: Long, val channel: Int, val program: Int) : MidiEvent()
    data class PitchBend(override val tick: Long, val channel: Int, val value: Int) : MidiEvent()
    data class SysEx(override val tick: Long, val data: ByteArray) : MidiEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SysEx
            if (tick != other.tick) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = tick.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
    data class Meta(override val tick: Long, val type: Int, val data: ByteArray) : MidiEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Meta
            if (tick != other.tick) return false
            if (type != other.type) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = tick.hashCode()
            result = 31 * result + type
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}
