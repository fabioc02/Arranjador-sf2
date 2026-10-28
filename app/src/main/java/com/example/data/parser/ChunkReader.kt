package com.example.data.parser

import java.nio.ByteBuffer

data class ChunkInfo(val name: String, val offset: Long, val size: Long)

class ChunkReader {
    fun findChunks(buffer: ByteBuffer, startOffset: Int): List<ChunkInfo> {
        val chunks = mutableListOf<ChunkInfo>()
        buffer.position(startOffset)
        
        while (buffer.remaining() >= 8) {
            val offset = buffer.position().toLong()
            val magic = ByteArray(4)
            buffer.get(magic)
            
            val chunkName = String(magic, Charsets.US_ASCII)
            val length = buffer.int.toLong() and 0xFFFFFFFFL
            
            val isKnownChunk = chunkName == "CASM" || chunkName == "OTSc" || 
                               chunkName == "FNRc" || chunkName == "MHhd" ||
                               chunkName == "MTrk" || chunkName == "CSEG"
            
            if (isKnownChunk && length >= 0 && length <= buffer.remaining()) {
                ParserLogger.logInfo("Found Chunk: $chunkName at offset $offset with size $length")
                chunks.add(ChunkInfo(chunkName, offset, length))
                buffer.position(buffer.position() + length.toInt())
            } else {
                buffer.position((offset + 1).toInt())
            }
        }
        
        return chunks
    }
}
