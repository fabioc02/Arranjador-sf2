package com.example.domain.audio

/**
 * JNI bridge to the high-performance C++ / Oboe sound engine.
 * Provides ultra-low latency real-time synthesis and sample playback.
 * Fully safe on both Android runtime and host JVM unit tests.
 */
object NativeAudioBridge {
    private const val TAG = "NativeAudioBridge"

    private val isAndroidRuntime: Boolean = try {
        System.getProperty("java.vm.name")?.contains("Dalvik", ignoreCase = true) == true
    } catch (_: Throwable) {
        false
    }

    var isLoaded: Boolean = false
        private set

    private fun logI(msg: String) {
        if (isAndroidRuntime) {
            try { android.util.Log.i(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") }
        } else {
            println("[$TAG] $msg")
        }
    }

    private fun logW(msg: String) {
        if (isAndroidRuntime) {
            try { android.util.Log.w(TAG, msg) } catch (_: Throwable) { System.err.println("[$TAG] $msg") }
        } else {
            System.err.println("[$TAG] $msg")
        }
    }

    private fun logE(msg: String) {
        if (isAndroidRuntime) {
            try { android.util.Log.e(TAG, msg) } catch (_: Throwable) { System.err.println("[$TAG] $msg") }
        } else {
            System.err.println("[$TAG] $msg")
        }
    }

    init {
        if (isAndroidRuntime) {
            try {
                System.loadLibrary("arranger_native_audio")
                isLoaded = true
                logI("Native audio engine loaded successfully (libarranger_native_audio.so)")
            } catch (e: UnsatisfiedLinkError) {
                logW("Native audio library not available: ${e.message}. Using high-performance Kotlin fallback.")
                isLoaded = false
            } catch (e: Throwable) {
                logW("Native audio library load failed: ${e.message}. Using high-performance Kotlin fallback.")
                isLoaded = false
            }
        } else {
            // Running on host JVM / unit test environment
            isLoaded = false
        }
    }

    fun init(sampleRate: Int = 44100, framesPerBurst: Int = 192): Boolean {
        if (!isLoaded) return false
        return try {
            nativeInit(sampleRate, framesPerBurst)
        } catch (e: Throwable) {
            logE("nativeInit failed: ${e.message}")
            false
        }
    }

    fun start(): Boolean {
        if (!isLoaded) return false
        return try {
            nativeStart()
        } catch (e: Throwable) {
            logE("nativeStart failed: ${e.message}")
            false
        }
    }

    fun stop() {
        if (!isLoaded) return
        try {
            nativeStop()
        } catch (e: Throwable) {
            logE("nativeStop failed: ${e.message}")
        }
    }

    fun setMaxPolyphony(polyphony: Int) {
        if (!isLoaded) return
        try {
            nativeSetMaxPolyphony(polyphony)
        } catch (e: Throwable) {
            logE("nativeSetMaxPolyphony failed: ${e.message}")
        }
    }

    fun noteOn(channel: Int, note: Int, velocity: Int) {
        if (!isLoaded) return
        try {
            nativeNoteOn(channel, note, velocity)
        } catch (e: Throwable) {
            logE("nativeNoteOn failed: ${e.message}")
        }
    }

    fun noteOff(channel: Int, note: Int) {
        if (!isLoaded) return
        try {
            nativeNoteOff(channel, note)
        } catch (e: Throwable) {
            logE("nativeNoteOff failed: ${e.message}")
        }
    }

    fun controlChange(channel: Int, controller: Int, value: Int) {
        if (!isLoaded) return
        try {
            nativeControlChange(channel, controller, value)
        } catch (e: Throwable) {
            logE("nativeControlChange failed: ${e.message}")
        }
    }

    fun pitchBend(channel: Int, value: Int) {
        if (!isLoaded) return
        try {
            nativePitchBend(channel, value)
        } catch (e: Throwable) {
            logE("nativePitchBend failed: ${e.message}")
        }
    }

    fun programChange(channel: Int, program: Int) {
        if (!isLoaded) return
        try {
            nativeProgramChange(channel, program)
        } catch (e: Throwable) {
            logE("nativeProgramChange failed: ${e.message}")
        }
    }

    fun allNotesOff(channel: Int = -1) {
        if (!isLoaded) return
        try {
            nativeAllNotesOff(channel)
        } catch (e: Throwable) {
            logE("nativeAllNotesOff failed: ${e.message}")
        }
    }

    fun registerSample(
        sampleId: Int,
        pcmData: FloatArray,
        isStereo: Boolean,
        sampleRate: Int,
        rootKey: Int,
        loopStart: Int,
        loopEnd: Int,
        isLooped: Boolean
    ) {
        if (!isLoaded) return
        try {
            nativeRegisterSample(sampleId, pcmData, isStereo, sampleRate, rootKey, loopStart, loopEnd, isLooped)
        } catch (e: Throwable) {
            logE("nativeRegisterSample failed: ${e.message}")
        }
    }

    fun mapKeyToSample(channel: Int, lowKey: Int, highKey: Int, sampleId: Int) {
        if (!isLoaded) return
        try {
            nativeMapKeyToSample(channel, lowKey, highKey, sampleId)
        } catch (e: Throwable) {
            logE("nativeMapKeyToSample failed: ${e.message}")
        }
    }

    fun clearAllSamples() {
        if (!isLoaded) return
        try {
            nativeClearAllSamples()
        } catch (e: Throwable) {
            logE("nativeClearAllSamples failed: ${e.message}")
        }
    }

    fun getActiveVoiceCount(): Int {
        if (!isLoaded) return 0
        return try {
            nativeGetActiveVoices()
        } catch (e: Throwable) {
            0
        }
    }

    fun isRunning(): Boolean {
        if (!isLoaded) return false
        return try {
            nativeIsRunning()
        } catch (e: Throwable) {
            false
        }
    }

    // JNI Native Declarations
    private external fun nativeInit(sampleRate: Int, framesPerBurst: Int): Boolean
    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeSetMaxPolyphony(polyphony: Int)
    private external fun nativeNoteOn(channel: Int, note: Int, velocity: Int)
    private external fun nativeNoteOff(channel: Int, note: Int)
    private external fun nativeControlChange(channel: Int, controller: Int, value: Int)
    private external fun nativePitchBend(channel: Int, value: Int)
    private external fun nativeProgramChange(channel: Int, program: Int)
    private external fun nativeAllNotesOff(channel: Int)
    private external fun nativeRegisterSample(
        sampleId: Int,
        pcmData: FloatArray,
        isStereo: Boolean,
        sampleRate: Int,
        rootKey: Int,
        loopStart: Int,
        loopEnd: Int,
        isLooped: Boolean
    )
    private external fun nativeMapKeyToSample(channel: Int, lowKey: Int, highKey: Int, sampleId: Int)
    private external fun nativeClearAllSamples()
    private external fun nativeGetActiveVoices(): Int
    private external fun nativeIsRunning(): Boolean
}
