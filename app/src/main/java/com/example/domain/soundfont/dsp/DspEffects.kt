package com.example.domain.soundfont.dsp

/**
 * High-performance, low-latency DSP effects processor for SoundFont synthesis.
 * Zero-allocation during audio rendering loop.
 */
class StereoReverb(private val sampleRate: Int = 44100) {

    private class CombFilter(size: Int) {
        val buffer = FloatArray(size)
        var bufferIndex = 0
        var filterStore = 0f

        fun process(input: Float, damp: Float, feedback: Float): Float {
            val output = buffer[bufferIndex]
            filterStore = (output * (1.0f - damp)) + (filterStore * damp)
            buffer[bufferIndex] = input + (filterStore * feedback)
            if (++bufferIndex >= buffer.size) bufferIndex = 0
            return output
        }

        fun clear() {
            buffer.fill(0f)
            filterStore = 0f
            bufferIndex = 0
        }
    }

    private class AllpassFilter(size: Int) {
        val buffer = FloatArray(size)
        var bufferIndex = 0

        fun process(input: Float): Float {
            val bufOut = buffer[bufferIndex]
            val output = -input + bufOut
            buffer[bufferIndex] = input + (bufOut * 0.5f)
            if (++bufferIndex >= buffer.size) bufferIndex = 0
            return output
        }

        fun clear() {
            buffer.fill(0f)
            bufferIndex = 0
        }
    }

    private val combLengthsL = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val combLengthsR = intArrayOf(1139, 1211, 1300, 1379, 1445, 1514, 1580, 1640)
    private val allpassLengthsL = intArrayOf(556, 441, 341, 225)
    private val allpassLengthsR = intArrayOf(579, 464, 364, 248)

    private val combsL = combLengthsL.map { CombFilter((it * sampleRate) / 44100) }.toTypedArray()
    private val combsR = combLengthsR.map { CombFilter((it * sampleRate) / 44100) }.toTypedArray()
    private val allpassesL = allpassLengthsL.map { AllpassFilter((it * sampleRate) / 44100) }.toTypedArray()
    private val allpassesR = allpassLengthsR.map { AllpassFilter((it * sampleRate) / 44100) }.toTypedArray()

    var roomSize = 0.82f
    var damp = 0.25f
    var wetLevel = 0.35f
    var dryLevel = 1.0f

    fun process(
        inputL: FloatArray,
        inputR: FloatArray,
        outputL: FloatArray,
        outputR: FloatArray,
        frames: Int,
        sendAmount: Float = 1.0f
    ) {
        if (sendAmount <= 0.001f) return

        val feedback = (roomSize * 0.28f) + 0.7f
        val effectiveWet = wetLevel * sendAmount

        for (i in 0 until frames) {
            val inL = inputL[i] * 0.015f
            val inR = inputR[i] * 0.015f

            var outL = 0f
            var outR = 0f

            for (c in combsL.indices) {
                outL += combsL[c].process(inL, damp, feedback)
                outR += combsR[c].process(inR, damp, feedback)
            }

            for (a in allpassesL.indices) {
                outL = allpassesL[a].process(outL)
                outR = allpassesR[a].process(outR)
            }

            outputL[i] += outL * effectiveWet
            outputR[i] += outR * effectiveWet
        }
    }

    fun clear() {
        combsL.forEach { it.clear() }
        combsR.forEach { it.clear() }
        allpassesL.forEach { it.clear() }
        allpassesR.forEach { it.clear() }
    }
}

/**
 * Stereo Chorus processor with dual 90-degree phase-shifted LFOs for rich stereo imaging.
 */
class StereoChorus(private val sampleRate: Int = 44100) {
    private val maxDelaySamples = (sampleRate * 0.040).toInt() // 40ms max delay
    private val delayBufferL = FloatArray(maxDelaySamples)
    private val delayBufferR = FloatArray(maxDelaySamples)
    private var writeIndex = 0

    var rateHz = 0.8f
    var depthMs = 3.5f
    var feedback = 0.25f
    var wetMix = 0.40f

    private var lfoPhase = 0.0

    fun process(
        inputL: FloatArray,
        inputR: FloatArray,
        outputL: FloatArray,
        outputR: FloatArray,
        frames: Int,
        sendAmount: Float = 1.0f
    ) {
        if (sendAmount <= 0.001f) return

        val lfoInc = (2.0 * Math.PI * rateHz) / sampleRate
        val depthSamples = (depthMs * 0.001f * sampleRate).coerceIn(1f, maxDelaySamples * 0.4f)
        val baseDelay = depthSamples + 2.0f
        val effectiveWet = wetMix * sendAmount

        for (i in 0 until frames) {
            val inL = inputL[i]
            val inR = inputR[i]

            // LFO for Left and Right (90 degrees / pi/2 phase difference)
            val modL = (Math.sin(lfoPhase) * depthSamples).toFloat()
            val modR = (Math.cos(lfoPhase) * depthSamples).toFloat()
            lfoPhase += lfoInc
            if (lfoPhase > 2.0 * Math.PI) lfoPhase -= 2.0 * Math.PI

            val readPosL = (writeIndex - (baseDelay + modL) + maxDelaySamples) % maxDelaySamples
            val readPosR = (writeIndex - (baseDelay + modR) + maxDelaySamples) % maxDelaySamples

            // Linear interpolation of delayed sample
            val delayedL = interpolateSample(delayBufferL, readPosL)
            val delayedR = interpolateSample(delayBufferR, readPosR)

            delayBufferL[writeIndex] = inL + (delayedL * feedback)
            delayBufferR[writeIndex] = inR + (delayedR * feedback)

            if (++writeIndex >= maxDelaySamples) writeIndex = 0

            outputL[i] += delayedL * effectiveWet
            outputR[i] += delayedR * effectiveWet
        }
    }

    private fun interpolateSample(buf: FloatArray, pos: Float): Float {
        val i0 = pos.toInt()
        val frac = pos - i0
        val i1 = if (i0 + 1 >= buf.size) 0 else i0 + 1
        return buf[i0] + frac * (buf[i1] - buf[i0])
    }

    fun clear() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        writeIndex = 0
        lfoPhase = 0.0
    }
}

/**
 * Transparent Soft Clipper / Limiter.
 * Prevents digital clipping on USB audio interface DACs without harsh distortion.
 */
object MasterSoftLimiter {

    fun process(buffer: FloatArray, offset: Int, frameCount: Int, channels: Int = 2) {
        val totalSamples = frameCount * channels
        val threshold = 0.85f
        val maxPeak = 0.99f

        for (i in 0 until totalSamples) {
            val idx = offset + i
            val x = buffer[idx]
            val absX = if (x < 0f) -x else x

            if (absX > threshold) {
                // Soft saturating cubic spline above threshold
                val excess = (absX - threshold) / (1.0f - threshold)
                val compressedExcess = excess / (1.0f + excess)
                val newAbs = threshold + compressedExcess * (maxPeak - threshold)
                buffer[idx] = if (x < 0f) -newAbs else newAbs
            }
        }
    }
}
