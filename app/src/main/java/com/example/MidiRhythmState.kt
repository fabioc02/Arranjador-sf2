package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.TrackState
import com.example.RhythmTrack
import com.example.RecordedNote
import com.example.RecordedCC

object MidiRhythmState {
    const val PPQ = 1920

    var isPlaying by mutableStateOf(false)
    var isPaused by mutableStateOf(false)
    var isRecording by mutableStateOf(false)
    var lastRecordingSnapshots = mutableMapOf<com.example.StyleSection, List<RhythmTrack>>()
    data class PlayingNote(val channel: Int, val note: Int, val absoluteOffTick: Long)
    val activePlayingNotes = java.util.concurrent.CopyOnWriteArrayList<PlayingNote>()
    val activeArrangerNotes = androidx.compose.runtime.mutableStateListOf<Int>()
    var bpm by mutableStateOf(120f)
    var timeSigNumerator by mutableStateOf(4)
    var timeSigDenominator by mutableStateOf(4)
    var currentSection by mutableStateOf(StyleSection.MAIN_A)
    var currentStyle by mutableStateOf<com.example.domain.models.StyFile?>(null)
    var showDspPanel by mutableStateOf(false)
    var showDrumSetup by mutableStateOf(false)
    var showStyleCreator by mutableStateOf(false)
    var sectionMeasures = androidx.compose.runtime.mutableStateMapOf<StyleSection, Int>().apply {
        StyleSection.values().forEach { put(it, 2) }
    }
    var sectionTracks = androidx.compose.runtime.mutableStateMapOf<StyleSection, List<RhythmTrack>>().apply {
        StyleSection.values().forEach { put(it, createDefaultTracks()) }
    }

    var measures: Int
        get() = sectionMeasures[currentSection] ?: 2
        set(value) { sectionMeasures[currentSection] = value }
        
    var quantizeResolution by mutableStateOf(4)
    @Volatile
    var currentTick: Int = 0
    var uiTick by mutableStateOf(0)
    var selectedTrackIndex by mutableStateOf(0)
    var showEventList by mutableStateOf(false)
    var showQuantizeDialog by mutableStateOf(false)
    var learningTrack by mutableStateOf<Int?>(null)
    var metronomeEnabled by mutableStateOf(true)
    var detectedChord by mutableStateOf<Chord?>(Chord(0, ChordType.Major))
    
    enum class EvalMode(val displayName: String) {
        CHORD("Acorde"),
        SCALE("Escala / C. Harmônico")
    }

    var evalMode by mutableStateOf(EvalMode.CHORD)
    var blockWrongNotes by mutableStateOf(false)
    var isLocalControlOff by mutableStateOf(false)

    var lastEvaluatedNote by mutableStateOf<Int?>(null)
    var lastEvaluatedStatus by mutableStateOf<Boolean?>(null) // true = CERTO, false = ERRADO
    var lastEvaluatedNoteName by mutableStateOf("")
    var lastEvaluatedReference by mutableStateOf("")

