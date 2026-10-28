package com.example.domain.midi

import com.example.domain.models.MidiEvent
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class MidiFileWriter {
    fun writeToBytes(events: List<MidiEvent>, ppq: Int, tempoBpm: Double = 120.0): ByteArray {
        val out = ByteArrayOutputStream()
        
        // MThd
        out.write("MThd".toByteArray())
        out.write(byteArrayOf(0, 0, 0, 6)) // length
        out.write(byteArrayOf(0, 0)) // type 0
        out.write(byteArrayOf(0, 1)) // 1 track
        out.write(byteArrayOf((ppq shr 8).toByte(), (ppq and 0xFF).toByte())) // division
        
        // MTrk
        val trackData = ByteArrayOutputStream()
        
        // Tempo meta event at tick 0
        writeVarInt(trackData, 0)
        trackData.write(0xFF)
        trackData.write(0x51)
        trackData.write(0x03)
        val microsecondsPerBeat = (60_000_000 / tempoBpm).toInt()
        trackData.write((microsecondsPerBeat shr 16) and 0xFF)
        trackData.write((microsecondsPerBeat shr 8) and 0xFF)
        trackData.write(microsecondsPerBeat and 0xFF)
        
        var lastTick = 0L
        for (event in events.sortedBy { it.tick }) {
            val delta = (event.tick - lastTick).toInt()
            writeVarInt(trackData, delta)
            lastTick = event.tick
            
            when (event) {
                is MidiEvent.NoteOn -> {
                    trackData.write(0x90 or event.channel)
                    trackData.write(event.note)
                    trackData.write(event.velocity)
                }
                is MidiEvent.NoteOff -> {
                    trackData.write(0x80 or event.channel)
                    trackData.write(event.note)
                    trackData.write(event.velocity)
                }
                is MidiEvent.ControlChange -> {
                    trackData.write(0xB0 or event.channel)
                    trackData.write(event.controller)
                    trackData.write(event.value)
                }
                is MidiEvent.ProgramChange -> {
                    trackData.write(0xC0 or event.channel)
                    trackData.write(event.program)
                }
                is MidiEvent.PitchBend -> {
                    trackData.write(0xE0 or event.channel)
                    trackData.write(event.value and 0x7F)
                    trackData.write((event.value shr 7) and 0x7F)
                }
                is MidiEvent.Meta -> {
                    trackData.write(0xFF)
                    trackData.write(event.type)
                    writeVarInt(trackData, event.data.size)
                    trackData.write(event.data)
                }
                is MidiEvent.SysEx -> {
                    // Usually F0 <len> <data>
                    trackData.write(0xF0)
                    writeVarInt(trackData, event.data.size)
                    trackData.write(event.data)
                }
            }
        }
        
        // End of track meta event
        writeVarInt(trackData, 0)
        trackData.write(0xFF)
        trackData.write(0x2F)
        trackData.write(0x00)
        
        val trackBytes = trackData.toByteArray()
        out.write("MTrk".toByteArray())
        val lenBuf = ByteBuffer.allocate(4).putInt(trackBytes.size).array()
        out.write(lenBuf)
        out.write(trackBytes)
        
        return out.toByteArray()
    }
    
    private fun writeVarInt(out: ByteArrayOutputStream, value: Int) {
        var buffer = value and 0x7F
        var v = value shr 7
        while (v > 0) {
            buffer = (buffer shl 8) or 0x80 or (v and 0x7F)
            v = v shr 7
        }
        while (true) {
            out.write(buffer and 0xFF)
            if ((buffer and 0x80) != 0) {
                buffer = buffer ushr 8
            } else {
                break
            }
        }
    }
}
