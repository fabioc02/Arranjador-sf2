package com.example.data.parser

import com.example.domain.models.MidiEvent
import java.nio.ByteBuffer

class MidiTrackParser {
    fun parseTrack(buffer: ByteBuffer, trackIndex: Int, markerParser: MarkerParser): Pair<Int, Long> {
        val events = parseTrackEvents(buffer, trackIndex, markerParser)
        return Pair(events.size, events.lastOrNull()?.tick ?: 0L)
    }

    fun parseTrackEvents(buffer: ByteBuffer, trackIndex: Int, markerParser: MarkerParser): List<MidiEvent> {
        val magic = ByteArray(4)
        buffer.get(magic)
        if (!magic.contentEquals("MTrk".toByteArray())) {
            throw ParserException("Invalid Track Header: MTrk not found at track $trackIndex")
        }
        
        val trackLength = buffer.int
        val endPosition = buffer.position() + trackLength
        
        var runningStatus: Int = 0
        var currentTick: Long = 0
        val events = mutableListOf<MidiEvent>()
        
        while (buffer.position() < endPosition) {
            val deltaTime = readVarInt(buffer)
            currentTick += deltaTime
            
            buffer.mark()
            var statusByte = buffer.get().toInt() and 0xFF
            
            if (statusByte < 0x80) {
                statusByte = runningStatus
                buffer.reset()
            } else {
                runningStatus = statusByte
            }
            
            when (statusByte) {
                0xFF -> { // Meta Event
                    val metaType = buffer.get().toInt() and 0xFF
                    val length = readVarInt(buffer)
                    val data = ByteArray(length)
                    buffer.get(data)
                    markerParser.processMetaEvent(metaType, data, currentTick)
                    events.add(MidiEvent.Meta(currentTick, metaType, data))
                }
                0xF0, 0xF7 -> { // SysEx
                    val length = readVarInt(buffer)
                    val data = ByteArray(length)
                    buffer.get(data)
                    events.add(MidiEvent.SysEx(currentTick, data))
                }
                else -> {
                    // MIDI Channel Event
                    val eventType = statusByte and 0xF0
                    val channel = statusByte and 0x0F
                    when (eventType) {
                        0x80 -> {
                            val note = buffer.get().toInt() and 0xFF
                            val velocity = buffer.get().toInt() and 0xFF
                            events.add(MidiEvent.NoteOff(currentTick, channel, note, velocity))
                        }
                        0x90 -> {
                            val note = buffer.get().toInt() and 0xFF
                            val velocity = buffer.get().toInt() and 0xFF
                            if (velocity > 0) {
                                events.add(MidiEvent.NoteOn(currentTick, channel, note, velocity))
                            } else {
                                events.add(MidiEvent.NoteOff(currentTick, channel, note, 0))
                            }
                        }
                        0xB0 -> {
                            val cc = buffer.get().toInt() and 0xFF
                            val value = buffer.get().toInt() and 0xFF
                            events.add(MidiEvent.ControlChange(currentTick, channel, cc, value))
                        }
                        0xC0 -> {
                            val pc = buffer.get().toInt() and 0xFF
                            events.add(MidiEvent.ProgramChange(currentTick, channel, pc))
                        }
                        0xE0 -> {
                            val lsb = buffer.get().toInt() and 0xFF
                            val msb = buffer.get().toInt() and 0xFF
                            val value = (msb shl 7) or lsb
                            events.add(MidiEvent.PitchBend(currentTick, channel, value))
                        }
                        0xD0 -> {
                            buffer.get() // Channel Pressure
                        }
                        0xA0 -> {
                            buffer.get(); buffer.get() // Polyphonic Aftertouch
                        }
                    }
                }
            }
        }
        
        buffer.position(endPosition)
        return events
    }
    
    private fun readVarInt(buffer: ByteBuffer): Int {
        var value = 0
        var b: Int
        do {
            b = buffer.get().toInt() and 0xFF
            value = (value shl 7) or (b and 0x7F)
        } while ((b and 0x80) != 0)
        return value
    }
}
