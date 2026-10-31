package com.example.domain.soundfont.repository

import com.example.domain.soundfont.model.*
import java.io.File
import kotlin.math.*

/**
 * Built-in high-quality starter SoundFont library.
 * Generates rich, realistic PCM samples and metadata across standard instrument categories.
 * Guarantees that previewing and playing instruments sounds distinct, immediate, and musical
 * even before the user imports their external 70+ Korg Triton SoundFonts.
 */
object StarterSoundFontLibrary {

    const val STARTER_PREFIX = "starter_lib://"
    private const val SAMPLE_RATE = 44100

    private val starterCatalog = mutableMapOf<String, SoundFontMetadata>()
    private val starterPresets = mutableMapOf<String, SoundFontPreset>()
    private val starterVoices = mutableListOf<SoundFontVoice>()

    init {
        buildLibrary()
    }

    fun getAllStarterVoices(): List<SoundFontVoice> = synchronized(starterVoices) { starterVoices.toList() }

    fun getStarterMetadata(filePath: String): SoundFontMetadata? = starterCatalog[filePath]

    fun getStarterPreset(filePath: String, bank: Int, program: Int): SoundFontPreset? {
        val key = "$filePath:$bank:$program"
        return starterPresets[key] ?: starterCatalog[filePath]?.presets?.firstOrNull()
    }

    private fun buildLibrary() {
        // 1. Acoustic Grand Piano
        createInstrument(
            name = "Korg Triton Stereo Grand",
            category = "Piano",
            bank = 0,
            program = 0,
            isStereo = true,
            generatePcm = { generatePianoPcm() }
        )

        // 2. Vintage Rhodes / Dyno EP
        createInstrument(
            name = "Korg Triton Dyno EP (Rhodes)",
            category = "E.Piano",
            bank = 0,
            program = 4,
            isStereo = true,
            generatePcm = { generateRhodesPcm() }
        )

        // 3. Tonewheel Jazz Organ
        createInstrument(
            name = "Korg Triton B3 Drawbars",
            category = "Órgão",
            bank = 0,
            program = 16,
            isStereo = true,
            generatePcm = { generateOrganPcm() }
        )

        // 4. Acoustic Plucked Guitar
        createInstrument(
            name = "Korg Triton Nylon Pluck",
            category = "Violão / Guitarra",
            bank = 0,
            program = 24,
            isStereo = true,
            generatePcm = { generateGuitarPcm() }
        )

        // 5. Electric Finger Bass
        createInstrument(
            name = "Korg Triton Finger Bass",
            category = "Baixo",
            bank = 0,
            program = 32,
            isStereo = false,
            generatePcm = { generateBassPcm() }
        )

        // 6. Lush Orchestral Strings
        createInstrument(
            name = "Korg Triton Lush Strings",
            category = "Cordas",
            bank = 0,
            program = 48,
            isStereo = true,
            generatePcm = { generateStringsPcm() }
        )

        // 7. Fanfare Brass Section
        createInstrument(
            name = "Korg Triton Warm Brass",
            category = "Metais",
            bank = 0,
            program = 61,
            isStereo = true,
            generatePcm = { generateBrassPcm() }
        )

        // 8. Ambient Choir / Warm Pad
        createInstrument(
            name = "Korg Triton Ambient Pad",
            category = "Pad / Coro",
            bank = 0,
            program = 88,
            isStereo = true,
            generatePcm = { generatePadPcm() }
        )

        // 9. Analog Saw Lead
        createInstrument(
            name = "Korg Triton Saw Lead",
            category = "Sintetizador",
            bank = 0,
            program = 80,
            isStereo = true,
            generatePcm = { generateLeadPcm() }
        )

        // 10. Multi-Zone Pop / Rock Drum Kit
        createDrumKit(
            name = "Korg Triton Pop Drum Kit",
            category = "Baterias",
            bank = 127,
            program = 0
        )
    }