    fun evaluateNoteAgainstTrack(noteNumber: Int, track: RhythmTrack): Pair<Boolean, String> {
        val pitchClass = (noteNumber % 12 + 12) % 12
        val isBass = track.channel == 11 || track.name.contains("Bass", ignoreCase = true)
        val isDrum = track.isDrumTrack || track.channel in 9..10

        if (isDrum) {
            return Pair(true, "Bateria / Percussão (Livre)")
        }

        val refRoot = if (isBass) 0 else (track.sourceRoot?.rootKey ?: 0)
        val refType = track.sourceRoot?.type ?: ChordType.Major7
        val rootName = com.example.domain.chord.NoteUtils.NOTE_NAMES[refRoot]

        val allowedPitchClasses = if (evalMode == EvalMode.CHORD) {
            val intervals = when (refType) {
                ChordType.Major -> listOf(0, 4, 7)
                ChordType.Major7 -> listOf(0, 4, 7, 11)
                ChordType.Minor -> listOf(0, 3, 7)
                ChordType.Minor7 -> listOf(0, 3, 7, 10)
                ChordType.Dominant7 -> listOf(0, 4, 7, 10)
                ChordType.Diminished -> listOf(0, 3, 6)
                ChordType.Augmented -> listOf(0, 4, 8)
                ChordType.Sus4 -> listOf(0, 5, 7)
                else -> listOf(0, 4, 7)
            }
            val bassExtra = if (isBass) listOf(2, 9) else emptyList()
            (intervals + bassExtra).map { (refRoot + it) % 12 }.toSet()
        } else {
            val intervals = if (refType == ChordType.Minor || refType == ChordType.Minor7) {
                listOf(0, 2, 3, 5, 7, 8, 10) // Minor scale
            } else {
                listOf(0, 2, 4, 5, 7, 9, 11) // Major scale
            }
            intervals.map { (refRoot + it) % 12 }.toSet()
        }

        val refLabel = if (evalMode == EvalMode.CHORD) {
            "$rootName ${refType.name}" + (if (isBass) " (Bass Fixo C)" else "")
        } else {
            "Escala $rootName " + (if (refType == ChordType.Minor || refType == ChordType.Minor7) "Menor" else "Maior")
        }

        val isCorrect = allowedPitchClasses.contains(pitchClass)
        return Pair(isCorrect, refLabel)
    }

    enum class RecordInputSource(val displayName: String) {
        RIGHT_1("Right 1 (Ch 1)"),
        RIGHT_2("Right 2 (Ch 2)"),
        ALL_RIGHT("Right 1 + 2"),
        OMNI("All Channels (Omni)")
    }

    var recordInputSource by mutableStateOf(RecordInputSource.RIGHT_1)

    // 16 channels x 128 notes lookup table for real-time deterministic Note On / Note Off pairing
    private val activeRecordingNotes = arrayOfNulls<RecordedNote>(2048)
    // Pitch lookup table to prevent layered/duplicate Note Ons on the same track
    private val activePitchNotes = arrayOfNulls<RecordedNote>(128)

    init {
        MidiState.timestampedMidiListeners.add { status, data1, data2, timestampNs ->
            processIncomingMidi(status, data1, data2, timestampNs)
        }
    }

    fun isChannelAllowedForRecording(channel: Int): Boolean {
        val r1Ch = com.example.domain.models.VoiceEngine.CH_RIGHT1
        val r2Ch = com.example.domain.models.VoiceEngine.CH_RIGHT2
        return when (recordInputSource) {
            RecordInputSource.RIGHT_1 -> channel == r1Ch
            RecordInputSource.RIGHT_2 -> channel == r2Ch
            RecordInputSource.ALL_RIGHT -> channel == r1Ch || channel == r2Ch
            RecordInputSource.OMNI -> true
        }
    }

    fun getTicksPerBeat(): Int = (PPQ * 4) / timeSigDenominator
    fun getTicksPerMeasure(): Int = timeSigNumerator * getTicksPerBeat()
    fun getTotalTicks(): Int = maxOf(1, measures * getTicksPerMeasure())

    fun clearActiveNotes() {
        for (i in 0 until 2048) {
            activeRecordingNotes[i] = null
        }
        for (i in 0 until 128) {
            activePitchNotes[i] = null
        }
    }

