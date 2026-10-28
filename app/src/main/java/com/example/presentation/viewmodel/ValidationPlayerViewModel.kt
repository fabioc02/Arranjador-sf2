package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.parser.StyParser
import com.example.domain.audio.*
import com.example.domain.chord.ChordEngine
import com.example.domain.midi.*
import com.example.domain.models.*
import com.example.domain.playback.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch




private fun noteToName(note: Int): String {
    val notes = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    if (note < 0 || note > 127) return "---"
    val octave = (note / 12) - 2
    val name = notes[note % 12]
    return "$name$octave"
}

class ValidationPlayerViewModel : ViewModel() {

    data class TranspositionLog(
        val track: String,
        val channel: Int,
        val originalNoteName: String,
        val transposedNoteName: String,
        val chordName: String,
        val ntr: String,
        val ntt: String
    )

    
    private val audioEngine = object {
        fun start() {}
        fun stop() {}
        val soundFontManager = object {
            fun loadSoundFont(path: String) {}
        }
    }
    
    val parser = StyParser()
    val chordEngine = ChordEngine()
    var playbackEngine: StylePlaybackEngine? = null
    
    val midiMonitor = MidiMonitor()
    
    private val _styFile = MutableStateFlow<StyFile?>(null)
    val styFile: StateFlow<StyFile?> = _styFile
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentSection = MutableStateFlow(com.example.domain.models.StyleSection.MainA)
    val currentSection: StateFlow<com.example.domain.models.StyleSection> = _currentSection
    
    private val _currentChord = MutableStateFlow(com.example.domain.models.Chord(0, com.example.domain.models.ChordType.Major))
    val currentChord: StateFlow<com.example.domain.models.Chord> = _currentChord
    
    private val _bpm = MutableStateFlow(120f)
    val bpm: StateFlow<Float> = _bpm
    
    private val _activeLeftHandNotes = MutableStateFlow<List<Int>>(emptyList())
    val activeLeftHandNotes: StateFlow<List<Int>> = _activeLeftHandNotes
    
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs
    
    
    private val _transpositionLogs = MutableStateFlow<List<TranspositionLog>>(emptyList())
    val transpositionLogs: StateFlow<List<TranspositionLog>> = _transpositionLogs
    
    private fun logTransposition(log: TranspositionLog) {
        val current = _transpositionLogs.value.toMutableList()
        current.add(0, log)
        if (current.size > 20) current.removeAt(current.size - 1)
        _transpositionLogs.value = current
    }

    private var midiScheduler: RealTimeMidiScheduler? = null
    
    init {
        audioEngine.start()
        log("Audio Engine Started")
    }
    
    fun loadSoundFont(path: String) {
        audioEngine.soundFontManager.loadSoundFont(path)
        log("Loaded SoundFont: $path")
    }
    