    private fun createInstrument(
        name: String,
        category: String,
        bank: Int,
        program: Int,
        isStereo: Boolean,
        generatePcm: () -> FloatArray
    ) {
        val path = "$STARTER_PREFIX${name.replace(' ', '_').lowercase()}.sf2"
        val pcm = generatePcm()

        val sampleL = SampleBuffer(
            id = (bank shl 8) or program,
            name = "$name L",
            data = pcm,
            sampleRate = SAMPLE_RATE,
            rootKey = 60,
            isLooped = true,
            loopStart = (pcm.size * 0.4).toInt(),
            loopEnd = (pcm.size * 0.95).toInt(),
            stereoChannel = if (isStereo) StereoChannel.LEFT else StereoChannel.MONO
        )

        val zoneL = InstrumentZone(
            lowKey = 0,
            highKey = 127,
            lowVel = 0,
            highVel = 127,
            rootKey = 60,
            pan = if (isStereo) -0.5f else 0.0f,
            sampleBuffer = sampleL,
            loopMode = LoopMode.CONTINUOUS_LOOP,
            attackSec = 0.003f,
            decaySec = 0.4f,
            sustainLevel = 0.8f,
            releaseSec = 0.35f
        )

        val zones = if (isStereo) {
            val sampleR = SampleBuffer(
                id = ((bank shl 8) or program) + 1000,
                name = "$name R",
                data = pcm,
                sampleRate = SAMPLE_RATE,
                rootKey = 60,
                isLooped = true,
                loopStart = (pcm.size * 0.4).toInt(),
                loopEnd = (pcm.size * 0.95).toInt(),
                stereoChannel = StereoChannel.RIGHT
            )
            val zoneR = zoneL.copy(pan = 0.5f, sampleBuffer = sampleR)
            listOf(zoneL, zoneR)
        } else {
            listOf(zoneL)
        }

        val preset = SoundFontPreset(
            bank = bank,
            program = program,
            name = name,
            zones = zones,
            isStereo = isStereo
        )

        val meta = SoundFontMetadata(
            name = name,
            filePath = path,
            type = SoundFontType.SF2,
            presets = listOf(preset),
            totalSamplesCount = zones.size,
            isStereo = isStereo
        )

        val voiceId = "$path:$bank:$program"
        val voice = SoundFontVoice(
            id = voiceId,
            name = name,
            category = category,
            filePath = path,
            fileType = SoundFontType.SF2,
            bank = bank,
            program = program,
            isStereo = isStereo,
            fileSizeFormatted = "RAM"
        )

        starterCatalog[path] = meta
        starterPresets[voiceId] = preset
        starterVoices.add(voice)
    }

    private fun createDrumKit(
        name: String,
        category: String,
        bank: Int,
        program: Int
    ) {
        val path = "$STARTER_PREFIX${name.replace(' ', '_').lowercase()}.sf2"

        // Individual drum hit PCM samples
        val kickPcm = generateKickPcm()
        val snarePcm = generateSnarePcm()
        val hihatClosedPcm = generateHiHatPcm(open = false)
        val hihatOpenPcm = generateHiHatPcm(open = true)
        val tomPcm = generateTomPcm()
        val cymbalPcm = generateCymbalPcm()

        fun makeOneShotZone(key: Int, pcm: FloatArray, pan: Float, nameSuffix: String): InstrumentZone {
            val buf = SampleBuffer(
                id = key + 5000,
                name = "$name $nameSuffix",
                data = pcm,
                sampleRate = SAMPLE_RATE,
                rootKey = key,
                isLooped = false
            )
            return InstrumentZone(
                lowKey = key,
                highKey = key,
                lowVel = 0,
                highVel = 127,
                rootKey = key,
                pan = pan,
                sampleBuffer = buf,
                loopMode = LoopMode.NO_LOOP,
                attackSec = 0.001f,
                decaySec = 0.8f,
                sustainLevel = 0.0f,
                releaseSec = 0.1f
            )
        }

        val zones = listOf(
            makeOneShotZone(35, kickPcm, 0.0f, "Acoustic Kick"),
            makeOneShotZone(36, kickPcm, 0.0f, "Kick"),
            makeOneShotZone(37, snarePcm, -0.2f, "Side Stick"),
            makeOneShotZone(38, snarePcm, 0.0f, "Snare 1"),
            makeOneShotZone(40, snarePcm, 0.1f, "Snare 2"),
            makeOneShotZone(41, tomPcm, -0.4f, "Low Floor Tom"),
            makeOneShotZone(42, hihatClosedPcm, -0.3f, "Closed Hi-Hat"),
            makeOneShotZone(43, tomPcm, -0.2f, "High Floor Tom"),
            makeOneShotZone(44, hihatClosedPcm, -0.3f, "Pedal Hi-Hat"),
            makeOneShotZone(45, tomPcm, 0.1f, "Low Tom"),
            makeOneShotZone(46, hihatOpenPcm, -0.3f, "Open Hi-Hat"),
            makeOneShotZone(47, tomPcm, 0.2f, "Low-Mid Tom"),
            makeOneShotZone(48, tomPcm, 0.3f, "Hi-Mid Tom"),
            makeOneShotZone(49, cymbalPcm, -0.4f, "Crash Cymbal 1"),
            makeOneShotZone(50, tomPcm, 0.4f, "High Tom"),
            makeOneShotZone(51, cymbalPcm, 0.4f, "Ride Cymbal 1")
        )

        val preset = SoundFontPreset(
            bank = bank,
            program = program,
            name = name,
            zones = zones,
            isStereo = true
        )

        val meta = SoundFontMetadata(
            name = name,
            filePath = path,
            type = SoundFontType.SF2,
            presets = listOf(preset),
            totalSamplesCount = zones.size,
            isStereo = true
        )

        val voiceId = "$path:$bank:$program"
        val voice = SoundFontVoice(
            id = voiceId,
            name = name,
            category = category,
            filePath = path,
            fileType = SoundFontType.SF2,
            bank = bank,
            program = program,
            isStereo = true,
            fileSizeFormatted = "RAM"
        )

        starterCatalog[path] = meta
        starterPresets[voiceId] = preset
        starterVoices.add(voice)
    }

