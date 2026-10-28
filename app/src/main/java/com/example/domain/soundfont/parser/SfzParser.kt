package com.example.domain.soundfont.parser

import com.example.domain.soundfont.model.InstrumentZone
import com.example.domain.soundfont.model.LoopMode
import com.example.domain.soundfont.model.SampleBuffer
import com.example.domain.soundfont.model.SoundFontMetadata
import com.example.domain.soundfont.model.SoundFontPreset
import com.example.domain.soundfont.model.SoundFontType
import com.example.domain.soundfont.model.StereoChannel
import java.io.File

/**
 * High-performance, lightweight SFZ parser for Korg Triton and standard SFZ libraries.
 * Parses opcode blocks (<control>, <global>, <group>, <region>) without loading WAV files up front.
 */
object SfzParser {

    fun parseMetadata(file: File): SoundFontMetadata {
        val lines = file.readLines()
        val zones = mutableListOf<InstrumentZone>()

        var defaultPath = ""
        var globalKey = -1
        var globalLoKey = 0
        var globalHiKey = 127
        var globalLoVel = 0
        var globalHiVel = 127
        var globalPan = 0f
        var globalLoopMode = LoopMode.NO_LOOP

        var currentOpcodes = mutableMapOf<String, String>()
        var inRegion = false

        fun flushRegion() {
            if (currentOpcodes.isEmpty()) return
            val samplePath = currentOpcodes["sample"]
            if (samplePath != null) {
                val fullRelPath = if (defaultPath.isNotEmpty()) {
                    "$defaultPath/$samplePath".replace('\\', '/').replace("//", "/")
                } else {
                    samplePath.replace('\\', '/')
                }

                // Determine key range
                val isExplicitDrumKey = Regex("""^[Nn](\d{2,3})""").find(File(samplePath).nameWithoutExtension) != null
                val key = currentOpcodes["key"]?.toIntOrNull()
                val rootKey = currentOpcodes["pitch_keycenter"]?.toIntOrNull()
                    ?: key
                    ?: guessRootKeyFromName(samplePath)

                val loKey = key ?: currentOpcodes["lokey"]?.toIntOrNull() ?: if (isExplicitDrumKey) rootKey else globalLoKey
                val hiKey = key ?: currentOpcodes["hikey"]?.toIntOrNull() ?: if (isExplicitDrumKey) rootKey else globalHiKey
                val loVel = currentOpcodes["lovel"]?.toIntOrNull() ?: globalLoVel
                val hiVel = currentOpcodes["hivel"]?.toIntOrNull() ?: globalHiVel

                // Pan: SFZ uses -100 to +100
                val panRaw = currentOpcodes["pan"]?.toFloatOrNull()
                val pan = when {
                    panRaw != null -> (panRaw / 100.0f).coerceIn(-1.0f, 1.0f)
                    samplePath.contains("_L", ignoreCase = true) -> -0.5f
                    samplePath.contains("_R", ignoreCase = true) -> 0.5f
                    else -> globalPan
                }

                val loopModeStr = currentOpcodes["loop_mode"] ?: "no_loop"
                val loopMode = when {
                    loopModeStr.contains("continuous", ignoreCase = true) -> LoopMode.CONTINUOUS_LOOP
                    loopModeStr.contains("release", ignoreCase = true) -> LoopMode.LOOP_DURING_RELEASE
                    else -> globalLoopMode
                }

                val attack = currentOpcodes["ampeg_attack"]?.toFloatOrNull() ?: 0.001f
                val decay = currentOpcodes["ampeg_decay"]?.toFloatOrNull() ?: 0.1f
                val sustain = currentOpcodes["ampeg_sustain"]?.toFloatOrNull()?.let { it / 100.0f } ?: 1.0f
                val release = currentOpcodes["ampeg_release"]?.toFloatOrNull() ?: 0.2f
                val tune = currentOpcodes["tune"]?.toIntOrNull() ?: 0

                val zone = InstrumentZone(
                    lowKey = loKey,
                    highKey = hiKey,
                    lowVel = loVel,
                    highVel = hiVel,
                    rootKey = rootKey,
                    fineTuneCents = tune,
                    pan = pan,
                    samplePath = fullRelPath,
                    loopMode = loopMode,
                    attackSec = attack,
                    decaySec = decay,
                    sustainLevel = sustain,
                    releaseSec = release
                )
                zones.add(zone)
            }
            currentOpcodes.clear()
        }

        for (lineRaw in lines) {
            val line = lineRaw.trim().substringBefore("//")
            if (line.isEmpty()) continue

            // Tokenize by opcodes and headers
            var remaining = line
            while (remaining.isNotEmpty()) {
                val headerMatch = Regex("""<(control|global|group|region)>""", RegexOption.IGNORE_CASE).find(remaining)
                if (headerMatch != null && headerMatch.range.first == 0) {
                    val headerType = headerMatch.groupValues[1].lowercase()
                    if (inRegion) {
                        flushRegion()
                        inRegion = false
                    }
                    if (headerType == "region") {
                        inRegion = true
                    }
                    remaining = remaining.substring(headerMatch.range.last + 1).trim()
                    continue
                }

                // Opcode match: opcode=value
                val opcodeMatch = Regex("""([a-zA-Z0-9_]+)=([^\s<]+)""").find(remaining)
                if (opcodeMatch != null && opcodeMatch.range.first == 0) {
                    val key = opcodeMatch.groupValues[1].lowercase()
                    val value = opcodeMatch.groupValues[2].trim('"', '\'')
                    if (inRegion) {
                        currentOpcodes[key] = value
                    } else {
                        // Global / Control opcodes
                        when (key) {
                            "default_path" -> defaultPath = value.replace('\\', '/').trimEnd('/')
                            "pan" -> globalPan = (value.toFloatOrNull() ?: 0f) / 100f
                            "lokey" -> globalLoKey = value.toIntOrNull() ?: 0
                            "hikey" -> globalHiKey = value.toIntOrNull() ?: 127
                            "lovel" -> globalLoVel = value.toIntOrNull() ?: 0
                            "hivel" -> globalHiVel = value.toIntOrNull() ?: 127
                        }
                    }
                    remaining = remaining.substring(opcodeMatch.range.last + 1).trim()
                } else {
                    // Skip token or advance
                    val nextSpace = remaining.indexOf(' ')
                    val nextTag = remaining.indexOf('<')
                    val advance = when {
                        nextSpace != -1 && (nextTag == -1 || nextSpace < nextTag) -> nextSpace + 1
                        nextTag != -1 -> nextTag
                        else -> remaining.length
                    }
                    remaining = remaining.substring(advance).trim()
                }
            }
        }
        if (inRegion) {
            flushRegion()
        }

        val hasStereo = zones.any { it.isStereoLeft || it.isStereoRight }
        val preset = SoundFontPreset(
            bank = 0,
            program = 0,
            name = file.nameWithoutExtension,
            zones = zones,
            isStereo = hasStereo
        )

        return SoundFontMetadata(
            name = file.nameWithoutExtension,
            filePath = file.absolutePath,
            type = SoundFontType.SFZ,
            presets = listOf(preset),
            totalSamplesCount = zones.size,
            isStereo = hasStereo
        )
    }

