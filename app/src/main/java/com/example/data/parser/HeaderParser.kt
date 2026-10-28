package com.example.data.parser

import java.nio.ByteBuffer

data class MidiHeader(
    val format: Int,
    val tracks: Int,
    val division: Int
)

class ParserException(message: String) : Exception(message)

class HeaderParser {
    fun parse(buffer: ByteBuffer): MidiHeader {
        val magic = ByteArray(4)
        buffer.get(magic)
        if (!magic.contentEquals("MThd".toByteArray())) {
            throw ParserException("Invalid MIDI Header: MThd not found")
        }
        
        val headerLength = buffer.int
        if (headerLength < 6) {
            throw ParserException("Invalid MIDI Header length: $headerLength")
        }
        
        val format = buffer.short.toInt()
        val tracks = buffer.short.toInt()
        val division = buffer.short.toInt()
        
        // Skip any remaining bytes in the header chunk
        if (headerLength > 6) {
            buffer.position(buffer.position() + (headerLength - 6))
        }
        
        return MidiHeader(format, tracks, division)
    }
}
