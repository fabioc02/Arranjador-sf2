package com.example.domain.audio

import com.example.domain.midi.MidiEventQueue
import com.example.domain.midi.MidiMessage
import com.example.domain.midi.MidiMessageType

class AudioEngineImpl(
    private val midiEventQueue: MidiEventQueue,
    private val synth: SynthBackend,
    private val audioBackend: AudioBackend
) : AudioEngine, AudioRenderer {
    
    override val monitor = LatencyMonitor()
    override val settings = AudioSettings()
    override val mixer = AudioMixer()
    override val masterBus = MasterBus()
    override val soundFontManager = SoundFontManager(synth)
    override val instrumentManager = InstrumentManager(soundFontManager, synth)
    override val profiler = PerformanceProfiler()
    override val voiceManager = VoiceManager()
    
    override fun start() {
        audioBackend.setRenderer(this)
        audioBackend.start(settings.sampleRate, settings.framesPerBuffer)
    }
    
    override fun stop() {
        audioBackend.stop()
    }
    
    override fun render(buffer: FloatArray, frameCount: Int) {
        val startRenderUs = System.nanoTime() / 1000
        
        // Process MIDI events from queue before rendering audio
        processMidiEvents()
        
        // Render synth output to buffer
        val startSynthUs = System.nanoTime() / 1000
        synth.render(buffer, 0, frameCount)
        val synthUs = (System.nanoTime() / 1000) - startSynthUs
        profiler.updateSynthTime(synthUs)
        
        // Apply Mixer
        val startMixerUs = System.nanoTime() / 1000
        mixer.process(buffer, frameCount)
        val mixerUs = (System.nanoTime() / 1000) - startMixerUs
        profiler.updateMixerTime(mixerUs)
        
        // Apply Master Bus
        val startMasterUs = System.nanoTime() / 1000
        masterBus.process(buffer, frameCount)
        val masterUs = (System.nanoTime() / 1000) - startMasterUs
        
        val totalUs = (System.nanoTime() / 1000) - startRenderUs
        profiler.updateRenderTime(totalUs)
        
        val stats = synth.getStatistics()
        profiler.updateVoiceCount(stats.activeVoices)
        
        monitor.update(totalUs, mixerUs, masterUs, stats.activeVoices)
    }
    
    private fun processMidiEvents() {
        var msg = midiEventQueue.poll()
        while (msg != null) {
            when (msg.type) {
                MidiMessageType.NoteOn -> {
                    synth.noteOn(msg.channel, msg.data1, msg.data2)
                    voiceManager.allocateVoice(msg.channel, msg.data1, msg.data2)
                }
                MidiMessageType.NoteOff -> {
                    synth.noteOff(msg.channel, msg.data1)
                    voiceManager.releaseVoice(msg.channel, msg.data1)
                }
                MidiMessageType.ControlChange -> {
                    synth.controlChange(msg.channel, msg.data1, msg.data2)
                    if (msg.data1 == 64) {
                        voiceManager.onSustainPedal(msg.data2 >= 64)
                    }
                }
                MidiMessageType.ProgramChange -> {
                    synth.programChange(msg.channel, msg.data1)
                }
                MidiMessageType.PitchBend -> {
                    synth.pitchBend(msg.channel, msg.data1)
                }
                MidiMessageType.SysEx -> {
                    if (msg.sysExData != null) {
                        synth.sysEx(msg.sysExData)
                    }
                }
                else -> {}
            }
            msg = midiEventQueue.poll()
        }
    }
}