    fun processIncomingMidi(status: Int, data1: Int, data2: Int, timestampNs: Long) {
        if (MidiState.isArrangerMode) return
        val command = status and 0xF0
        val channel = status and 0x0F
        val isNoteOn = command == 0x90 && data2 > 0
        val isNoteOff = command == 0x80 || (command == 0x90 && data2 == 0)

        // Strict channel filtering during recording
        if (isRecording && !isChannelAllowedForRecording(channel)) {
            return
        }

        val track = tracks.getOrNull(selectedTrackIndex) ?: return

        if (isNoteOn) {
            val noteName = com.example.domain.chord.NoteUtils.NOTE_NAMES[data1 % 12] + (data1 / 12 - 1)
            val (isCorrect, refLabel) = evaluateNoteAgainstTrack(data1, track)
            lastEvaluatedNote = data1
            lastEvaluatedStatus = isCorrect
            lastEvaluatedNoteName = noteName
            lastEvaluatedReference = refLabel

            if (isRecording && blockWrongNotes && !isCorrect) {
                return // Block recording when user has enabled 'blockWrongNotes'
            }
        }

        if (isNoteOn && isRecording) {
            val totalTicks = getTotalTicks()
            val tick = if (totalTicks > 0) (currentTick % totalTicks) else 0
            val pitch = data1 and 0x7F

            // ANTI-DUPLICATE GUARD: If a note of the exact same pitch was triggered within 35ms or at the same tick, ignore duplicate
            val existingPitch = activePitchNotes[pitch]
            if (existingPitch != null) {
                val timeDiffNs = timestampNs - existingPitch.timestampNs
                if (existingPitch.tick == tick || (timeDiffNs in 1..35_000_000L)) {
                    // Suppress duplicate Note On (e.g. from layered Right 1 + Right 2)
                    return
                }
            }

            val note = RecordedNote(
                tick = tick,
                note = data1,
                velocity = data2,
                durationTicks = 1,
                timestampMs = timestampNs / 1_000_000L,
                originalTick = tick,
                timestampNs = timestampNs
            )
            val noteIndex = (channel * 128) + pitch
            if (noteIndex in 0 until 2048) {
                val previousNote = activeRecordingNotes[noteIndex]
                if (previousNote != null) {
                    var dur = currentTick - previousNote.tick
                    if (dur < 0) dur += totalTicks
                    if (dur == 0) dur = 1
                    previousNote.durationTicks = dur
                }
                activeRecordingNotes[noteIndex] = note
            }
            activePitchNotes[pitch] = note
            track.addNote(note)
        } else if (isNoteOff) {
            val pitch = data1 and 0x7F
            activePitchNotes[pitch] = null
            val noteIndex = (channel * 128) + pitch
            if (noteIndex in 0 until 2048) {
                val activeNote = activeRecordingNotes[noteIndex]
                if (activeNote != null) {
                    activeRecordingNotes[noteIndex] = null
                    val totalTicks = getTotalTicks()
                    var duration = currentTick - activeNote.tick
                    if (duration < 0) duration += totalTicks
                    if (duration == 0) duration = 1
                    activeNote.durationTicks = duration
                    track.notifyNoteModified()
                }
            }
        } else if (isRecording) {
            val totalTicks = getTotalTicks()
            val tick = if (totalTicks > 0) (currentTick % totalTicks) else 0
            if (command == 0xB0) {
                // Ignore bank selects (0, 32) and channel mode / reset messages (120..127) during performance recording
                if (data1 !in listOf(0, 32, 120, 121, 122, 123, 124, 125, 126, 127)) {
                    track.addCC(RecordedCC(tick, data1, data2))
                }
            } else if (command == 0xE0) {
                track.addCC(RecordedCC(tick, 129, data1))
                track.addCC(RecordedCC(tick, 130, data2))
            } else if (command == 0xD0) {
                track.addCC(RecordedCC(tick, 128, data1))
            }
        }
    }

    fun quantizeTick(originalTick: Int, resolution: Int, totalTicks: Int): Int {
        if (resolution <= 0 || totalTicks <= 0) return originalTick
        val qTicks = (PPQ * 4) / resolution
        if (qTicks <= 0) return originalTick
        val rounded = ((originalTick + (qTicks / 2)) / qTicks) * qTicks
        val result = rounded % totalTicks
        return if (result < 0) result + totalTicks else result
    }

    fun quantizeTrack(trackIndex: Int = selectedTrackIndex) {
        val track = tracks.getOrNull(trackIndex) ?: return
        val total = getTotalTicks()
        val res = quantizeResolution
        val currentNotes = track.notes
        val quantized = currentNotes.map { note ->
            note.copy(tick = quantizeTick(note.originalTick, res, total))
        }
        track.notes = quantized
    }

    fun unquantizeTrack(trackIndex: Int = selectedTrackIndex) {
        val track = tracks.getOrNull(trackIndex) ?: return
        val currentNotes = track.notes
        val unquantized = currentNotes.map { note ->
            note.copy(tick = note.originalTick)
        }
        track.notes = unquantized
    }

