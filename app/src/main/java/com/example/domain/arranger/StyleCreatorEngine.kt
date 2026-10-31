package com.example.domain.arranger

import com.example.domain.models.Chord
import com.example.domain.models.StyleSection
import com.example.RhythmTrack
import com.example.MidiState
import com.example.domain.midi_core.MidiDispatcher
import com.example.domain.casm.CasmPlaybackEngine
import java.util.concurrent.atomic.AtomicBoolean

class StyleCreatorEngine(
    private val state: ArrangerCoreState,
    private val ppq: Int = 1920
) {
    val casmEngine = CasmPlaybackEngine(state)
    val tracks: List<RhythmTrack> get() = com.example.MidiRhythmState.tracks
    val metronomeEnabled: Boolean get() = com.example.MidiRhythmState.metronomeEnabled
    val timeSigNumerator: Int get() = com.example.MidiRhythmState.timeSigNumerator
    val timeSigDenominator: Int get() = com.example.MidiRhythmState.timeSigDenominator
    val loopMeasures: Int get() = com.example.MidiRhythmState.measures
    val detectedChord: Chord? get() = com.example.MidiRhythmState.detectedChord
    val currentStyleCasm: com.example.domain.models.StyleCasm? get() = com.example.MidiRhythmState.currentStyle?.casm
    val section: StyleSection get() = when (com.example.MidiRhythmState.currentSection) {
        com.example.StyleSection.INTRO_A -> com.example.domain.models.StyleSection.IntroA
        com.example.StyleSection.INTRO_B -> com.example.domain.models.StyleSection.IntroB
        com.example.StyleSection.INTRO_C -> com.example.domain.models.StyleSection.IntroC
        com.example.StyleSection.MAIN_A -> com.example.domain.models.StyleSection.MainA
        com.example.StyleSection.MAIN_B -> com.example.domain.models.StyleSection.MainB
        com.example.StyleSection.MAIN_C -> com.example.domain.models.StyleSection.MainC
        com.example.StyleSection.MAIN_D -> com.example.domain.models.StyleSection.MainD
        com.example.StyleSection.FILL_A -> com.example.domain.models.StyleSection.FillInAA
        com.example.StyleSection.FILL_B -> com.example.domain.models.StyleSection.FillInBB
        com.example.StyleSection.FILL_C -> com.example.domain.models.StyleSection.FillInCC
        com.example.StyleSection.FILL_D -> com.example.domain.models.StyleSection.FillInDD
        com.example.StyleSection.ENDING_A -> com.example.domain.models.StyleSection.EndingA
        com.example.StyleSection.ENDING_B -> com.example.domain.models.StyleSection.EndingB
        com.example.StyleSection.ENDING_C -> com.example.domain.models.StyleSection.EndingC
    }
    
    private val activePlayingNotes = ArrayList<PlayingNote>()
    private var isRunning = AtomicBoolean(false)
    private var clock: ArrangerClock? = null
    private var currentTick = 0
    private var lastUiUpdateNs = 0L
    
    data class PlayingNote(val channel: Int, val note: Int, val absoluteOffTick: Long)
    
    fun updateTracks(newTracks: List<RhythmTrack>) {}

    fun start() {
        if (isRunning.getAndSet(true)) return
        currentTick = 0
        lastUiUpdateNs = 0L
        synchronized(activePlayingNotes) {
            activePlayingNotes.clear()
        }
        
        // 1. Configure PART MODE first (Normal vs Drum) as required by Yamaha XG spec
        tracks.forEach { track ->
            val xgPart = track.channel - 1
            val setupNum = if (track.isDrumTrack) {
                if (track.channel == 9) 0x02.toByte() else 0x01.toByte()
            } else {
                0x00.toByte()
            }
            MidiState.sendSysEx(byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, 0x08, xgPart.toByte(), 0x07, setupNum, 0xF7.toByte()))
        }
        Thread.sleep(30)
        
        // Preload active rhythm soundfonts + live performance parts to guarantee immediate zero-latency playback
        val rhythmVoices = tracks.mapNotNull { it.soundFontVoice }
        val liveVoices = listOf(
            com.example.domain.models.VoiceEngine.channels[com.example.domain.models.VoiceEngine.CH_RIGHT1].soundFontVoice,
            com.example.domain.models.VoiceEngine.channels[com.example.domain.models.VoiceEngine.CH_RIGHT2].soundFontVoice,
            com.example.domain.models.VoiceEngine.channels[com.example.domain.models.VoiceEngine.CH_LEFT].soundFontVoice
        ).filterNotNull()
        com.example.MidiState.soundFontRepository.preloadActiveRhythmAndParts(rhythmVoices, liveVoices)

        // 2. Send Bank Select, Program Change, and Part Controls (Vol, Pan, Rev, Cho, DSP)
        tracks.forEach { track ->
            val ch = track.channel - 1
            val xgPart = ch
            if (track.soundFontVoice != null) {
                com.example.MidiState.soundFontSynth.setChannelVoice(ch, track.soundFontVoice)
                com.example.MidiState.voiceManager?.setSoundFontMapping(ch, track.soundFontVoice!!)
            } else {
                MidiDispatcher.sendProgramChange(ch, track.bankMsb, track.bankLsb, track.program)
            }
            MidiDispatcher.sendControlChange(ch, 7, track.volume)
            MidiDispatcher.sendControlChange(ch, 10, track.pan)
            MidiDispatcher.sendControlChange(ch, 91, track.reverb)
            MidiDispatcher.sendControlChange(ch, 93, track.chorus)
            MidiDispatcher.sendControlChange(ch, 94, track.dsp)
            
            if (track.isDrumTrack) {
                track.drumParams.forEach { (key, params) ->
                    val sendNRPN = { nrpnMsb: Int, value: Int ->
                        MidiState.sendControlChange(xgPart, 99, nrpnMsb)
                        MidiState.sendControlChange(xgPart, 98, key)
                        MidiState.sendControlChange(xgPart, 6, value)
                    }
                    sendNRPN(24, params.pitchCoarse)
                    sendNRPN(25, params.pitchFine)
                    sendNRPN(26, params.level)
                    sendNRPN(28, params.pan)
                    sendNRPN(29, params.reverb)
                    sendNRPN(30, params.chorus)
                    
                    val setupByte = if (track.channel == 10) 0x30.toByte() else 0x31.toByte()
                    val sysexHeader = byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, setupByte, key.toByte())
                    MidiState.sendSysEx(sysexHeader + byteArrayOf(0, params.pitchCoarse.toByte(), 0xF7.toByte()))
                    MidiState.sendSysEx(sysexHeader + byteArrayOf(1, params.pitchFine.toByte(), 0xF7.toByte()))
                    MidiState.sendSysEx(sysexHeader + byteArrayOf(2, params.level.toByte(), 0xF7.toByte()))
                    MidiState.sendSysEx(sysexHeader + byteArrayOf(4, params.pan.toByte(), 0xF7.toByte()))
                    MidiState.sendSysEx(sysexHeader + byteArrayOf(5, params.reverb.toByte(), 0xF7.toByte()))
                    MidiState.sendSysEx(sysexHeader + byteArrayOf(6, params.chorus.toByte(), 0xF7.toByte()))
                    Thread.sleep(5)
                }
            }
        }
        Thread.sleep(30)
        
        clock = ArrangerClock(state, ppq, ::onTick)
        clock?.start()
    }

    private var pendingMetronomeOffTick = -1
    private var pendingMetronomeNote = -1

    fun stop() {
        isRunning.set(false)
        clock?.stop()
        clock = null
        if (pendingMetronomeNote != -1) {
            com.example.domain.midi_core.MidiDispatcher.sendNoteOff(com.example.domain.metronome.MetronomeConfig.channel, pendingMetronomeNote, 0, 0L)
            pendingMetronomeNote = -1
        }
        synchronized(activePlayingNotes) {
            activePlayingNotes.forEach {
                MidiState.sendNoteOff(it.channel, it.note, 0, System.nanoTime())
            }
            activePlayingNotes.clear()
        }
        for (i in 0..15) {
            MidiState.sendControlChange(i, 123, 0, 0L)
        }
    }

    private fun onTick(clockTick: Int, targetTimeNs: Long) {
        val totalTicks = maxOf(1, loopMeasures * timeSigNumerator * ((ppq * 4) / timeSigDenominator))
        
        // Turn off previous metronome note when scheduled tick is reached
        if (pendingMetronomeNote != -1 && currentTick >= pendingMetronomeOffTick) {
            com.example.domain.midi_core.MidiDispatcher.sendNoteOff(com.example.domain.metronome.MetronomeConfig.channel, pendingMetronomeNote, 0, targetTimeNs)
            pendingMetronomeNote = -1
        }

        // Process Note Offs without allocating iterators or array copies
        synchronized(activePlayingNotes) {
            for (i in activePlayingNotes.indices.reversed()) {
                val pNote = activePlayingNotes[i]
                if (pNote.absoluteOffTick <= clockTick) {
                    MidiDispatcher.sendNoteOff(pNote.channel, pNote.note, 0, targetTimeNs)
                    activePlayingNotes.removeAt(i)
                }
            }
        }
        
        val ticksPerBeat = (ppq * 4) / timeSigDenominator
        val isMetronomeOn = metronomeEnabled || com.example.domain.metronome.MetronomeConfig.enabled
        if (isMetronomeOn && currentTick % ticksPerBeat == 0) {
            val isFirstBeat = (currentTick % (timeSigNumerator * ticksPerBeat)) == 0
            val note = if (isFirstBeat) com.example.domain.metronome.MetronomeConfig.downbeatNote else com.example.domain.metronome.MetronomeConfig.upbeatNote
            val vel = if (isFirstBeat) com.example.domain.metronome.MetronomeConfig.accentVelocity else com.example.domain.metronome.MetronomeConfig.normalVelocity
            val ch = com.example.domain.metronome.MetronomeConfig.channel

            com.example.domain.midi_core.MidiDispatcher.sendControlChange(ch, 7, com.example.domain.metronome.MetronomeConfig.volume, targetTimeNs)
            com.example.domain.midi_core.MidiDispatcher.sendControlChange(ch, 11, 127, targetTimeNs)

            com.example.domain.midi_core.MidiDispatcher.sendNoteOn(ch, note, vel, targetTimeNs)

            pendingMetronomeNote = note
            pendingMetronomeOffTick = currentTick + (ticksPerBeat / 4).coerceAtLeast(1)
        }
        
        // Update CASM Engine if we have styles
        currentStyleCasm?.let {
            casmEngine.updateStyle(it.sections)
        }

        tracks.forEach { track ->
            if (!track.isMuted) {
                val ccs = track.getCCsAt(currentTick)
                if (ccs != null && ccs.isNotEmpty()) {
                    var pitchLsb = 0
                    var pitchMsb = -1
                    for (i in 0 until ccs.size) {
                        val cc = ccs[i]
                        when {
                            cc.cc == 128 -> MidiState.sendChannelPressure(track.channel - 1, cc.value, targetTimeNs)
                            cc.cc == 129 -> pitchLsb = cc.value
                            cc.cc == 130 -> {
                                pitchMsb = cc.value
                                MidiState.sendPitchBend(track.channel - 1, pitchLsb, pitchMsb, targetTimeNs)
                            }
                            cc.cc < 128 -> MidiState.sendControlChange(track.channel - 1, cc.cc, cc.value, targetTimeNs)
                        }
                    }
                }
                
                val notes = track.getNotesAt(currentTick)
                if (notes != null && notes.isNotEmpty()) {
                    for (i in 0 until notes.size) {
                        val note = notes[i]
                        val scaledVel = (note.velocity * (track.volume / 100f)).toInt().coerceIn(1, 127)
                        var finalNote = note.note
                        
                        val detected = detectedChord
                        if (detected != null) {
                            finalNote = casmEngine.processNote(
                                originalNote = note.note,
                                channel = track.channel - 1,
                                sectionName = section.name,
                                currentChord = detected,
                                currentBassNote = detected.bassKey,
                                overrideSourceRootKey = track.sourceRoot?.rootKey
                            )
                        }
                        
                        MidiDispatcher.sendNoteOn(track.channel - 1, finalNote, scaledVel, targetTimeNs)
                        val absOffTick = clockTick + note.durationTicks
                        synchronized(activePlayingNotes) {
                            activePlayingNotes.add(PlayingNote(track.channel - 1, finalNote, absOffTick.toLong()))
                        }
                    }
                }
            }
        }
        
        currentTick = (currentTick + 1) % totalTicks
        com.example.MidiRhythmState.currentTick = currentTick
        // Throttle UI update to ~60 FPS (every ~16ms) to eliminate Compose snapshot allocations & GC pauses
        val nowNs = targetTimeNs
        if (nowNs - lastUiUpdateNs >= 16_666_666L) {
            lastUiUpdateNs = nowNs
            com.example.MidiRhythmState.uiTick = currentTick
        }
    }
}