    /**
     * Loads the actual WAV audio sample data for all zones of a given preset.
     * Caches loaded buffers so duplicate WAV references in different velocity layers share the same buffer.
     */
    fun loadSamplesForPreset(
        sfzFile: File,
        preset: SoundFontPreset
    ): Map<String, SampleBuffer> {
        val baseDir = sfzFile.parentFile ?: File(".")
        val cache = mutableMapOf<String, SampleBuffer>()

        for (zone in preset.zones) {
            val relPath = zone.samplePath ?: continue
            val sampleBuffer = cache.getOrPut(relPath) {
                var wavFile = File(baseDir, relPath)
                if (!wavFile.exists()) {
                    // Fallback 1: check in "samples/" subfolder with relative name
                    val inSamples = File(File(baseDir, "samples"), File(relPath).name)
                    if (inSamples.exists()) {
                        wavFile = inSamples
                    } else {
                        // Fallback 2: check if relPath already has samples/ but baseDir doesn't match
                        val bareName = File(relPath).name
                        val directSample = File(baseDir, bareName)
                        if (directSample.exists()) {
                            wavFile = directSample
                        }
                    }
                }

                if (wavFile.exists()) {
                    WavReader.readWav(wavFile, defaultRootKey = zone.effectiveRootKey)
                } else {
                    // Empty fallback buffer to prevent crashes if a sample is missing
                    SampleBuffer(
                        id = 0,
                        name = wavFile.nameWithoutExtension,
                        data = FloatArray(64),
                        sampleRate = 44100,
                        rootKey = zone.effectiveRootKey,
                        stereoChannel = StereoChannel.MONO
                    )
                }
            }
            zone.sampleBuffer = sampleBuffer
        }
        return cache
    }

    /**
     * Extracts root key from note names like "N035_Bass_Drum", "C4", "A#1", "G#6", "D1" if present in filename.
     */
    fun guessRootKeyFromName(name: String): Int {
        val cleanName = File(name).nameWithoutExtension
            .replace("_L", "", ignoreCase = true)
            .replace("_R", "", ignoreCase = true)

        // Triton / Yamaha drum note format: N035_Acoustic_Bass_Drum, N36_Kick, etc.
        val nMatch = Regex("""^[Nn](\d{2,3})""").find(cleanName)
        if (nMatch != null) {
            val num = nMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 0..127) return num
        }

        // Standard scientific pitch notation: C4, F#2, Bb3, etc.
        val match = Regex("""([A-Ga-g])(#|b)?(-?[0-9])""").find(cleanName) ?: return 60
        val noteLetter = match.groupValues[1].uppercase()
        val accidental = match.groupValues[2]
        val octave = match.groupValues[3].toIntOrNull() ?: 4

        var semitone = when (noteLetter) {
            "C" -> 0
            "D" -> 2
            "E" -> 4
            "F" -> 5
            "G" -> 7
            "A" -> 9
            "B" -> 11
            else -> 0
        }
        if (accidental == "#") semitone += 1
        if (accidental == "b") semitone -= 1

        val midiNote = (octave + 1) * 12 + semitone
        return midiNote.coerceIn(0, 127)
    }
}