    var tracks: List<RhythmTrack>
        get() = sectionTracks[currentSection] ?: emptyList()
        set(value) { sectionTracks[currentSection] = value }

    private var lastVoiceAppliedKey: String? = null

    fun selectTrack(index: Int, sendMidiVoice: Boolean = true) {
        if (index in tracks.indices) {
            selectedTrackIndex = index
            val track = tracks[index]
            com.example.domain.midi.GlobalMidiRouter.overrideRightHandChannel = track.channel - 1
            if (sendMidiVoice) {
                applyTrackVoice(track)
            }
        }
    }

    fun applyTrackVoice(track: RhythmTrack, force: Boolean = false) {
        val ch = track.channel - 1
        if (ch in 0..15) {
            val sf = track.soundFontVoice
            if (sf != null) {
                MidiState.soundFontSynth.setChannelVoice(ch, sf)
                MidiState.voiceManager?.setSoundFontMapping(ch, sf)
                return
            }
            val key = "$ch:${track.bankMsb}:${track.bankLsb}:${track.program}:${track.isDrumTrack}"
            if (!force && key == lastVoiceAppliedKey) {
                return
            }
            lastVoiceAppliedKey = key
            val setupNum = if (track.isDrumTrack) {
                if (track.channel == 9) 0x02.toByte() else 0x01.toByte()
            } else {
                0x00.toByte()
            }
            MidiState.sendSysEx(byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, 0x08, ch.toByte(), 0x07, setupNum, 0xF7.toByte()))
            MidiState.sendProgramChange(ch, track.bankMsb, track.bankLsb, track.program)
        }
    }

    fun resetLastVoiceAppliedKey() {
        lastVoiceAppliedKey = null
    }

    fun createDefaultTracks(): List<RhythmTrack> = listOf(
        RhythmTrack("Rhythm 1", 9, 127, 0, 0, true),
        RhythmTrack("Rhythm 2", 10, 127, 0, 0, true),
        RhythmTrack("Bass", 11, 0, 112, 33, false),
        RhythmTrack("Chord 1", 12, 0, 112, 0, false),
        RhythmTrack("Chord 2", 13, 0, 112, 27, false),
        RhythmTrack("Pad", 14, 0, 112, 88, false),
        RhythmTrack("Phrase 1", 15, 0, 112, 61, false),
        RhythmTrack("Phrase 2", 16, 0, 112, 48, false)
    )    
    fun executeMidiAction(action: com.example.domain.midi_core.MidiAction) {
        when (action) {
            com.example.domain.midi_core.MidiAction.START -> isPlaying = true
            com.example.domain.midi_core.MidiAction.STOP -> isPlaying = false
            com.example.domain.midi_core.MidiAction.MAIN_A -> currentSection = com.example.StyleSection.MAIN_A
            com.example.domain.midi_core.MidiAction.MAIN_B -> currentSection = com.example.StyleSection.MAIN_B
            com.example.domain.midi_core.MidiAction.MAIN_C -> currentSection = com.example.StyleSection.MAIN_C
            com.example.domain.midi_core.MidiAction.MAIN_D -> currentSection = com.example.StyleSection.MAIN_D
            com.example.domain.midi_core.MidiAction.FILL_A -> currentSection = com.example.StyleSection.FILL_A
            com.example.domain.midi_core.MidiAction.FILL_B -> currentSection = com.example.StyleSection.FILL_B
            com.example.domain.midi_core.MidiAction.FILL_C -> currentSection = com.example.StyleSection.FILL_C
            com.example.domain.midi_core.MidiAction.FILL_D -> currentSection = com.example.StyleSection.FILL_D
            com.example.domain.midi_core.MidiAction.BREAK -> currentSection = com.example.StyleSection.INTRO_A // Fallback for break if not mapped
            com.example.domain.midi_core.MidiAction.INTRO_A -> currentSection = com.example.StyleSection.INTRO_A
            com.example.domain.midi_core.MidiAction.ENDING_A -> currentSection = com.example.StyleSection.ENDING_A
        }
    }
}
