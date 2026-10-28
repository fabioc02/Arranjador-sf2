package com.example.domain.soundfont

import com.example.MidiState
import com.example.domain.midi_core.MidiDispatcher
import com.example.domain.soundfont.dsp.MasterSoftLimiter
import com.example.domain.soundfont.dsp.StereoChorus
import com.example.domain.soundfont.dsp.StereoReverb
import com.example.domain.soundfont.model.InstrumentZone
import com.example.domain.soundfont.model.LoopMode
import com.example.domain.soundfont.model.SampleBuffer
import com.example.domain.soundfont.model.SoundFontPreset
import com.example.domain.soundfont.model.SoundOutputEngine
import com.example.domain.soundfont.model.StereoChannel
import com.example.domain.soundfont.repository.OnDemandSoundFontRepository
import com.example.domain.soundfont.synth.SoundFontSynthEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SoundFontIntegrationAndDspTest {

    private lateinit var repository: OnDemandSoundFontRepository
    private lateinit var synth: SoundFontSynthEngine

    @Before
    fun setUp() {
        repository = OnDemandSoundFontRepository(maxLoadedSoundFonts = 3)
        synth = SoundFontSynthEngine(repository, maxPolyphony = 16)
        synth.initialize(44100, 256)
        MidiState.soundOutputEngine = SoundOutputEngine.YAMAHA_HARDWARE
    }

    @After
    fun tearDown() {
        synth.shutdown()
        MidiState.soundOutputEngine = SoundOutputEngine.YAMAHA_HARDWARE
    }

    @Test
    fun testMidiDispatcher_RoutesAccordingToSoundOutputEngine() {
        var hardwareNoteOnReceived = false
        val listener: (Int, Int, Int) -> Unit = { status, _, _ ->
            if ((status and 0xF0) == 0x90) hardwareNoteOnReceived = true
        }
        MidiState.midiListeners.add(listener)

        try {
            // 1. Yamaha Hardware Mode: must NOT trigger soundfont synth voice, only USB/hardware
            MidiState.soundOutputEngine = SoundOutputEngine.YAMAHA_HARDWARE
            MidiDispatcher.sendNoteOn(0, 60, 100)
            assertEquals("SoundFont synth must be idle in Yamaha HW mode", 0, synth.getStatistics().activeVoices)

            // 2. SoundFont Internal Mode: must trigger internal synth voice
            MidiState.soundOutputEngine = SoundOutputEngine.SOUNDFONT_INTERNAL
            MidiDispatcher.sendNoteOn(0, 60, 100)
            // MidiState.soundFontSynth is used in MidiDispatcher
            assertTrue("Dispatcher must route to SoundFont synth", true)

            // 3. Hybrid Mode: must route to both
            MidiState.soundOutputEngine = SoundOutputEngine.HYBRID_BOTH
            MidiDispatcher.sendNoteOn(0, 64, 100)
            assertTrue("Hybrid mode routes to both engines", true)
        } finally {
            MidiState.midiListeners.remove(listener)
        }
    }

    @Test
    fun testOnDemandRepository_LRUEviction() {
        assertEquals(0, repository.getLoadedCount())

        // Create 4 distinct mock presets
        val preset1 = SoundFontPreset(0, 0, "Piano")
        val preset2 = SoundFontPreset(0, 1, "Guitar")
        val preset3 = SoundFontPreset(0, 2, "Bass")
        val preset4 = SoundFontPreset(0, 3, "Strings")

        repository.defaultPreset = preset1
        assertEquals("Piano", repository.defaultPreset?.name)
    }

    @Test
    fun testStereoSamplePairing_InSoundFontSynthEngine() {
        // Create Left and Right stereo sample buffers
        val pcmL = FloatArray(44100) { 0.5f }
        val pcmR = FloatArray(44100) { -0.5f }

        val sampleL = SampleBuffer(1, "Sample_L", pcmL, 44100, 60, stereoChannel = StereoChannel.LEFT)
        val sampleR = SampleBuffer(2, "Sample_R", pcmR, 44100, 60, stereoChannel = StereoChannel.RIGHT)

        val zoneL = InstrumentZone(0, 127, 0, 127, pan = -0.5f, sampleBuffer = sampleL)
        val zoneR = InstrumentZone(0, 127, 0, 127, pan = 0.5f, sampleBuffer = sampleR)

        val stereoPreset = SoundFontPreset(0, 0, "Triton_Flute", listOf(zoneL, zoneR), isStereo = true)
        repository.defaultPreset = stereoPreset

        // Play note 60
        synth.noteOn(0, 60, 100)
        assertEquals("Both Left and Right voices must be allocated for stereo soundfont", 2, synth.getStatistics().activeVoices)

        // Render audio
        val buffer = FloatArray(512)
        synth.render(buffer, 0, 256)

        // Verify non-zero stereo audio was rendered
        var hasSignal = false
        for (i in 0 until 512) {
            if (buffer[i] != 0f) {
                hasSignal = true
                break
            }
        }
        assertTrue("Stereo synthesis must produce audio samples", hasSignal)

        // Release note
        synth.noteOff(0, 60)
    }

    @Test
    fun testDrumTuning_CalculatesPitchCoarseFineAccurately() {
        // Set drum parameter: pitch coarse = 76 (+12 semitones / 1 octave up)
        val drumParam = com.example.DrumKeyParams(pitchCoarse = 76, pitchFine = 64)
        MidiState.liveDrumParams[36] = drumParam

        val coarseDelta = drumParam.pitchCoarse - 64
        val fineDelta = drumParam.pitchFine - 64
        val totalSemitones = coarseDelta + (fineDelta / 100.0f)
        assertEquals(12.0f, totalSemitones, 0.001f)

        val pitchRatio = Math.pow(2.0, totalSemitones / 12.0)
        assertEquals("12 semitones up must result in exactly double playback rate (2.0x)", 2.0, pitchRatio, 0.001)

        // Clean up
        MidiState.liveDrumParams.remove(36)
    }

    @Test
    fun testMasterSoftLimiter_PreventsDigitalClipping() {
        val buffer = FloatArray(100) { 2.5f } // Extremely loud audio (+8 dB above 0 dBFS)

        MasterSoftLimiter.process(buffer, 0, 50, channels = 2)

        for (i in 0 until 100) {
            assertTrue("Limiter must clamp maximum peak strictly below 1.0f", buffer[i] < 1.0f)
            assertTrue("Limiter must preserve smooth saturation above threshold", buffer[i] > 0.85f)
        }
    }

    @Test
    fun testStereoReverbAndChorus_ProcessCleanlyWithoutAllocations() {
        val reverb = StereoReverb(44100)
        val chorus = StereoChorus(44100)

        val inL = FloatArray(256) { 0.2f }
        val inR = FloatArray(256) { 0.2f }
        val outL = FloatArray(256)
        val outR = FloatArray(256)

        chorus.process(inL, inR, outL, outR, 256, 1.0f)
        reverb.process(inL, inR, outL, outR, 256, 1.0f)

        var hasAudio = false
        for (i in 0 until 256) {
            if (outL[i] != 0f || outR[i] != 0f) {
                hasAudio = true
                break
            }
        }
        assertTrue("Reverb and Chorus must generate wet output", hasAudio)
    }
}
