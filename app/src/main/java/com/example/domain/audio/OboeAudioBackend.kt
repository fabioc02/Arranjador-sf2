package com.example.domain.audio

import android.content.Context

/**
 * Audio backend that utilizes Google Oboe for ultra-low latency audio streaming.
 * Automatically delegates to AudioTrackBackend if the native library is unavailable.
 */
class OboeAudioBackend(
    private val fallbackBackend: AudioTrackBackend = AudioTrackBackend()
) : AudioBackend {
    private val tag = "OboeAudioBackend"
    private var usingNative = false

    private fun logI(msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    override fun setRenderer(renderer: AudioRenderer) {
        fallbackBackend.setRenderer(renderer)
    }

    fun attachContext(context: Context) {
        fallbackBackend.attachContext(context)
    }

    override fun start(sampleRate: Int, framesPerBuffer: Int) {
        logI("Starting ultra-low latency AudioTrack backend for SoundFont synthesis")
        fallbackBackend.start(sampleRate, framesPerBuffer)

        if (NativeAudioBridge.isLoaded) {
            val started = NativeAudioBridge.init(sampleRate, framesPerBuffer)
            if (started) {
                usingNative = true
                logI("Native audio bridge initialized alongside SoundFont engine")
            }
        }
    }

    override fun stop() {
        fallbackBackend.stop()
        if (usingNative) {
            NativeAudioBridge.stop()
            usingNative = false
        }
    }

    val isNativeActive: Boolean
        get() = usingNative && NativeAudioBridge.isRunning()
}
