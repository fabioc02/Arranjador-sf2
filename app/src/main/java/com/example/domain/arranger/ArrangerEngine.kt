package com.example.domain.arranger

import com.example.domain.models.StyleSection
import com.example.domain.casm.CasmPlaybackEngine
import com.example.domain.midi_core.MidiDispatcher
import com.example.domain.models.MidiEvent
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ARRANGER ENGINE
 * O coração do sistema. Roda isolado da UI.
 */
class ArrangerEngine(
    val state: ArrangerCoreState,
    val ppq: Int = 1920
) {
    private val clock = ArrangerClock(state, ppq, ::onTick)
    val chordDetector = ChordDetectorCore(state)
    val casmEngine = CasmPlaybackEngine(state)
    var voiceManager: VoiceManager? = null
    
    private val commandQueue = ConcurrentLinkedQueue<ArrangerCommand>()

    // Maps original (untransposed) Note to Transposed Note for reliable NoteOff
    private val activeTransposedNotes = mutableMapOf<Pair<Int, Int>, Int>() // (Channel, OriginalNote) -> TransposedNote

    fun start() {
        if (!state.isPlaying.get()) {
            state.resetTick()
            state.isPlaying.set(true)
            state.isPaused.set(false)
            clock.start()
            for (i in 0..15) {
                MidiDispatcher.sendControlChange(i, 123, 0, 0L)
                MidiDispatcher.sendControlChange(i, 120, 0, 0L)
            }
            activeTransposedNotes.clear()
        }
    }

    fun stop() {
        state.isPlaying.set(false)
        clock.stop()
        state.resetTick()
        if (pendingMetronomeNote != -1) {
            MidiDispatcher.sendNoteOff(com.example.domain.metronome.MetronomeConfig.channel, pendingMetronomeNote, 0, 0L)
            pendingMetronomeNote = -1
        }
        for (i in 0..15) {
            MidiDispatcher.sendControlChange(i, 123, 0, 0L)
            MidiDispatcher.sendControlChange(i, 120, 0, 0L)
        }
        activeTransposedNotes.clear()
    }
    
    fun sendCommand(cmd: ArrangerCommand) {
        commandQueue.add(cmd)
        if (!state.isPlaying.get()) {
            processCommands()
        }
    }

        private fun sendOverrides() {
        voiceManager?.channelRemap?.forEach { (channel, voice) ->
            MidiDispatcher.sendControlChange(channel, 0, voice.msb, System.nanoTime())
            MidiDispatcher.sendControlChange(channel, 32, voice.lsb, System.nanoTime())
            MidiDispatcher.sendProgramChange(channel, voice.msb, voice.lsb, voice.pc)
        }
    }

    private fun processCommands() {
        while (true) {
            val cmd = commandQueue.poll() ?: break
            when (cmd) {
                is ArrangerCommand.ChangeSection -> {
                    state.nextSection.set(cmd.section)
                }
                is ArrangerCommand.UpdateBpm -> {
                    state.bpm = cmd.bpm
                }
                is ArrangerCommand.ToggleLeftHold -> {
                    state.leftHoldEnabled.set(cmd.enabled)
                    if (!cmd.enabled) chordDetector.clearLeftHold()
                }
                is ArrangerCommand.LoadStyle -> {
                    state.loadedStyle.set(cmd.styFile)
                    casmEngine.updateStyle(cmd.styFile.casm.sections)
                    
                    // Send global initialization events (at tick 0) from the first section
                    val firstSection = cmd.styFile.sections.values.minByOrNull { it.startTick }
                    if (firstSection != null) {
                        val initEvents = firstSection.tracks.values.flatMap { track ->
                            track.events.filter { it.tick == 0L }
                        }
                        
                        val targetTimeNs = System.nanoTime()
                        initEvents.forEach { event ->
                            when (event) {
                                is com.example.domain.models.MidiEvent.ControlChange -> {
                                    val override = voiceManager?.channelRemap?.get(event.channel)
                                    if (override != null && (event.controller == 0 || event.controller == 32)) {
                                        // Ignore Style Bank Select if user overridden
                                    } else {
                                        com.example.domain.midi_core.MidiDispatcher.sendControlChange(event.channel, event.controller, event.value, targetTimeNs)
                                    }
                                }
                                is com.example.domain.models.MidiEvent.ProgramChange -> {
                                    val override = voiceManager?.channelRemap?.get(event.channel)
                                    if (override != null) {
                                        com.example.domain.midi_core.MidiDispatcher.sendControlChange(event.channel, 0, override.msb, targetTimeNs)
                                        com.example.domain.midi_core.MidiDispatcher.sendControlChange(event.channel, 32, override.lsb, targetTimeNs)
                                        com.example.MidiState.sendProgramChangeRaw(event.channel, override.pc, targetTimeNs)
                                    } else {
                                        com.example.MidiState.sendProgramChangeRaw(event.channel, event.program, targetTimeNs)
                                    }
                                }
                                is com.example.domain.models.MidiEvent.SysEx -> {
                                    com.example.MidiState.sendSysEx(event.data)
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onTick(tick: Int, targetTimeNs: Long) {
        processCommands()
        manageLoopAndSections(tick)
        processStyleTracks(state.currentTick.get(), targetTimeNs)
        processMetronome(tick, targetTimeNs)
    }
    
    private fun processStyleTracks(currentTick: Int, targetTimeNs: Long) {
        val style = state.loadedStyle.get() ?: return
        val currentSection = state.currentSection.get()
        val sectionData = style.sections[currentSection] ?: return
        
        val tickInLoop = currentTick.toLong()
        
        sectionData.tracks.forEach { (channel, track) ->
            val eventsNow = track.events.filter { it.tick == tickInLoop }
            
            eventsNow.forEach { event ->
                when (event) {
                    is MidiEvent.NoteOn -> {
                        if (event.velocity > 0) {
                            val currentChord = state.currentChord.get()
                            var transposedNote = casmEngine.processNote(
                                originalNote = event.note,
                                channel = event.channel,
                                sectionName = sectionData.name,
                                currentChord = currentChord,
                                currentBassNote = state.currentBassNote
                            )
                            if (event.channel == 9 || event.channel == 8) {
                                transposedNote = voiceManager?.drumNoteRemap?.get(transposedNote) ?: transposedNote
                            }
                            MidiDispatcher.sendNoteOn(event.channel, transposedNote, event.velocity, targetTimeNs)
                            activeTransposedNotes[Pair(event.channel, event.note)] = transposedNote
                        } else {
                            // Velocity 0 is NoteOff
                            val transposedNote = activeTransposedNotes.remove(Pair(event.channel, event.note)) ?: event.note
                            MidiDispatcher.sendNoteOff(event.channel, transposedNote, 0, targetTimeNs)
                        }
                    }
                    is MidiEvent.NoteOff -> {
                        val transposedNote = activeTransposedNotes.remove(Pair(event.channel, event.note)) ?: event.note
                        MidiDispatcher.sendNoteOff(event.channel, transposedNote, event.velocity, targetTimeNs)
                    }
                    is MidiEvent.ControlChange -> {
                        val override = voiceManager?.channelRemap?.get(event.channel)
                        if (override != null && (event.controller == 0 || event.controller == 32)) {
                            // Ignora Bank Select do style
                        } else {
                            MidiDispatcher.sendControlChange(event.channel, event.controller, event.value, targetTimeNs)
                        }
                    }
                    is MidiEvent.ProgramChange -> {
                        val override = voiceManager?.channelRemap?.get(event.channel)
                        if (override != null) {
                            MidiDispatcher.sendControlChange(event.channel, 0, override.msb, targetTimeNs)
                            MidiDispatcher.sendControlChange(event.channel, 32, override.lsb, targetTimeNs)
                            com.example.MidiState.sendProgramChangeRaw(event.channel, override.pc, targetTimeNs)
                        } else {
                            // Envia APENAS o PC. O estilo já enviou o CC0 e CC32 corretamente nos eventos de Control Change.
                            com.example.MidiState.sendProgramChangeRaw(event.channel, event.program, targetTimeNs)
                        }
                        
                        // Restore live drum params if this is the drum channel
                        if (event.channel == 9 || event.channel == 8) {
                            com.example.MidiState.liveDrumParams.forEach { (key, params) ->
                                fun sendNRPN(msb: Int, paramVal: Int) {
                                    MidiDispatcher.sendControlChange(event.channel, 99, 1, targetTimeNs)
                                    MidiDispatcher.sendControlChange(event.channel, 98, msb, targetTimeNs)
                                    MidiDispatcher.sendControlChange(event.channel, 6, paramVal, targetTimeNs)
                                }
                                sendNRPN(24, params.pitchCoarse)
                                sendNRPN(26, params.pitchFine)
                                sendNRPN(28, params.level)
                                sendNRPN(29, params.pan)
                                sendNRPN(30, params.reverb)
                                sendNRPN(31, params.chorus)
                            }
                        }
                    }
                    is MidiEvent.PitchBend -> {
                        MidiDispatcher.sendPitchBend(event.channel, event.value and 0x7F, (event.value shr 7) and 0x7F, targetTimeNs)
                    }
                    is MidiEvent.SysEx -> {
                        com.example.MidiState.sendSysEx(event.data)
                    }
                    else -> {}
                }
            }
        }
    }
    
    private fun manageLoopAndSections(tick: Int) {
        val style = state.loadedStyle.get() ?: return
        
        // Verifica se houve pedido de troca imediata que é inválido (e previne travamento)
        var currentSection = state.currentSection.get()
        if (style.sections[currentSection] == null) {
            val fallback = style.sections.keys.firstOrNull() ?: return
            state.currentSection.set(fallback)
            currentSection = fallback
        }
        
        val sectionData = style.sections[currentSection] ?: return
        
        val sectionLengthTicks = sectionData.endTick - sectionData.startTick
        if (sectionLengthTicks <= 0L) return
        
        if (tick > 0 && tick % sectionLengthTicks == 0L) {
            var next = state.nextSection.getAndSet(null)
            
            val isEnding = currentSection == StyleSection.EndingA || currentSection == StyleSection.EndingB || currentSection == StyleSection.EndingC || currentSection == StyleSection.EndingD
            val isFill = currentSection.name.startsWith("FillIn") || currentSection == StyleSection.Break
            val isIntro = currentSection.name.startsWith("Intro")
            
            if (isEnding) {
                stop()
                return
            }
            
            if (next == null && isFill) {
                next = when(currentSection) {
                    StyleSection.FillInAA -> StyleSection.MainA
                    StyleSection.FillInAB -> StyleSection.MainB
                    StyleSection.FillInBA -> StyleSection.MainA
                    StyleSection.FillInBB -> StyleSection.MainB
                    StyleSection.FillInCC -> StyleSection.MainC
                    StyleSection.FillInCD -> StyleSection.MainD
                    StyleSection.FillInDD -> StyleSection.MainD
                    else -> StyleSection.MainA
                }
            }
            
            if (next == null && isIntro) {
                next = when(currentSection) {
                    StyleSection.IntroA -> StyleSection.MainA
                    StyleSection.IntroB -> StyleSection.MainB
                    StyleSection.IntroC -> StyleSection.MainC
                    StyleSection.IntroD -> StyleSection.MainD
                    else -> StyleSection.MainA
                }
            }

            if (next != null && style.sections[next] != null) {
                state.currentSection.set(next)
            }
            // Cut hanging notes at loop point
            activeTransposedNotes.forEach { (key, transposedNote) ->
                MidiDispatcher.sendNoteOff(key.first, transposedNote, 0, System.nanoTime())
            }
            activeTransposedNotes.clear()
            
            // Reset do tick para o loop da (nova ou mesma) seção
            state.resetTick() 
        }
    }
    
    private var pendingMetronomeOffTick = -1
    private var pendingMetronomeNote = -1

    private fun processMetronome(tick: Int, targetTimeNs: Long) {
        val isMetronomeActive = state.metronomeEnabled.get() || com.example.domain.metronome.MetronomeConfig.enabled

        // Turn off previous note when scheduled tick is reached (prevents acoustic envelope choke)
        if (pendingMetronomeNote != -1 && tick >= pendingMetronomeOffTick) {
            MidiDispatcher.sendNoteOff(com.example.domain.metronome.MetronomeConfig.channel, pendingMetronomeNote, 0, targetTimeNs)
            pendingMetronomeNote = -1
        }

        if (!isMetronomeActive) return

        val ticksPerBeat = (ppq * 4) / state.timeSigDenominator
        if (tick % ticksPerBeat == 0) {
            val isFirstBeat = (tick % (state.timeSigNumerator * ticksPerBeat)) == 0
            val note = if (isFirstBeat) com.example.domain.metronome.MetronomeConfig.downbeatNote else com.example.domain.metronome.MetronomeConfig.upbeatNote
            val vel = if (isFirstBeat) com.example.domain.metronome.MetronomeConfig.accentVelocity else com.example.domain.metronome.MetronomeConfig.normalVelocity
            val ch = com.example.domain.metronome.MetronomeConfig.channel

            // Guarantee volume and expression on metronome drum channel
            MidiDispatcher.sendControlChange(ch, 7, com.example.domain.metronome.MetronomeConfig.volume, targetTimeNs)
            MidiDispatcher.sendControlChange(ch, 11, 127, targetTimeNs)

            MidiDispatcher.sendNoteOn(ch, note, vel, targetTimeNs)

            pendingMetronomeNote = note
            // Turn off 1/4 of a beat later (e.g. 480 ticks at 1920 PPQ, ~62ms at 120 BPM)
            pendingMetronomeOffTick = tick + (ticksPerBeat / 4).coerceAtLeast(1)
        }
    }
}

sealed class ArrangerCommand {
    data class ChangeSection(val section: StyleSection) : ArrangerCommand()
    data class UpdateBpm(val bpm: Float) : ArrangerCommand()
    data class ToggleLeftHold(val enabled: Boolean) : ArrangerCommand()
    data class LoadStyle(val styFile: com.example.domain.models.StyFile) : ArrangerCommand()
}
