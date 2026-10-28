package com.example.domain.arranger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.models.StyFile
import com.example.domain.models.StyleSection

import com.example.domain.midi_core.MidiMappingManager
import com.example.domain.arranger.VoiceManager

import com.example.domain.midi_core.MidiAction
import com.example.MidiState

import com.example.domain.models.Chord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ArrangerViewModel(application: Application) : AndroidViewModel(application) {
    val state = ArrangerCoreState()
    
    val engine = ArrangerEngine(state, ppq = 1920) // Using default standard PPQ

    
    val midiMappingManager = MidiMappingManager(application)
    val voiceManager = VoiceManager(application)



    private val _currentTick = MutableStateFlow(0)
    val currentTick: StateFlow<Int> = _currentTick
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentSection = MutableStateFlow(StyleSection.MainA)
    val currentSection: StateFlow<StyleSection> = _currentSection

    private val _currentChord = MutableStateFlow<Chord?>(null)
    val currentChord: StateFlow<Chord?> = _currentChord
    
    private val _bpm = MutableStateFlow(120f)
    val bpm: StateFlow<Float> = _bpm

    
    
    private val _metronomeEnabled = MutableStateFlow(false)
    val metronomeEnabled: StateFlow<Boolean> = _metronomeEnabled

    init {
        engine.voiceManager = voiceManager
        com.example.MidiState.voiceManager = voiceManager
        com.example.domain.metronome.MetronomeConfig.init(application)
        state.metronomeEnabled.set(com.example.domain.metronome.MetronomeConfig.enabled)

        // Intercept MIDI messages for mapping
        val rawMidiListener: (ByteArray) -> Unit = { msg ->
            val isArranger = MidiState.currentTab == "arranger"
            val isCreator = MidiState.currentTab == "creator"
            val isMidiSettings = MidiState.currentTab == "midi"
            
            if (isArranger || isCreator || isMidiSettings) {
                val action = midiMappingManager.onRawMidiMessage(msg)
                if (action != null) {
                    if (isArranger) {
                        executeMidiAction(action)
                    } else if (isCreator) {
                        com.example.MidiRhythmState.executeMidiAction(action)
                    }
                }
            }
        }
        MidiState.rawMidiListeners.add(rawMidiListener)
        
        MidiState.midiListeners.add { status, data1, data2 ->
            if (MidiState.currentTab == "arranger") {
                val command = status and 0xF0
                val channel = status and 0x0F
                val isNoteOn = command == 0x90 && data2 > 0
                val isNoteOff = command == 0x80 || (command == 0x90 && data2 == 0)
                
                if (isNoteOn || isNoteOff) {
                    val physicalNote = data1 - MidiState.keyboardTranspose
                    val isLeftHand = channel == 2 || channel == 3 || (channel <= 1 && physicalNote <= MidiState.splitPoint)
                    if (isLeftHand) {
                        if (isNoteOn) {
                            engine.chordDetector.processNoteOn(data1, data2, channel)
                        } else {
                            engine.chordDetector.processNoteOff(data1, channel)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                _currentTick.value = state.currentTick.get()
                _isPlaying.value = state.isPlaying.get()
                _currentSection.value = state.currentSection.get()
                _currentChord.value = state.currentChord.get()
                _bpm.value = state.bpm
                _metronomeEnabled.value = state.metronomeEnabled.get() || com.example.domain.metronome.MetronomeConfig.enabled
                kotlinx.coroutines.delay(33) // ~30 fps UI updates
            }
        }
    }

    fun toggleMetronome() {
        val current = state.metronomeEnabled.get() || com.example.domain.metronome.MetronomeConfig.enabled
        val newVal = !current
        state.metronomeEnabled.set(newVal)
        com.example.domain.metronome.MetronomeConfig.enabled = newVal
        com.example.domain.metronome.MetronomeConfig.save(getApplication())
        _metronomeEnabled.value = newVal
    }

    fun setMetronomeEnabled(enabled: Boolean) {
        state.metronomeEnabled.set(enabled)
        com.example.domain.metronome.MetronomeConfig.enabled = enabled
        com.example.domain.metronome.MetronomeConfig.save(getApplication())
        _metronomeEnabled.value = enabled
    }
    

    private fun executeMidiAction(action: MidiAction) {
        when (action) {
            MidiAction.START -> play()
            MidiAction.STOP -> stop()
            MidiAction.MAIN_A -> setSection(StyleSection.MainA)
            MidiAction.MAIN_B -> setSection(StyleSection.MainB)
            MidiAction.MAIN_C -> setSection(StyleSection.MainC)
            MidiAction.MAIN_D -> setSection(StyleSection.MainD)
            MidiAction.FILL_A -> setSection(StyleSection.FillInAA)
            MidiAction.FILL_B -> setSection(StyleSection.FillInBB)
            MidiAction.FILL_C -> setSection(StyleSection.FillInCC)
            MidiAction.FILL_D -> setSection(StyleSection.FillInDD)
            MidiAction.BREAK -> setSection(StyleSection.Break)
            MidiAction.INTRO_A -> setSection(StyleSection.IntroA)
            MidiAction.ENDING_A -> setSection(StyleSection.EndingA)
        }
    }

    fun play() {
        engine.start()
    }
    
    fun stop() {
        engine.stop()
    }
    
    fun setBpm(newBpm: Float) {
        engine.sendCommand(ArrangerCommand.UpdateBpm(newBpm))
    }
    
    fun setSection(section: StyleSection) {
        engine.sendCommand(ArrangerCommand.ChangeSection(section))
    }
    
    fun loadStyle(styFile: StyFile) {
        engine.sendCommand(ArrangerCommand.LoadStyle(styFile))
    }
    
    fun autoFill() {
        val current = state.currentSection.get()
        val fillSection = when (current) {
            StyleSection.MainA -> StyleSection.FillInAA
            StyleSection.MainB -> StyleSection.FillInBB
            StyleSection.MainC -> StyleSection.FillInCC
            StyleSection.MainD -> StyleSection.FillInDD
            else -> StyleSection.FillInAA
        }
        val style = state.loadedStyle.get()
        if (style != null && style.hasSection(fillSection)) {
            engine.sendCommand(ArrangerCommand.ChangeSection(fillSection))
        }
    }
    
    fun pressLeftNote(note: Int) {
        engine.chordDetector.processNoteOn(note, 100)
    }
    
    fun releaseLeftNote(note: Int) {
        engine.chordDetector.processNoteOff(note)
    }
    
    fun setLeftHold(enabled: Boolean) {
        engine.sendCommand(ArrangerCommand.ToggleLeftHold(enabled))
    }
}
