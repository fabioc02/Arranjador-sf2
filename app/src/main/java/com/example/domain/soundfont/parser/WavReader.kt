package com.example.domain.soundfont.parser

import com.example.domain.soundfont.model.SampleBuffer
import com.example.domain.soundfont.model.StereoChannel
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lightweight, zero-external-dependency PCM WAV reader.
 * Supports 16-bit and 24-bit PCM, Mono and Stereo, 44.1kHz / 48kHz.
 */
object WavReader {

    fun readWav(file: File, sampleId: Int = 0, defaultRootKey: Int = 60): SampleBuffer {
        return file.inputStream().use { stream ->
            readWav(stream, file.nameWithoutExtension, sampleId, defaultRootKey)
        }
    }

    fun readWav(stream: InputStream, name: String, sampleId: Int = 0, defaultRootKey: Int = 60): SampleBuffer {
        val bytes = stream.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Validate RIFF header
        val riff = readAscii(buffer, 4)
        require(riff == "RIFF") { "Not a valid RIFF file: $riff" }
        buffer.int // chunkSize

        val wave = readAscii(buffer, 4)
        require(wave == "WAVE") { "Not a valid WAVE file: $wave" }

        var channels = 1
        var sampleRate = 44100
        var bitsPerSample = 16
        var pcmData: FloatArray? = null
        var isLooped = false
        var loopStart = 0
        var loopEnd = 0
        var rootKey = defaultRootKey

        // Parse chunks
        while (buffer.remaining() >= 8) {
            val chunkId = readAscii(buffer, 4)
            val chunkSize = buffer.int
            val nextChunkPos = buffer.position() + chunkSize

            when (chunkId) {
                "fmt " -> {
                    val formatCode = buffer.short.toInt() and 0xFFFF
                    channels = buffer.short.toInt() and 0xFFFF
                    sampleRate = buffer.int
                    buffer.int // byteRate
                    buffer.short // blockAlign
                    bitsPerSample = buffer.short.toInt() and 0xFFFF
                    // Ignore extra fmt bytes if any
                }
                "data" -> {
                    pcmData = readPcmSamples(buffer, chunkSize, channels, bitsPerSample)
                }
                "smpl" -> {
                    // Sampler chunk (for loops and root key)
                    if (chunkSize >= 36) {
                        buffer.int // manufacturer
                        buffer.int // product
                        buffer.int // samplePeriod
                        val unityNote = buffer.int
                        if (unityNote in 0..127) {
                            rootKey = unityNote
                        }
                        buffer.int // pitchFraction
                        buffer.int // SMPTE format
                        buffer.int // SMPTE offset
                        val numLoops = buffer.int
                        buffer.int // samplerData
                        if (numLoops > 0 && buffer.remaining() >= 24) {
                            buffer.int // cuePointId
                            buffer.int // type
                            loopStart = buffer.int
                            loopEnd = buffer.int
                            buffer.int // fraction
                            buffer.int // playCount
                            isLooped = loopEnd > loopStart
                        }
                    }
                }
            }

            if (nextChunkPos in 0..buffer.capacity()) {
                buffer.position(nextChunkPos)
            } else {
                break
            }
        }

        requireNotNull(pcmData) { "WAV file does not contain a 'data' chunk" }

        val stereoChannel = when {
            name.endsWith("_L", ignoreCase = true) -> StereoChannel.LEFT
            name.endsWith("_R", ignoreCase = true) -> StereoChannel.RIGHT
            else -> StereoChannel.MONO
        }

        return SampleBuffer(
            id = sampleId,
            name = name,
            data = pcmData,
            sampleRate = sampleRate,
            rootKey = rootKey,
            tuningCents = 0,
            isLooped = isLooped,
            loopStart = loopStart,
            loopEnd = if (loopEnd > 0) loopEnd else pcmData.size,
            stereoChannel = stereoChannel
        )
    }

    private fun readPcmSamples(
        buffer: ByteBuffer,
        chunkSize: Int,
        channels: Int,
        bitsPerSample: Int
    ): FloatArray {
        val bytesPerSample = bitsPerSample / 8
        val totalSamples = chunkSize / bytesPerSample
        val monoSamples = totalSamples / channels
        val result = FloatArray(monoSamples)

        val pos = buffer.position()
        when (bitsPerSample) {
            16 -> {
                for (i in 0 until monoSamples) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        val sample16 = buffer.short
                        sum += sample16 / 32768.0f
                    }
                    result[i] = sum / channels
                }
            }
            24 -> {
                for (i in 0 until monoSamples) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        val b0 = buffer.get().toInt() and 0xFF
                        val b1 = buffer.get().toInt() and 0xFF
                        val b2 = buffer.get().toInt() // signed high byte
                        val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                        sum += sample24 / 8388608.0f
                    }
                    result[i] = sum / channels
                }
            }
            8 -> {
                for (i in 0 until monoSamples) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        val sample8 = (buffer.get().toInt() and 0xFF) - 128
                        sum += sample8 / 128.0f
                    }
                    result[i] = sum / channels
                }
            }
            else -> {
                // Fallback for 32-bit float or other formats
                for (i in 0 until monoSamples) {
                    result[i] = 0f
                }
            }
        }
        return result
    }

    private fun readAscii(buffer: ByteBuffer, length: Int): String {
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return String(bytes, Charsets.US_ASCII)
    }
}
