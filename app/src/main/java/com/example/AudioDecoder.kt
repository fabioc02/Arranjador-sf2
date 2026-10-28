package com.example

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log

object AudioDecoder {
    class PCMResult(val data: ByteArray, val sampleRate: Int, val channels: Int)

    fun decodeToPCM(context: Context, uri: Uri): PCMResult? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            Log.e("AudioDecoder", "Failed to set data source", e)
            return null
        }

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                trackIndex = i
                format = f
                break
            }
        }

        if (trackIndex == -1 || format == null) {
            extractor.release()
            return null
        }

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        
        var sampleRate = 44100
        var channels = 2
        try {
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
        } catch (e: Exception) {}
        
        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            extractor.release()
            return null
        }

        try {
            codec.configure(format, null, null, 0)
            codec.start()
        } catch (e: Exception) {
            codec.release()
            extractor.release()
            return null
        }

        val pcmData = mutableListOf<ByteArray>()
        val info = MediaCodec.BufferInfo()
        var isEOS = false
        val timeoutUs = 10000L

        try {
            while (true) {
                if (!isEOS) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        if (buffer != null) {
                            val sampleSize = extractor.readSampleData(buffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                if (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outBuffer.position(info.offset)
                        outBuffer.get(chunk)
                        pcmData.add(chunk)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioDecoder", "Decoding loop error", e)
        }

        try {
            codec.stop()
            codec.release()
            extractor.release()
        } catch (e: Exception) {}

                if (pcmData.isEmpty()) return null
        
        var totalSize = 0
        for (chunk in pcmData) totalSize += chunk.size
        
        // Limit to 50MB of raw PCM to avoid OutOfMemoryError in Java
        val MAX_SIZE = 50 * 1024 * 1024
        if (totalSize > MAX_SIZE) {
            totalSize = MAX_SIZE
        }
        
        val finalPCM = try {
            ByteArray(totalSize)
        } catch (e: OutOfMemoryError) {
            return null
        }
        
        var offset = 0
        for (chunk in pcmData) {
            val toCopy = minOf(chunk.size, totalSize - offset)
            if (toCopy <= 0) break
            System.arraycopy(chunk, 0, finalPCM, offset, toCopy)
            offset += toCopy
        }
        
        return PCMResult(finalPCM, sampleRate, channels)
    }
}