    fun loadStyle(data: ByteArray, filename: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                
                val sty = parser.parseBytes(data, filename)
                _styFile.value = sty
                log("Loaded STY: $filename")
                    
                    playbackEngine = StylePlaybackEngine(
                        style = _styFile.value!!,
                        chordEngine = chordEngine
                    )
                    playbackEngine?.currentChord = _currentChord.value
                    log("Playback Engine initialized")
                
            } catch (e: Exception) {
                log("Error loading STY: ${e.message}")
            }
        }
    }
    
    fun play() {
        if (playbackEngine == null) return
        if (_isPlaying.value) return
        
        _isPlaying.value = true
        log("Playback started")
        
        midiScheduler?.stop()
        
        val engine = playbackEngine!!
        midiScheduler = RealTimeMidiScheduler(
            ppq = engine.clock.ppqn,
            getBpm = { _bpm.value },
            mode = com.example.MidiState.midiSchedulerMode,
            getNextEventTick = { playbackEngine?.getNextEventTick() },
            onTick = { currentTick, targetTimeNs ->
                val (events, _) = engine.processTick(currentTick)
                
                for (event in events) {
                    midiMonitor.logGenerated(event)
                    if (event.eventType == "NoteOn" && event.velocity > 0) {
                        logTransposition(
                            TranspositionLog(
                                track = event.trackName,
                                channel = event.channel,
                                originalNoteName = noteToName(event.originalNote),
                                transposedNoteName = noteToName(event.note),
                                chordName = _currentChord.value.displayName,
                                ntr = event.ntrApplied,
                                ntt = event.nttApplied
                            )
                        )
                    }

                    when (event.eventType) {
                        "NoteOn" -> com.example.MidiState.sendNoteOn(event.channel, event.note, event.velocity, targetTimeNs)
                        "NoteOff" -> com.example.MidiState.sendNoteOff(event.channel, event.note, event.velocity, targetTimeNs)
                        "CC" -> com.example.MidiState.sendControlChange(event.channel, event.ccController, event.ccValue, targetTimeNs)
                        "ProgramChange" -> com.example.MidiState.sendProgramChangeRaw(event.channel, event.program, targetTimeNs)
                        "PitchBend" -> com.example.MidiState.sendPitchBend(event.channel, 0, event.pitchBendValue, targetTimeNs) // Very basic
                        "SysEx" -> event.sysExData?.let { com.example.MidiState.sendSysEx(it, targetTimeNs) }
                    }
                }
                
                if (_currentSection.value != engine.sectionPlayer.currentSection) {
                    _currentSection.value = engine.sectionPlayer.currentSection
                    log("Transition to: ${engine.sectionPlayer.currentSection.name}")
                }
                
                if (engine.isFinished) {
                    stop()
                }
            }
        )
        midiScheduler?.start()
    }
    
    fun stop() {
        _isPlaying.value = false
        midiScheduler?.stop()
        midiScheduler = null
        
        playbackEngine?.reset()
        
        com.example.MidiState.panicMidi()
        log("Playback stopped")
    }
    
    private val heldNotes = mutableSetOf<Int>()
    fun handleMidiInput(status: Int, data1: Int, data2: Int) {
        val command = status and 0xF0
        val channel = status and 0x0F
        
        val physicalNote = data1 - com.example.MidiState.keyboardTranspose
        val isLeftHand = channel == 2 || channel == 3 || (channel <= 1 && physicalNote <= com.example.MidiState.splitPoint)

        val isNoteOn = command == 0x90 && data2 > 0
        val isNoteOff = command == 0x80 || (command == 0x90 && data2 == 0)
        
        if (isNoteOff) {
            if (heldNotes.contains(data1)) {
                heldNotes.remove(data1)
                _activeLeftHandNotes.value = heldNotes.sorted()
            }
        } else if (isNoteOn && isLeftHand) {
            heldNotes.add(data1)
            _activeLeftHandNotes.value = heldNotes.sorted()
            val detected = chordEngine.detectChord(heldNotes.sorted())
            if (detected != null && detected != _currentChord.value) {
                setChord(detected)
            }
        }

    }
    
    fun setSection(section: com.example.domain.models.StyleSection) {
        val sty = _styFile.value ?: return
        if (sty.hasSection(section)) {
            playbackEngine?.playSection(section)
            _currentSection.value = section
            log("Jumping to section: ${section.name}")
        }
    }
    
    fun setChord(chord: com.example.domain.models.Chord) {
        _currentChord.value = chord
        playbackEngine?.currentChord = chord
        log("Chord changed to: ${chord.rootKey} ${chord.type.name}")
    }
    
    fun setChannelInstrument(channel: Int, bankMsb: Int, bankLsb: Int, program: Int) {
        com.example.MidiState.sendProgramChange(channel, bankMsb, bankLsb, program)
        log("Ch ${channel + 1} changed to PC $program")
    }
    
    fun setBpm(bpm: Float) {
        _bpm.value = bpm
        // midiScheduler pulls bpm dynamically
        log("BPM changed to: $bpm")
    }
    
    private fun log(message: String) {
        val current = _logs.value.toMutableList()
        current.add(0, "${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())} - $message")
        if (current.size > 50) {
            current.removeAt(current.size - 1)
        }
        _logs.value = current
    }
    
    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
