package com.example.domain.soundfont.model

enum class SoundFontType {
    SF2,
    SFZ
}

enum class StereoChannel {
    MONO,
    LEFT,
    RIGHT
}

enum class LoopMode {
    NO_LOOP,
    CONTINUOUS_LOOP,
    LOOP_DURING_RELEASE
}

/**
 * Header of a sample stored in the SoundFont (e.g. from SF2 'shdr' chunk or SFZ region).
 * Does not contain raw PCM sample data, keeping memory footprint minimal.
 */
data class SampleHeader(
    val id: Int,
    val name: String,
    val startOffset: Long,
    val endOffset: Long,
    val startLoop: Long,
    val endLoop: Long,
    val sampleRate: Int = 44100,
    val originalPitch: Int = 60,
    val pitchCorrection: Int = 0,
    val sampleLink: Int = -1,
    val sampleType: Int = 1 // 1: mono, 2: right, 4: left, 8: linked
) {
    val stereoChannel: StereoChannel
        get() = when (sampleType) {
            2 -> StereoChannel.RIGHT
            4 -> StereoChannel.LEFT
            else -> StereoChannel.MONO
        }

    val isStereo: Boolean
        get() = stereoChannel != StereoChannel.MONO
}

/**
 * In-memory buffer containing loaded PCM audio samples (normalized FloatArray between -1.0f and 1.0f).
 * Loaded lazily only when the preset is active!
 */
data class SampleBuffer(
    val id: Int,
    val name: String,
    val data: FloatArray,
    val sampleRate: Int = 44100,
    val rootKey: Int = 60,
    val tuningCents: Int = 0,
    val isLooped: Boolean = false,
    val loopStart: Int = 0,
    val loopEnd: Int = 0,
    val stereoChannel: StereoChannel = StereoChannel.MONO
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SampleBuffer
        return id == other.id && name == other.name
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        return result
    }
}

/**
 * Defines a playback region/zone for a specific key and velocity range.
 */
data class InstrumentZone(
    val lowKey: Int = 0,
    val highKey: Int = 127,
    val lowVel: Int = 0,
    val highVel: Int = 127,
    val rootKey: Int = -1, // -1: use sample's originalPitch
    val fineTuneCents: Int = 0,
    val coarseTuneSemitones: Int = 0,
    val pan: Float = 0.0f, // -1.0f (Left) to +1.0f (Right)
    val sampleId: Int = -1,
    val samplePath: String? = null, // For SFZ
    val sampleHeader: SampleHeader? = null,
    var sampleBuffer: SampleBuffer? = null,
    val loopMode: LoopMode = LoopMode.NO_LOOP,
    val attackSec: Float = 0.001f,
    val decaySec: Float = 0.1f,
    val sustainLevel: Float = 1.0f,
    val releaseSec: Float = 0.2f,
    val attenuationDb: Float = 0.0f
) {
    fun matches(note: Int, velocity: Int): Boolean {
        return note in lowKey..highKey && velocity in lowVel..highVel
    }

    val effectiveRootKey: Int
        get() {
            if (rootKey in 0..127) return rootKey
            return sampleHeader?.originalPitch ?: 60
        }

    val isStereoLeft: Boolean
        get() = pan < -0.1f || sampleHeader?.stereoChannel == StereoChannel.LEFT

    val isStereoRight: Boolean
        get() = pan > 0.1f || sampleHeader?.stereoChannel == StereoChannel.RIGHT
}

/**
 * A Preset or Program (e.g. Bank 0, Program 33) containing its instrument zones.
 */
data class SoundFontPreset(
    val bank: Int,
    val program: Int,
    val name: String,
    val zones: List<InstrumentZone> = emptyList(),
    val isStereo: Boolean = false
) {
    /**
     * Finds matching zones for a given note and velocity.
     * In stereo soundfonts (like Triton with L and R pairs), this returns two zones (Left and Right).
     */
    fun findMatchingZones(note: Int, velocity: Int): List<InstrumentZone> {
        return zones.filter { it.matches(note, velocity) }
    }
}

/**
 * Complete metadata for a SoundFont file (.sf2 or .sfz).
 * Can be held in memory with tiny footprint (< 20 KB per soundfont).
 */
data class SoundFontMetadata(
    val name: String,
    val filePath: String,
    val type: SoundFontType,
    val presets: List<SoundFontPreset>,
    val totalSamplesCount: Int = 0,
    val smplChunkOffset: Long = 0L,
    val smplChunkLength: Long = 0L,
    val isStereo: Boolean = false
) {
    fun getPreset(bank: Int, program: Int): SoundFontPreset? {
        return presets.firstOrNull { it.bank == bank && it.program == program }
            ?: presets.firstOrNull { it.program == program }
            ?: presets.firstOrNull()
    }
}

/**
 * High-level descriptor of an available SoundFont instrument for browsing and channel mapping.
 * Avoids any conflict with Yamaha PSR-S750 hardware addresses.
 */
data class SoundFontVoice(
    val id: String, // filePath or filePath#bank#prog
    val name: String,
    val category: String, // e.g. "Piano", "E.Piano", "Organ", "Guitar", "Bass", "Strings", "Brass", "Synth", "Drums", etc.
    val filePath: String,
    val fileType: SoundFontType,
    val bank: Int = 0,
    val program: Int = 0,
    val isStereo: Boolean = true,
    val fileSizeFormatted: String = ""
)

