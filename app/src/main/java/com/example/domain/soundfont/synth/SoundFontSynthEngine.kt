package com.example.domain.soundfont.synth

import com.example.MidiState
import com.example.domain.audio.SynthBackend
import com.example.domain.audio.SynthStatistics
import com.example.domain.soundfont.dsp.MasterSoftLimiter
import com.example.domain.soundfont.dsp.StereoChorus
import com.example.domain.soundfont.dsp.StereoReverb
import com.example.domain.soundfont.model.InstrumentZone
import com.example.domain.soundfont.model.LoopMode
import com.example.domain.soundfont.model.SampleBuffer
import com.example.domain.soundfont.model.SoundFontPreset
import com.example.domain.soundfont.model.StereoChannel
import com.example.domain.soundfont.repository.OnDemandSoundFontRepository

/**
 * High-performance polyphonic SoundFont (SF2 & SFZ) software synthesizer engine.
 * Supports:
 * - Real stereo voice pairs (Left & Right samples)
 * - Linear interpolation resampling
 * - Loop points
 * - ADSR amplitude envelope
 * - Yamaha XG Drum Tuning (pitchCoarse, pitchFine, level, pan)
 * - DSP Effects (Stereo Reverb, Stereo Chorus, Soft Limiter)
 */
class SoundFontSynthEngine(
    val repository: OnDemandSoundFontRepository,
    private val maxPolyphony: Int = 48
) : SynthBackend {

    private var sampleRate = 44100
    private var sampleRateF = 44100f

    private enum class EnvState {
        IDLE, ATTACK, DECAY, SUSTAIN, RELEASE
    }

    private class Voice {
        var active = false
        var channel = 0
        var note = 0
        var velocity = 0
        var sampleBuffer: SampleBuffer? = null

        var samplePos = 0.0
        var stepRate = 1.0

        var isLooped = false
        var loopStart = 0
        var loopEnd = 0

        var gainL = 0.7f
        var gainR = 0.7f
        var reverbSend = 0.3f
        var chorusSend = 0.0f

        var envState = EnvState.IDLE
        var envLevel = 0.0f
        var attackStep = 0.01f
        var decayStep = 0.005f
        var sustainLevel = 1.0f
        var releaseStep = 0.005f

        fun release() {
            if (active && envState != EnvState.RELEASE && envState != EnvState.IDLE) {
                envState = EnvState.RELEASE
            }
        }

        fun stop() {
            active = false
            envState = EnvState.IDLE
            sampleBuffer = null
        }
    }

    private val voices = Array(maxPolyphony) { Voice() }

    // MIDI Channel States (16 channels)
    private class ChannelState {
        var bankMsb: Int = 0
        var bankLsb: Int = 0
        var program: Int = 0
        var volume: Float = 0.8f // CC 7
        var pan: Float = 0.0f // CC 10: -1.0f to +1.0f
        var expression: Float = 1.0f // CC 11
        var reverbSend: Float = 0.35f // CC 91
        var chorusSend: Float = 0.0f // CC 93
        var dspSend: Float = 0.0f // CC 94
        var pitchBendSemitones: Float = 0.0f
        var isDrumChannel: Boolean = false
        var activePreset: SoundFontPreset? = null
        var assignedSoundFontVoice: com.example.domain.soundfont.model.SoundFontVoice? = null
    }

    private val channels = Array(16) { ch ->
        ChannelState().apply {
            isDrumChannel = (ch == 9 || ch == 8) // Channels 10 and 9 (0-indexed)
        }
    }

    /**
     * Directly binds a channel to a specific SoundFont instrument (e.g. Triton VST preset).
     * Protects this channel against being overwritten by Yamaha style Program Changes.
     */
    fun setChannelVoice(channel: Int, voice: com.example.domain.soundfont.model.SoundFontVoice?) {
        val ch = channel and 0x0F
        channels[ch].assignedSoundFontVoice = voice
        if (voice != null) {
            val meta = repository.getCatalogMetadata(voice.filePath)
            if (meta != null) {
                channels[ch].activePreset = repository.getOrLoadPreset(meta, voice.bank, voice.program)
            } else {
                channels[ch].activePreset = null
            }
        } else {
            updateChannelPreset(ch)
        }
    }

    // DSP Effects
    private var reverb = StereoReverb(sampleRate)
    private var chorus = StereoChorus(sampleRate)

    // Intermediate render buffers (allocated once, zero garbage collection)
    private var internalBufferL = FloatArray(1024)
    private var internalBufferR = FloatArray(1024)
    private var revSendL = FloatArray(1024)
    private var revSendR = FloatArray(1024)
    private var choSendL = FloatArray(1024)
    private var choSendR = FloatArray(1024)

    override fun initialize(sampleRate: Int, framesPerBuffer: Int) {
        this.sampleRate = sampleRate
        this.sampleRateF = sampleRate.toFloat()
        reverb = StereoReverb(sampleRate)
        chorus = StereoChorus(sampleRate)

        val needed = (framesPerBuffer + 64).coerceAtLeast(1024)
        if (internalBufferL.size < needed) {
            internalBufferL = FloatArray(needed)
            internalBufferR = FloatArray(needed)
            revSendL = FloatArray(needed)
            revSendR = FloatArray(needed)
            choSendL = FloatArray(needed)
            choSendR = FloatArray(needed)
        }
    }

    override fun shutdown() {
        panic()
    }

    override fun loadSoundFont(path: String): Int {
        val file = java.io.File(path)
        val meta = repository.registerSoundFont(file)
        return if (meta != null) 1 else -1
    }

    override fun unloadSoundFont(id: Int) {}

    override fun bankSelect(channel: Int, bank: Int) {
        val ch = channel and 0x0F
        channels[ch].bankMsb = bank
        if (channels[ch].assignedSoundFontVoice == null) {
            updateChannelPreset(ch)
        }
    }

    override fun programChange(channel: Int, program: Int) {
        val ch = channel and 0x0F
        channels[ch].program = program
        if (channels[ch].assignedSoundFontVoice == null) {
            updateChannelPreset(ch)
        }
    }

    private fun updateChannelPreset(ch: Int) {
        val state = channels[ch]
        val bank = state.bankMsb
        val prog = state.program
        state.activePreset = repository.findPresetForChannel(ch, bank, prog)
    }

    override fun noteOn(channel: Int, note: Int, velocity: Int) {
        if (velocity == 0) {
            noteOff(channel, note)
            return
        }

        val ch = channel and 0x0F
        val state = channels[ch]

        var preset = state.activePreset
        if (preset == null && state.assignedSoundFontVoice != null) {
            val voice = state.assignedSoundFontVoice!!
            val meta = repository.getCatalogMetadata(voice.filePath)
            if (meta != null) {
                preset = repository.getOrLoadPreset(meta, voice.bank, voice.program)
                state.activePreset = preset
            }
        }
        if (preset == null) {
            preset = repository.findPresetForChannel(ch, state.bankMsb, state.program) ?: repository.defaultPreset
        }

        // 1. Check for Yamaha Drum Key tuning on drum channel
        var drumPitchOffsetSemitones = 0f
        var drumLevelGain = 1.0f
        var drumPanOffset = 0f

        if (state.isDrumChannel) {
            val drumParam = MidiState.liveDrumParams[note]
            if (drumParam != null) {
                // pitchCoarse: 64 = center, pitchFine: 64 = center
                val coarse = drumParam.pitchCoarse - 64
                val fine = drumParam.pitchFine - 64
                drumPitchOffsetSemitones = coarse + (fine / 100.0f)
                drumLevelGain = drumParam.level / 127.0f
                drumPanOffset = (drumParam.pan - 64) / 64.0f
            }
        }

        val matchingZones = preset?.findMatchingZones(note, velocity) ?: emptyList()

        if (matchingZones.isEmpty()) {
            // Allocate fallback procedural tone if soundfont has no sample for this key
            allocateVoice(
                ch = ch,
                note = note,
                velocity = velocity,
                zone = null,
                sample = null,
                drumPitchOffset = drumPitchOffsetSemitones,
                drumLevelGain = drumLevelGain,
                drumPanOffset = drumPanOffset
            )
            return
        }

        // Allocate voice for each matching zone (in Korg Triton, matches 2 zones: Left and Right!)
        for (zone in matchingZones) {
            val sample = zone.sampleBuffer
            allocateVoice(
                ch = ch,
                note = note,
                velocity = velocity,
                zone = zone,
                sample = sample,
                drumPitchOffset = drumPitchOffsetSemitones,
                drumLevelGain = drumLevelGain,
                drumPanOffset = drumPanOffset
            )
        }
    }

    private fun allocateVoice(
        ch: Int,
        note: Int,
        velocity: Int,
        zone: InstrumentZone?,
        sample: SampleBuffer?,
        drumPitchOffset: Float,
        drumLevelGain: Float,
        drumPanOffset: Float
    ) {
        val state = channels[ch]

        // Find free voice or steal oldest in release
        var voice = voices.find { !it.active }
        if (voice == null) {
            voice = voices.filter { it.active && it.envState == EnvState.RELEASE }.minByOrNull { it.envLevel }
        }
        if (voice == null) {
            voice = voices.minByOrNull { it.envLevel } ?: voices[0]
        }

        voice.active = true
        voice.channel = ch
        voice.note = note
        voice.velocity = velocity
        voice.sampleBuffer = sample
        voice.samplePos = 0.0

        val rootKey = zone?.effectiveRootKey ?: sample?.rootKey ?: note
        val fineTuneCents = (zone?.fineTuneCents ?: 0) + (sample?.tuningCents ?: 0)
        val coarseTune = zone?.coarseTuneSemitones ?: 0

        val totalPitchOffset = (note - rootKey) + coarseTune + (fineTuneCents / 100.0f) + state.pitchBendSemitones + drumPitchOffset
        val sampleNativeRate = sample?.sampleRate?.toDouble() ?: 44100.0
        val pitchRatio = Math.pow(2.0, totalPitchOffset / 12.0)
        voice.stepRate = (sampleNativeRate / sampleRateF) * pitchRatio

        voice.isLooped = (zone?.loopMode == LoopMode.CONTINUOUS_LOOP) || (sample?.isLooped == true)
        voice.loopStart = sample?.loopStart ?: 0
        voice.loopEnd = sample?.loopEnd ?: (sample?.data?.size ?: 0)

        // Calculate stereo gains
        val zonePan = zone?.pan ?: 0.0f
        val netPan = (state.pan + zonePan + drumPanOffset).coerceIn(-1.0f, 1.0f)
        val volVelGain = (velocity / 127.0f) * state.volume * state.expression * drumLevelGain

        // Equal-power stereo panning
        val panAngle = (netPan + 1.0f) * (Math.PI / 4.0) // 0 to pi/2
        voice.gainL = (Math.cos(panAngle) * volVelGain).toFloat() * 0.45f
        voice.gainR = (Math.sin(panAngle) * volVelGain).toFloat() * 0.45f

        // Boost for single-channel stereo sample (if Left-only or Right-only)
        if (sample?.stereoChannel == StereoChannel.LEFT) {
            voice.gainL *= 1.3f
            voice.gainR *= 0.1f
        } else if (sample?.stereoChannel == StereoChannel.RIGHT) {
            voice.gainL *= 0.1f
            voice.gainR *= 1.3f
        }

        voice.reverbSend = state.reverbSend
        voice.chorusSend = state.chorusSend

        // Envelope setup
        val attackSec = zone?.attackSec ?: 0.002f
        val decaySec = zone?.decaySec ?: 0.1f
        voice.sustainLevel = zone?.sustainLevel ?: 1.0f
        val releaseSec = zone?.releaseSec ?: 0.25f

        voice.attackStep = (1.0f / (attackSec * sampleRateF)).coerceIn(0.0001f, 1.0f)
        voice.decayStep = (1.0f / (decaySec * sampleRateF)).coerceIn(0.00001f, 1.0f)
        voice.releaseStep = (1.0f / (releaseSec * sampleRateF)).coerceIn(0.00001f, 1.0f)

        voice.envState = EnvState.ATTACK
        voice.envLevel = 0.0f
    }

    override fun noteOff(channel: Int, note: Int) {
        val ch = channel and 0x0F
        for (v in voices) {
            if (v.active && v.channel == ch && v.note == note) {
                v.release()
            }
        }
    }

    override fun pitchBend(channel: Int, value: Int) {
        val ch = channel and 0x0F
        // 8192 is center, range -2 to +2 semitones
        channels[ch].pitchBendSemitones = ((value - 8192) / 8192.0f) * 2.0f
    }

    override fun controlChange(channel: Int, controller: Int, value: Int) {
        val ch = channel and 0x0F
        val state = channels[ch]
        when (controller) {
            7 -> state.volume = value / 127.0f // Volume
            10 -> state.pan = ((value - 64) / 64.0f).coerceIn(-1.0f, 1.0f) // Pan
            11 -> state.expression = value / 127.0f // Expression
            91 -> state.reverbSend = value / 127.0f // Reverb Send
            93 -> state.chorusSend = value / 127.0f // Chorus Send
            94 -> state.dspSend = value / 127.0f // DSP Send
            120, 123 -> panic() // All Sounds Off / All Notes Off
        }
    }

    override fun afterTouch(channel: Int, note: Int, value: Int) {}
    override fun channelPressure(channel: Int, value: Int) {}
    override fun sysEx(data: ByteArray) {
        // Yamaha XG SysEx Parser for Drum Setup if received directly
        if (data.size >= 9 && data[0] == 0xF0.toByte() && data[1] == 0x43.toByte() && data[2] == 0x10.toByte() && data[3] == 0x4C.toByte()) {
            val highAddr = data[4].toInt() and 0xFF
            val midAddr = data[5].toInt() and 0xFF // drum key (pitch, level, pan, etc.)
            val lowAddr = data[6].toInt() and 0xFF
            val value = data[7].toInt() and 0xFF
            // Sync with live drum setup
            if (highAddr in 0x30..0x3F) {
                val drumKey = lowAddr
                val param = MidiState.liveDrumParams.getOrPut(drumKey) { com.example.DrumKeyParams() }
                when (midAddr) {
                    2 -> param.pitchCoarse = value
                    3 -> param.pitchFine = value
                    4 -> param.level = value
                    6 -> param.pan = value
                    7 -> param.reverb = value
                    8 -> param.chorus = value
                }
            }
        }
    }

    override fun render(buffer: FloatArray, offset: Int, frameCount: Int) {
        // Clear working buffers
        internalBufferL.fill(0f, 0, frameCount)
        internalBufferR.fill(0f, 0, frameCount)
        revSendL.fill(0f, 0, frameCount)
        revSendR.fill(0f, 0, frameCount)
        choSendL.fill(0f, 0, frameCount)
        choSendR.fill(0f, 0, frameCount)

        // Render active voices
        for (voice in voices) {
            if (!voice.active) continue
            renderVoice(voice, frameCount)
        }

        // Apply Chorus Effect
        chorus.process(choSendL, choSendR, internalBufferL, internalBufferR, frameCount, 1.0f)

        // Apply Reverb Effect
        reverb.process(revSendL, revSendR, internalBufferL, internalBufferR, frameCount, 1.0f)

        // Interleave Left and Right channels into destination buffer
        for (i in 0 until frameCount) {
            buffer[offset + i * 2] = internalBufferL[i]
            buffer[offset + i * 2 + 1] = internalBufferR[i]
        }

        // Apply transparent Master Soft Limiter
        MasterSoftLimiter.process(buffer, offset, frameCount, 2)
    }

    private fun renderVoice(voice: Voice, frameCount: Int) {
        val sampleBuf = voice.sampleBuffer
        val pcm = sampleBuf?.data

        for (i in 0 until frameCount) {
            // Update ADSR amplitude envelope
            when (voice.envState) {
                EnvState.ATTACK -> {
                    voice.envLevel += voice.attackStep
                    if (voice.envLevel >= 1.0f) {
                        voice.envLevel = 1.0f
                        voice.envState = EnvState.DECAY
                    }
                }
                EnvState.DECAY -> {
                    voice.envLevel -= voice.decayStep
                    if (voice.envLevel <= voice.sustainLevel) {
                        voice.envLevel = voice.sustainLevel
                        voice.envState = EnvState.SUSTAIN
                    }
                }
                EnvState.SUSTAIN -> {
                    // Constant level until noteOff
                }
                EnvState.RELEASE -> {
                    voice.envLevel -= voice.releaseStep
                    if (voice.envLevel <= 0.0001f) {
                        voice.stop()
                        return
                    }
                }
                EnvState.IDLE -> {
                    voice.stop()
                    return
                }
            }

            // Sample Resampling / Interpolation
            var rawSample = 0.0f
            if (pcm != null && pcm.isNotEmpty()) {
                val pos = voice.samplePos
                val i0 = pos.toInt()

                if (voice.isLooped && voice.loopEnd > voice.loopStart) {
                    if (pos >= voice.loopEnd) {
                        val loopLen = voice.loopEnd - voice.loopStart
                        voice.samplePos = voice.loopStart + ((pos - voice.loopEnd) % loopLen)
                    }
                } else if (i0 >= pcm.size - 1) {
                    voice.stop()
                    return
                }

                val currPos = voice.samplePos
                val currI0 = currPos.toInt()
                val frac = (currPos - currI0).toFloat()
                val currI1 = if (currI0 + 1 < pcm.size) currI0 + 1 else currI0

                // Linear interpolation
                rawSample = pcm[currI0] + frac * (pcm[currI1] - pcm[currI0])
                voice.samplePos += voice.stepRate
            } else {
                // Procedural triangle/sine fallback if no sample data loaded
                val freq = 440.0 * Math.pow(2.0, (voice.note - 69) / 12.0)
                val phaseInc = freq / sampleRateF
                voice.samplePos += phaseInc
                val normPhase = (voice.samplePos % 1.0).toFloat()
                rawSample = if (normPhase < 0.5f) 4f * normPhase - 1f else 3f - 4f * normPhase
            }

            val ampSample = rawSample * voice.envLevel
            val sL = ampSample * voice.gainL
            val sR = ampSample * voice.gainR

            internalBufferL[i] += sL
            internalBufferR[i] += sR

            if (voice.reverbSend > 0.001f) {
                revSendL[i] += sL * voice.reverbSend
                revSendR[i] += sR * voice.reverbSend
            }
            if (voice.chorusSend > 0.001f) {
                choSendL[i] += sL * voice.chorusSend
                choSendR[i] += sR * voice.chorusSend
            }
        }
    }

    override fun panic() {
        reset()
        reverb.clear()
        chorus.clear()
    }

    override fun reset() {
        for (v in voices) {
            v.stop()
        }
    }

    override fun getStatistics(): SynthStatistics {
        val activeCount = voices.count { it.active }
        return SynthStatistics(
            activeVoices = activeCount,
            cpuUsage = (activeCount / maxPolyphony.toFloat()) * 100f,
            memoryUsage = (repository.getLoadedCount() * 11500000L)
        )
    }
}