    // ==========================================
    // PCM GENERATION ALGORITHMS
    // ==========================================

    private fun generatePianoPcm(): FloatArray {
        val len = (SAMPLE_RATE * 1.2).toInt()
        val data = FloatArray(len)
        val f0 = 261.63 // C4
        val twoPi = 2.0 * Math.PI

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-3.2 * t)
            val hammer = exp(-55.0 * t) * 0.2 * (sin(twoPi * 1200.0 * t))

            val s1 = sin(twoPi * f0 * t)
            val s2 = 0.55 * sin(twoPi * f0 * 2.0 * t)
            val s3 = 0.32 * sin(twoPi * f0 * 3.0 * t)
            val s4 = 0.18 * sin(twoPi * f0 * 4.0 * t)
            val s5 = 0.10 * sin(twoPi * f0 * 5.0 * t)
            val s6 = 0.05 * sin(twoPi * f0 * 6.0 * t)

            data[i] = ((s1 + s2 + s3 + s4 + s5 + s6) * env * 0.5 + hammer).toFloat()
        }
        return data
    }

    private fun generateRhodesPcm(): FloatArray {
        val len = (SAMPLE_RATE * 1.2).toInt()
        val data = FloatArray(len)
        val f0 = 261.63
        val twoPi = 2.0 * Math.PI

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-2.2 * t)
            val bellEnv = exp(-9.0 * t)
            val bell = 0.35 * bellEnv * sin(twoPi * f0 * 7.0 * t)
            val mod = 1.2 * exp(-6.0 * t) * sin(twoPi * f0 * 3.5 * t)
            val carrier = sin(twoPi * f0 * t + mod)

            data[i] = ((carrier * 0.6 + bell) * env).toFloat()
        }
        return data
    }

    private fun generateOrganPcm(): FloatArray {
        val len = (SAMPLE_RATE * 0.8).toInt()
        val data = FloatArray(len)
        val f0 = 261.63
        val twoPi = 2.0 * Math.PI

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val vibrato = 1.0 + 0.003 * sin(twoPi * 6.0 * t)
            val freq = f0 * vibrato

            // Drawbars: 888000000
            val sub = 0.65 * sin(twoPi * (freq * 0.5) * t)
            val fund = 1.0 * sin(twoPi * freq * t)
            val third = 0.6 * sin(twoPi * (freq * 2.0) * t)
            val fourth = 0.4 * sin(twoPi * (freq * 3.0) * t)

            data[i] = ((sub + fund + third + fourth) * 0.35).toFloat()
        }
        return data
    }

    private fun generateGuitarPcm(): FloatArray {
        val len = (SAMPLE_RATE * 1.0).toInt()
        val data = FloatArray(len)
        val f0 = 261.63
        val twoPi = 2.0 * Math.PI

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-4.0 * t)
            val pick = exp(-70.0 * t) * 0.35 * (sin(twoPi * 2200.0 * t))

            val s1 = sin(twoPi * f0 * t)
            val s2 = 0.7 * sin(twoPi * f0 * 2.0 * t)
            val s3 = 0.45 * sin(twoPi * f0 * 3.0 * t)
            val s4 = 0.25 * sin(twoPi * f0 * 4.0 * t)

            data[i] = ((s1 + s2 + s3 + s4) * env * 0.45 + pick).toFloat()
        }
        return data
    }

    private fun generateBassPcm(): FloatArray {
        val len = (SAMPLE_RATE * 1.0).toInt()
        val data = FloatArray(len)
        val f0 = 65.41 // C2
        val twoPi = 2.0 * Math.PI

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-2.8 * t)
            val punch = exp(-45.0 * t) * 0.3 * sin(twoPi * 180.0 * t)

            val s1 = sin(twoPi * f0 * t)
            val s2 = 0.8 * sin(twoPi * f0 * 2.0 * t)
            val s3 = 0.4 * sin(twoPi * f0 * 3.0 * t)
            val s4 = 0.2 * sin(twoPi * f0 * 4.0 * t)

            data[i] = ((s1 + s2 + s3 + s4) * env * 0.55 + punch).toFloat()
        }
        return data
    }

    private fun generateStringsPcm(): FloatArray {
        val len = (SAMPLE_RATE * 1.0).toInt()
        val data = FloatArray(len)
        val f0 = 261.63
        val twoPi = 2.0 * Math.PI

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.08).coerceAtMost(1.0)
            val osc1 = sin(twoPi * (f0 * 1.000) * t)
            val osc2 = 0.85 * sin(twoPi * (f0 * 1.004) * t)
            val osc3 = 0.85 * sin(twoPi * (f0 * 0.996) * t)
            val osc4 = 0.5 * sin(twoPi * (f0 * 2.002) * t)

            data[i] = ((osc1 + osc2 + osc3 + osc4) * 0.3 * attack).toFloat()
        }
        return data
    }

    private fun generateBrassPcm(): FloatArray {
        val len = (SAMPLE_RATE * 0.9).toInt()
        val data = FloatArray(len)
        val f0 = 261.63
        val twoPi = 2.0 * Math.PI

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.03).coerceAtMost(1.0)
            val env = exp(-1.5 * t) * attack

            // Bright rich sawtooth harmonics
            var sum = 0.0
            for (h in 1..10) {
                sum += (1.0 / h) * sin(twoPi * (f0 * h) * t)
            }
            data[i] = (sum * 0.35 * env).toFloat()
        }
        return data
    }

    private fun generatePadPcm(): FloatArray {
        val len = (SAMPLE_RATE * 1.2).toInt()
        val data = FloatArray(len)
        val f0 = 261.63
        val twoPi = 2.0 * Math.PI

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.12).coerceAtMost(1.0)
            val chorus = sin(twoPi * 0.8 * t) * 0.005

            val o1 = sin(twoPi * (f0 * (1.0 + chorus)) * t)
            val o2 = sin(twoPi * (f0 * 2.0 * (1.0 - chorus)) * t) * 0.5
            val o3 = sin(twoPi * (f0 * 0.5) * t) * 0.4

            data[i] = ((o1 + o2 + o3) * 0.35 * attack).toFloat()
        }
        return data
    }

    private fun generateLeadPcm(): FloatArray {
        val len = (SAMPLE_RATE * 0.8).toInt()
        val data = FloatArray(len)
        val f0 = 261.63
        val twoPi = 2.0 * Math.PI

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val phase = (f0 * t) % 1.0
            val saw = (2.0 * phase - 1.0) * 0.5
            val sub = sin(twoPi * (f0 * 0.5) * t) * 0.3

            data[i] = ((saw + sub) * 0.5).toFloat()
        }
        return data
    }

    private fun generateKickPcm(): FloatArray {
        val len = (SAMPLE_RATE * 0.35).toInt()
        val data = FloatArray(len)
        val twoPi = 2.0 * Math.PI

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 135.0 * exp(-18.0 * t) + 45.0
            val env = exp(-12.0 * t)
            val click = exp(-120.0 * t) * 0.35 * sin(twoPi * 900.0 * t)

            data[i] = (sin(twoPi * freq * t) * env * 0.85 + click).toFloat()
        }
        return data
    }

    private fun generateSnarePcm(): FloatArray {
        val len = (SAMPLE_RATE * 0.35).toInt()
        val data = FloatArray(len)
        val twoPi = 2.0 * Math.PI
        val random = java.util.Random(12345)

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val tone = sin(twoPi * 185.0 * t) * exp(-18.0 * t) * 0.4
            val noise = (random.nextFloat() * 2.0f - 1.0f) * exp(-14.0 * t) * 0.6

            data[i] = (tone + noise).toFloat()
        }
        return data
    }

    private fun generateHiHatPcm(open: Boolean): FloatArray {
        val duration = if (open) 0.35 else 0.06
        val len = (SAMPLE_RATE * duration).toInt()
        val data = FloatArray(len)
        val decay = if (open) 9.0 else 45.0
        val random = java.util.Random(54321)

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val noise = (random.nextFloat() * 2.0f - 1.0f)
            val env = exp(-decay * t)
            data[i] = (noise * env * 0.45).toFloat()
        }
        return data
    }

    private fun generateTomPcm(): FloatArray {
        val len = (SAMPLE_RATE * 0.45).toInt()
        val data = FloatArray(len)
        val twoPi = 2.0 * Math.PI

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 160.0 * exp(-10.0 * t) + 85.0
            val env = exp(-7.0 * t)
            data[i] = (sin(twoPi * freq * t) * env * 0.7).toFloat()
        }
        return data
    }

    private fun generateCymbalPcm(): FloatArray {
        val len = (SAMPLE_RATE * 1.1).toInt()
        val data = FloatArray(len)
        val random = java.util.Random(9999)

        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val noise = (random.nextFloat() * 2.0f - 1.0f)
            val env = exp(-3.5 * t)
            data[i] = (noise * env * 0.4).toFloat()
        }
        return data
    }
}
