package com.example


import androidx.compose.foundation.gestures.detectDragGestures

import androidx.compose.foundation.gestures.detectDragGestures



import androidx.compose.foundation.gestures.detectDragGestures



import androidx.compose.ui.input.pointer.pointerInput






import androidx.compose.material.icons.Icons



import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import com.example.ui.arranger.SoundFontPickerModal



import androidx.compose.material3.Icon



import androidx.compose.ui.draw.clip




import android.content.Context



import androidx.compose.foundation.Canvas



import androidx.compose.foundation.background



import androidx.compose.foundation.border



import androidx.compose.foundation.clickable



import androidx.compose.foundation.layout.*



import androidx.compose.foundation.lazy.LazyColumn



import androidx.compose.foundation.lazy.items



import androidx.compose.foundation.rememberScrollState



import androidx.compose.foundation.verticalScroll



import androidx.compose.foundation.horizontalScroll



import androidx.compose.material3.*



import androidx.compose.runtime.*



import androidx.compose.ui.Alignment



import androidx.compose.ui.Modifier



import androidx.compose.ui.graphics.Color



import androidx.compose.ui.platform.LocalContext



import androidx.compose.ui.text.font.FontWeight



import androidx.compose.ui.unit.dp



import androidx.compose.ui.unit.sp

import com.squareup.moshi.Moshi

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.delay

import kotlinx.coroutines.launch

import java.io.File



import androidx.activity.compose.rememberLauncherForActivityResult



import androidx.activity.result.contract.ActivityResultContracts


import android.net.Uri

import java.io.InputStream

import java.io.OutputStream

import java.io.FileOutputStream

import androidx.compose.foundation.shape.RoundedCornerShape
import java.io.FileInputStream


data class RecordedCC(
    val tick: Int,
    val cc: Int,
    var value: Int
)


data class RecordedNote(
    var tick: Int,
    val note: Int,
    val velocity: Int,
    var durationTicks: Int = 1,
    val timestampMs: Long = System.currentTimeMillis(),
    val originalTick: Int = tick,
    val timestampNs: Long = 0L
)


data class DrumKeyParams(
    var pitchCoarse: Int = 64,
    var pitchFine: Int = 64,
    var level: Int = 127,
    var pan: Int = 64,
    var reverb: Int = 0,
    var chorus: Int = 0
)

class RhythmTrack(
    var name: String, 
    channel: Int,
    defaultBankMsb: Int = 0,
    defaultBankLsb: Int = 112,
    defaultProgram: Int = 0,
    defaultIsDrumTrack: Boolean = false
) {
    var isDrumTrack by mutableStateOf(defaultIsDrumTrack)
    var soundFontVoice by mutableStateOf<com.example.domain.soundfont.model.SoundFontVoice?>(null)
    private val _notes = mutableListOf<RecordedNote>()
    private val _ccEvents = mutableListOf<RecordedCC>()
    
    // O(1) tick indices for lightning-fast real-time audio playback without allocations
    private val notesByTick = java.util.concurrent.ConcurrentHashMap<Int, MutableList<RecordedNote>>()
    private val ccByTick = java.util.concurrent.ConcurrentHashMap<Int, MutableList<RecordedCC>>()

    var notesVersion by mutableStateOf(0)
    var ccVersion by mutableStateOf(0)

    var notes: List<RecordedNote>
        get() {
            notesVersion
            synchronized(_notes) {
                return _notes.toList()
            }
        }
        set(value) {
            synchronized(_notes) {
                _notes.clear()
                _notes.addAll(value)
                rebuildNoteIndex()
            }
            notesVersion++
        }

    var ccEvents: List<RecordedCC>
        get() {
            ccVersion
            synchronized(_ccEvents) {
                return _ccEvents.toList()
            }
        }
        set(value) {
            synchronized(_ccEvents) {
                _ccEvents.clear()
                _ccEvents.addAll(value)
                rebuildCCIndex()
            }
            ccVersion++
        }

    fun getNotesAt(tick: Int): List<RecordedNote>? = notesByTick[tick]
    fun getCCsAt(tick: Int): List<RecordedCC>? = ccByTick[tick]

    private fun rebuildNoteIndex() {
        notesByTick.clear()
        for (n in _notes) {
            var list = notesByTick[n.tick]
            if (list == null) {
                list = mutableListOf()
                notesByTick[n.tick] = list
            }
            list.add(n)
        }
    }

    private fun rebuildCCIndex() {
        ccByTick.clear()
        for (c in _ccEvents) {
            var list = ccByTick[c.tick]
            if (list == null) {
                list = mutableListOf()
                ccByTick[c.tick] = list
            }
            list.add(c)
        }
    }

    fun addNote(note: RecordedNote) {
        synchronized(_notes) {
            // Overdub/Replace rule: Remove overlapping note of same pitch within 5 ticks
            val iter = _notes.iterator()
            while (iter.hasNext()) {
                val n = iter.next()
                if (n.note == note.note && kotlin.math.abs(n.tick - note.tick) <= 5) {
                    iter.remove()
                    break
                }
            }
            _notes.add(note)
            rebuildNoteIndex()
        }
        notesVersion++
    }

    fun notifyNoteModified() {
        synchronized(_notes) {
            rebuildNoteIndex()
        }
        notesVersion++
    }

    fun addCC(cc: RecordedCC) {
        synchronized(_ccEvents) {
            // Deduplicate: If same CC type exists at same tick, update value instead of appending endlessly
            for (existing in _ccEvents) {
                if (existing.tick == cc.tick && existing.cc == cc.cc) {
                    existing.value = cc.value
                    return
                }
            }
            _ccEvents.add(cc)
            rebuildCCIndex()
        }
        ccVersion++
    }

    var channel by mutableStateOf(channel)
    var volume by mutableStateOf(100)
    var pan by mutableStateOf(64)
    var reverb by mutableStateOf(40)
    var chorus by mutableStateOf(0)
    var dsp by mutableStateOf(0)
    var isMuted by mutableStateOf(false)
    var sourceRoot by mutableStateOf<com.example.domain.models.Chord?>(null)
    
    var bankMsb by mutableStateOf(defaultBankMsb)
    var bankLsb by mutableStateOf(defaultBankLsb)
    var program by mutableStateOf(defaultProgram)
    
    var isLearning by mutableStateOf(false)
    val drumParams = androidx.compose.runtime.mutableStateMapOf<Int, DrumKeyParams>()
    
    fun toState(): TrackState {
        synchronized(_notes) {
            synchronized(_ccEvents) {
                return TrackState(name, channel, bankMsb, bankLsb, program, volume, isMuted, isDrumTrack, _notes.toList(), _ccEvents.toList(), pan, reverb, chorus, dsp, sourceRoot, drumParams.toMap(), soundFontVoice)
            }
        }
    }

    fun fromState(state: TrackState) {
        name = state.name
        channel = state.channel
        bankMsb = state.bankMsb
        bankLsb = state.bankLsb
        program = state.program
        volume = state.volume
        isMuted = state.isMuted
        isDrumTrack = state.isDrumTrack
        soundFontVoice = state.soundFontVoice
        notes = state.notes
        ccEvents = state.ccEvents
        pan = state.pan
        reverb = state.reverb
        chorus = state.chorus
        dsp = state.dsp
        sourceRoot = state.sourceRoot
        drumParams.clear()
        drumParams.putAll(state.drumParams)
    }
}


data class TrackState(
    val name: String, val channel: Int, val bankMsb: Int, val bankLsb: Int, val program: Int,
    val volume: Int, val isMuted: Boolean, val isDrumTrack: Boolean, val notes: List<RecordedNote>,
    val ccEvents: List<RecordedCC> = emptyList(), val pan: Int = 64, val reverb: Int = 40, val chorus: Int = 0,
    val dsp: Int = 0, val sourceRoot: com.example.domain.models.Chord? = null,
    val drumParams: Map<Int, DrumKeyParams> = emptyMap(),
    val soundFontVoice: com.example.domain.soundfont.model.SoundFontVoice? = null
)


data class RhythmState(
    val bpm: Float,
    val timeSigNumerator: Int,
    val timeSigDenominator: Int,
    val measures: Int,
    val tracks: List<TrackState>
)

@Composable
fun MidiRhythmCreatorDialog(visible: Boolean, onDismiss: () -> Unit) {
    var showExtMidi by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showSoundFontHelpDialog by remember { mutableStateOf(false) }
    var showSaveRhythmDialog by remember { mutableStateOf(false) }
    var showLoadRhythmDialog by remember { mutableStateOf(false) }
    var rhythmNameInput by remember { mutableStateOf("Meu Ritmo") }
    var savedRhythmsList by remember { mutableStateOf(listOf<com.example.domain.rhythm.SavedRhythmHeader>()) }

    val context = androidx.compose.ui.platform.LocalContext.current
    if (!visible) return
    if (MidiRhythmState.showDrumSetup) {
        com.example.ui.drumsetup.DrumSetupDialog(onDismiss = { MidiRhythmState.showDrumSetup = false })
    }
    if (showSoundFontHelpDialog) {
        com.example.ui.arranger.SoundFontHelpDialog(
            onDismiss = { showSoundFontHelpDialog = false },
            onSoundFontsUpdated = { }
        )
    }

    if (showSaveRhythmDialog) {
        AlertDialog(
            onDismissRequest = { showSaveRhythmDialog = false },
            title = { Text("Salvar Ritmo no App") },
            text = {
                Column {
                    Text("O ritmo será salvo com todos os SoundFonts atribuídos às tracks, notas e parâmetros.", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rhythmNameInput,
                        onValueChange = { rhythmNameInput = it },
                        label = { Text("Nome do Ritmo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val sectionStateMap = MidiRhythmState.sectionTracks.mapValues { (_, trkList) ->
                        trkList.map { it.toState() }
                    }
                    com.example.domain.rhythm.RhythmProjectManager.saveRhythm(
                        context = context,
                        name = rhythmNameInput.ifBlank { "Ritmo" },
                        bpm = MidiRhythmState.bpm,
                        measures = MidiRhythmState.measures,
                        timeSigNum = MidiRhythmState.timeSigNumerator,
                        timeSigDen = MidiRhythmState.timeSigDenominator,
                        sectionTracks = sectionStateMap
                    )
                    showSaveRhythmDialog = false
                }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveRhythmDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showLoadRhythmDialog) {
        AlertDialog(
            onDismissRequest = { showLoadRhythmDialog = false },
            title = { Text("Carregar Ritmo Salvo") },
            text = {
                if (savedRhythmsList.isEmpty()) {
                    Text("Nenhum ritmo salvo no app ainda. Crie e salve um ritmo primeiro!", fontSize = 12.sp, color = Color.Gray)
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(savedRhythmsList.size) { idx ->
                            val header = savedRhythmsList[idx]
                            Surface(
                                onClick = {
                                    val loaded = com.example.domain.rhythm.RhythmProjectManager.loadRhythm(context, header.id)
                                    if (loaded != null) {
                                        MidiRhythmState.bpm = loaded.bpm
                                        MidiRhythmState.measures = loaded.measures
                                        MidiRhythmState.timeSigNumerator = loaded.timeSigNum
                                        MidiRhythmState.timeSigDenominator = loaded.timeSigDen

                                        loaded.sectionTracks.forEach { (secName, trackStates) ->
                                            val sec = com.example.StyleSection.values().find { it.name == secName }
                                            if (sec != null) {
                                                val restoredList = trackStates.map { state ->
                                                    RhythmTrack(state.name, state.channel, state.bankMsb, state.bankLsb, state.program, state.isDrumTrack).apply {
                                                        fromState(state)
                                                    }
                                                }
                                                MidiRhythmState.sectionTracks[sec] = restoredList
                                            }
                                        }

                                        // Preload active rhythm soundfonts + 3 live performance parts (R1, R2, L)
                                        val rhythmVoices = MidiRhythmState.tracks.mapNotNull { it.soundFontVoice }
                                        val liveVoices = listOf(
                                            com.example.domain.models.VoiceEngine.channels[com.example.domain.models.VoiceEngine.CH_RIGHT1].soundFontVoice,
                                            com.example.domain.models.VoiceEngine.channels[com.example.domain.models.VoiceEngine.CH_RIGHT2].soundFontVoice,
                                            com.example.domain.models.VoiceEngine.channels[com.example.domain.models.VoiceEngine.CH_LEFT].soundFontVoice
                                        ).filterNotNull()
                                        MidiState.soundFontRepository.preloadActiveRhythmAndParts(rhythmVoices, liveVoices)

                                        MidiRhythmState.tracks.forEach { track ->
                                            MidiRhythmState.applyTrackVoice(track, force = true)
                                        }
                                    }
                                    showLoadRhythmDialog = false
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(header.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${header.bpm.toInt()} BPM • ${header.measures} Comp • ${header.usedSoundFontsCount} SFs", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = {
                                        com.example.domain.rhythm.RhythmProjectManager.deleteRhythm(context, header.id)
                                        savedRhythmsList = com.example.domain.rhythm.RhythmProjectManager.listSavedRhythms(context)
                                    }) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadRhythmDialog = false }) { Text("Fechar") }
            }
        )
    }


    





    
    val ppq = MidiRhythmState.PPQ
    val ticksPerBeat = MidiRhythmState.getTicksPerBeat()
    val ticksPerMeasure = MidiRhythmState.getTicksPerMeasure()
    val totalTicks = MidiRhythmState.getTotalTicks()

    DisposableEffect(MidiRhythmState.selectedTrackIndex, MidiState.isArrangerMode) {
        if (!MidiState.isArrangerMode && MidiRhythmState.tracks.isNotEmpty()) {
            val track = MidiRhythmState.tracks.getOrNull(MidiRhythmState.selectedTrackIndex)
            if (track != null) {
                com.example.domain.midi.GlobalMidiRouter.overrideRightHandChannel = track.channel - 1
                MidiRhythmState.applyTrackVoice(track)
            }
        }
        onDispose {
            com.example.domain.midi.GlobalMidiRouter.overrideRightHandChannel = null
        }
    }


        // Playback loop

        val arrangerCoreState = androidx.compose.runtime.remember { com.example.domain.arranger.ArrangerCoreState() }
    val creatorEngine = androidx.compose.runtime.remember { com.example.domain.arranger.StyleCreatorEngine(arrangerCoreState, ppq) }

    // Sync bpm continuously
    arrangerCoreState.bpm = MidiRhythmState.bpm

    DisposableEffect(MidiRhythmState.isPlaying, MidiRhythmState.isPaused) {
        if (MidiRhythmState.isPlaying && !MidiRhythmState.isPaused) {
            creatorEngine.start()
        } else {
            creatorEngine.stop()
        }
        onDispose {
            creatorEngine.stop()
        }
    }




androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f),
        title = { Text("Rhythm Creator") },
        text = {
            var kbdOffsetX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
            var kbdOffsetY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(200f) }
            var showVirtualKeyboardDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            
            Box(modifier = Modifier.fillMaxSize()) {
            if (showExtMidi) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Button(onClick = { showExtMidi = false }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text("Back to Rhythm Creator") }
                    Box(modifier = Modifier.weight(1f)) {
                        com.example.ui.validation.ExternalMidiControlPanel(null)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
                    // Top Global Controls
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    // Send Local Control OFF to all channels to prevent internal engine playback
                                    for (ch in 0..15) {
                                        MidiState.sendControlChange(ch, 122, 0)
                                    }
                                    MidiRhythmState.isLocalControlOff = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (MidiRhythmState.isLocalControlOff) Color(0xFF1976D2) else Color.DarkGray),
                                modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text(if (MidiRhythmState.isLocalControlOff) "Local OFF (Ativo)" else "Local OFF", fontSize = 12.sp) }

                            Button(
                                onClick = {
                                    // Send Local Control ON to all channels
                                    for (ch in 0..15) {
                                        MidiState.sendControlChange(ch, 122, 127)
                                    }
                                    MidiRhythmState.isLocalControlOff = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (!MidiRhythmState.isLocalControlOff) Color(0xFF2E7D32) else Color.DarkGray),
                                modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text(if (!MidiRhythmState.isLocalControlOff) "Local ON (Ativo)" else "Local ON", fontSize = 12.sp) }

                            val uiContext = androidx.compose.ui.platform.LocalContext.current
                            val soundEngine = MidiState.soundOutputEngine
                            Button(
                                onClick = {
                                    val next = when (soundEngine) {
                                        com.example.domain.soundfont.model.SoundOutputEngine.YAMAHA_HARDWARE -> com.example.domain.soundfont.model.SoundOutputEngine.SOUNDFONT_INTERNAL
                                        com.example.domain.soundfont.model.SoundOutputEngine.SOUNDFONT_INTERNAL -> com.example.domain.soundfont.model.SoundOutputEngine.HYBRID_BOTH
                                        com.example.domain.soundfont.model.SoundOutputEngine.HYBRID_BOTH -> com.example.domain.soundfont.model.SoundOutputEngine.YAMAHA_HARDWARE
                                    }
                                    MidiState.setSoundOutput(next, uiContext)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when (soundEngine) {
                                        com.example.domain.soundfont.model.SoundOutputEngine.YAMAHA_HARDWARE -> Color(0xFF37474F)
                                        com.example.domain.soundfont.model.SoundOutputEngine.SOUNDFONT_INTERNAL -> Color(0xFF6A1B9A)
                                        com.example.domain.soundfont.model.SoundOutputEngine.HYBRID_BOTH -> Color(0xFF00695C)
                                    }
                                ),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(soundEngine.shortName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showSoundFontHelpDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8)),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("📁 SoundFonts (Pastas)", fontSize = 11.sp, color = Color.White)
                            }

                            val currentTrack = MidiRhythmState.tracks.getOrNull(MidiRhythmState.selectedTrackIndex)
                            val isCurrentBass = currentTrack?.channel == 11 || currentTrack?.name?.contains("Bass", true) == true
                            val rootName = if (isCurrentBass) "C (Fixo)" else (currentTrack?.sourceRoot?.let { com.example.domain.chord.NoteUtils.NOTE_NAMES[it.rootKey % 12] } ?: "C")
                            Text("💡 Origem: $rootName", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(end=4.dp))
                            Button(
                                onClick = {
                                    if (!MidiRhythmState.isRecording) {
                                        MidiRhythmState.lastRecordingSnapshots[MidiRhythmState.currentSection] = MidiRhythmState.tracks.map { track -> 
                                            RhythmTrack(track.name, track.channel, track.bankMsb, track.bankLsb, track.program, track.isDrumTrack).apply {
                                                notes = track.notes.toList()
                                                ccEvents = track.ccEvents.toList()
                                                volume = track.volume
                                                pan = track.pan
                                                isMuted = track.isMuted
                                                sourceRoot = track.sourceRoot
                                                soundFontVoice = track.soundFontVoice
                                                drumParams.putAll(track.drumParams)
                                            }
                                        }
                                    } else {
                                        MidiRhythmState.clearActiveNotes()
                                    }
                                    MidiRhythmState.isRecording = !MidiRhythmState.isRecording 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (MidiRhythmState.isRecording) Color.Red else Color.DarkGray),
                                modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("REC", fontSize = 12.sp) }

                            var showInputMenu by remember { mutableStateOf(false) }
                            Box {
                                Button(
                                    onClick = { showInputMenu = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("In: ${MidiRhythmState.recordInputSource.displayName}", fontSize = 11.sp, color = Color.White)
                                }
                                DropdownMenu(expanded = showInputMenu, onDismissRequest = { showInputMenu = false }) {
                                    MidiRhythmState.RecordInputSource.values().forEach { source ->
                                        DropdownMenuItem(
                                            text = { Text(source.displayName) },
                                            onClick = {
                                                MidiRhythmState.recordInputSource = source
                                                showInputMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            var showSectionMenu by remember { mutableStateOf(false) }
                            Box {
                                Button(onClick = { showSectionMenu = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray), modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text(MidiRhythmState.currentSection.name, fontSize = 12.sp)
                                }
                                DropdownMenu(expanded = showSectionMenu, onDismissRequest = { showSectionMenu = false }) {
                                    com.example.StyleSection.values().forEach { section ->
                                        DropdownMenuItem(text = { Text(section.name) }, onClick = {
                                            MidiRhythmState.currentSection = section
                                            showSectionMenu = false
                                        })
                                    }
                                }
                            }
                            
                            val context = androidx.compose.ui.platform.LocalContext.current
                            Button(
                                onClick = {
                                    rhythmNameInput = "Ritmo ${System.currentTimeMillis() % 1000}"
                                    showSaveRhythmDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("💾 SALVAR", fontSize = 11.sp, fontWeight = FontWeight.Bold) }

                            Button(
                                onClick = {
                                    savedRhythmsList = com.example.domain.rhythm.RhythmProjectManager.listSavedRhythms(context)
                                    showLoadRhythmDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                                modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("📂 CARREGAR", fontSize = 11.sp, fontWeight = FontWeight.Bold) }

                            Button(
                                onClick = {
                                    val nextPoly = if (com.example.MidiState.polyphonyMode == 128) 64 else 128
                                    com.example.MidiState.setPolyphony(nextPoly)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (com.example.MidiState.polyphonyMode == 128) Color(0xFFE65100) else Color(0xFF5D4037)),
                                modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("⚡ ${com.example.MidiState.polyphonyMode} Vozes", fontSize = 11.sp, fontWeight = FontWeight.Bold) }

                            Button(
                                onClick = {
                                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val styleData = com.example.domain.midi.CasmWriter.writeStyle(MidiRhythmState.sectionTracks, ppq)
                                        com.example.domain.midi.FileExporter.saveStyleFile(context, styleData, "YamahaStyle.sty")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                                modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("EXPORT STY", fontSize = 11.sp) }
                            
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.DarkGray, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 4.dp).height(32.dp)) {
                                androidx.compose.material3.IconButton(onClick = { MidiRhythmState.measures = (MidiRhythmState.measures - 1).coerceAtLeast(1) }, modifier = Modifier.size(24.dp)) {
                                    Text("-", color = Color.White, fontSize = 12.sp)
                                }
                                Text("${MidiRhythmState.measures} Meas", color = Color.White, fontSize = 10.sp)
                                androidx.compose.material3.IconButton(onClick = { MidiRhythmState.measures = (MidiRhythmState.measures + 1).coerceAtMost(8) }, modifier = Modifier.size(24.dp)) {
                                    Text("+", color = Color.White, fontSize = 12.sp)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.DarkGray, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 4.dp).height(32.dp)) {
                                androidx.compose.material3.IconButton(onClick = { MidiRhythmState.timeSigNumerator = (MidiRhythmState.timeSigNumerator - 1).coerceAtLeast(2) }, modifier = Modifier.size(24.dp)) {
                                    Text("-", color = Color.White, fontSize = 12.sp)
                                }
                                Text("${MidiRhythmState.timeSigNumerator}/${MidiRhythmState.timeSigDenominator}", color = Color.White, fontSize = 10.sp)
                                androidx.compose.material3.IconButton(onClick = { MidiRhythmState.timeSigNumerator = (MidiRhythmState.timeSigNumerator + 1).coerceAtMost(16) }, modifier = Modifier.size(24.dp)) {
                                    Text("+", color = Color.White, fontSize = 12.sp)
                                }
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.DarkGray, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 4.dp).height(32.dp)) {
                                Text("Quant: 1/${MidiRhythmState.quantizeResolution}", color = Color.White, fontSize = 10.sp, modifier = Modifier.clickable {
                                    MidiRhythmState.quantizeResolution = when (MidiRhythmState.quantizeResolution) {
                                        4 -> 8; 8 -> 16; 16 -> 32; 32 -> 4; else -> 4
                                    }
                                })
                            }
                        }

                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { showExtMidi = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF009688)), modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("EXT MIDI", color = Color.White, fontSize = 12.sp)
                            }
                            Button(onClick = { MidiRhythmState.showDrumSetup = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)), modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("DRUM SETUP", color = Color.White, fontSize = 12.sp)
                            }
                            Button(onClick = { 
                                MidiState.sendSysEx(byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, 0x00, 0x00, 0x7E, 0x00, 0xF7.toByte()))
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)), modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("INIT XG", color = Color.White, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { MidiState.isArrangerMode = !MidiState.isArrangerMode },
                                colors = ButtonDefaults.buttonColors(containerColor = if (MidiState.isArrangerMode) Color.Magenta else Color.DarkGray),
                                modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("Arranger", color = Color.White, fontSize = 12.sp) }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.background(Color.DarkGray, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 4.dp).height(32.dp)
                            ) {
                                androidx.compose.material3.IconButton(onClick = { MidiState.splitPoint = (MidiState.splitPoint - 1).coerceAtLeast(24) }, modifier = Modifier.size(24.dp)) {
                                    Text("-", color = Color.White, fontSize = 12.sp)
                                }
                                Text("Split: ${com.example.domain.chord.NoteUtils.NOTE_NAMES[MidiState.splitPoint % 12]} ${(MidiState.splitPoint / 12) - 1}", color = Color.White, fontSize = 10.sp)
                                androidx.compose.material3.IconButton(onClick = { MidiState.splitPoint = (MidiState.splitPoint + 1).coerceAtMost(96) }, modifier = Modifier.size(24.dp)) {
                                    Text("+", color = Color.White, fontSize = 12.sp)
                                }
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.background(Color.DarkGray, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 4.dp).height(32.dp).width(200.dp)
                            ) {
                                Text("Tempo", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(end=4.dp))
                                androidx.compose.foundation.layout.Box(modifier = Modifier.clickable { MidiRhythmState.bpm = (MidiRhythmState.bpm - 1f).coerceAtLeast(40f) }.padding(horizontal=4.dp)) {
                                    Text("-", color = Color.White, fontSize = 16.sp)
                                }
                                Slider(
                                    value = MidiRhythmState.bpm,
                                    valueRange = 40f..300f,
                                    onValueChange = { MidiRhythmState.bpm = it },
                                    modifier = Modifier.weight(1f).height(24.dp)
                                )
                                androidx.compose.foundation.layout.Box(modifier = Modifier.clickable { MidiRhythmState.bpm = (MidiRhythmState.bpm + 1f).coerceAtMost(300f) }.padding(horizontal=4.dp)) {
                                    Text("+", color = Color.White, fontSize = 14.sp)
                                }
                                Text("${MidiRhythmState.bpm.toInt()}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(start=4.dp))
                            }
                            var showMetronomeConfigDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            if (showMetronomeConfigDialog) {
                                com.example.ui.arranger.MetronomeDialog(onDismiss = { showMetronomeConfigDialog = false })
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { 
                                        MidiRhythmState.metronomeEnabled = !MidiRhythmState.metronomeEnabled
                                        com.example.domain.metronome.MetronomeConfig.enabled = MidiRhythmState.metronomeEnabled
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (MidiRhythmState.metronomeEnabled) Color(0xFFE91E63) else Color.DarkGray),
                                    modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)
                                ) { Text(if (MidiRhythmState.metronomeEnabled) "Click ON" else "Click OFF", color = Color.White, fontSize = 12.sp) }

                                IconButton(
                                    onClick = { showMetronomeConfigDialog = true },
                                    modifier = Modifier.size(32.dp).padding(start = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Configurar Click",
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        
                        // NEW ROW for Play/Stop and Virtual Keyboard Toggle
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(
                                onClick = { 
                                    MidiRhythmState.isPlaying = !MidiRhythmState.isPlaying
                                    MidiRhythmState.isPaused = false
                                    if (!MidiRhythmState.isPlaying) { MidiRhythmState.currentTick = 0; MidiRhythmState.uiTick = 0 }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (MidiRhythmState.isPlaying) Color.Red else Color.Green),
                                modifier = Modifier.height(36.dp).weight(1f).padding(end = 4.dp)
                            ) { Text(if (MidiRhythmState.isPlaying) "⏹ STOP (Rhythm)" else "▶ PLAY (Rhythm)", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                            
                            Button(
                                onClick = { showVirtualKeyboardDialog = !showVirtualKeyboardDialog },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                                modifier = Modifier.height(36.dp).weight(1f).padding(start = 4.dp)
                            ) { Text("🎹 Teclado Virtual", fontSize = 12.sp) }
                        }
                        
                    }
                    
                    if (MidiState.isArrangerMode) {
                        com.example.ui.validation.ChordEducationalPanel(activeNotes = MidiRhythmState.activeArrangerNotes, currentChord = MidiRhythmState.detectedChord, compact = true)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // Tracks
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(MidiRhythmState.tracks.size) { index ->
                            val track = MidiRhythmState.tracks[index]
                            val isSelected = index == MidiRhythmState.selectedTrackIndex
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(2.dp, if (isSelected) Color.Yellow else Color.Transparent)
                                    .background(if (isSelected) Color.DarkGray else Color.LightGray)
                                    .clickable { MidiRhythmState.selectTrack(index) }
                                    .padding(8.dp)
                            ) {
                                Text(track.name, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Color.Black)
                                
                                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("CH${track.channel}", fontSize = 10.sp, color = if(isSelected) Color.LightGray else Color.DarkGray, modifier = Modifier.align(Alignment.CenterVertically))
                                    
                                    var showVoiceDialog by remember { mutableStateOf(false) }
                                    if (showVoiceDialog) {
                                        var voiceTab by remember { mutableStateOf(if (track.soundFontVoice != null) 1 else 0) }
                                        val sfVoices = MidiState.soundFontRepository.availableVoices
                                        val availableCategories = remember(sfVoices.size) {
                                            listOf("Todos") + sfVoices.map { it.category }.distinct().sorted()
                                        }
                                        var selectedCategory by remember { mutableStateOf("Todos") }

                                        androidx.compose.material3.AlertDialog(
                                            onDismissRequest = { showVoiceDialog = false },
                                            title = { Text("Selecionar Instrumento (Canal ${track.channel})") },
                                            text = {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    TabRow(selectedTabIndex = voiceTab) {
                                                        Tab(
                                                            selected = voiceTab == 0,
                                                            onClick = { voiceTab = 0 },
                                                            text = { Text("🎹 Yamaha PSR-S750", fontSize = 11.sp) }
                                                        )
                                                        Tab(
                                                            selected = voiceTab == 1,
                                                            onClick = { voiceTab = 1 },
                                                            text = { Text("🎵 SoundFonts (${sfVoices.size})", fontSize = 11.sp) }
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    if (voiceTab == 0) {
                                                        LazyColumn(modifier = Modifier.height(320.dp)) {
                                                            val list = if (track.channel == 9) {
                                                                com.example.InstrumentDatabase.drumKits + com.example.InstrumentDatabase.instruments
                                                            } else if (track.isDrumTrack) {
                                                                com.example.InstrumentDatabase.drumKits
                                                            } else {
                                                                com.example.InstrumentDatabase.instruments
                                                            }
                                                            items(list) { voice ->
                                                                Surface(onClick = {
                                                                    track.soundFontVoice = null
                                                                    track.bankMsb = voice.msb; track.bankLsb = voice.lsb; track.program = voice.pc
                                                                    
                                                                    val isSelectedDrum = com.example.InstrumentDatabase.drumKits.any { it.msb == voice.msb && it.lsb == voice.lsb && it.pc == voice.pc }
                                                                    if (track.isDrumTrack != isSelectedDrum) {
                                                                        track.isDrumTrack = isSelectedDrum
                                                                        val xgPart = track.channel - 1
                                                                        val setupNum = if (isSelectedDrum) {
                                                                            if (track.channel == 9) 0x02.toByte() else 0x01.toByte()
                                                                        } else {
                                                                            0x00.toByte()
                                                                        }
                                                                        MidiState.sendSysEx(byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, 0x08, xgPart.toByte(), 0x07, setupNum, 0xF7.toByte()))
                                                                    }
                                                                    
                                                                    MidiRhythmState.applyTrackVoice(track, force = true)
                                                                    showVoiceDialog = false
                                                                }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                                                    Text("${voice.name} (${voice.msb}-${voice.lsb}-${voice.pc})", modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        // SoundFonts Tab
                                                        if (sfVoices.isEmpty()) {
                                                            Column(
                                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Text("Nenhum SoundFont indexado ainda.", fontSize = 12.sp, color = Color.Gray)
                                                                Text("Toque abaixo para abrir a pasta do Korg Triton ou importar arquivos .sf2 / .sfz.", fontSize = 11.sp, color = Color.DarkGray)
                                                                Button(
                                                                    onClick = {
                                                                        showVoiceDialog = false
                                                                        showSoundFontHelpDialog = true
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8))
                                                                ) {
                                                                    Text("📁 Abrir Pasta / Importar SoundFonts", fontSize = 11.sp)
                                                                }
                                                            }
                                                        } else {
                                                            // SoundFonts Tab with full search & category management
                                                            var sfSearchQuery by remember { mutableStateOf("") }
                                                            var voiceToReclassifyInTrack by remember { mutableStateOf<com.example.domain.soundfont.model.SoundFontVoice?>(null) }

                                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                                // Search bar
                                                                OutlinedTextField(
                                                                    value = sfSearchQuery,
                                                                    onValueChange = { sfSearchQuery = it },
                                                                    placeholder = { Text("🔍 Buscar Triton, Rhodes, kit...", fontSize = 12.sp) },
                                                                    singleLine = true,
                                                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                                                )

                                                                // Category chips
                                                                val allCategoriesList = remember(sfVoices.size) {
                                                                    listOf("Todos") + com.example.domain.soundfont.repository.SoundFontCategoryManager.getAllCategories()
                                                                }
                                                                val scrollStateCat = rememberScrollState()
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth().horizontalScroll(scrollStateCat).padding(bottom = 6.dp),
                                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                ) {
                                                                    allCategoriesList.forEach { cat ->
                                                                        val count = if (cat == "Todos") sfVoices.size else sfVoices.count { it.category.equals(cat, ignoreCase = true) }
                                                                        FilterChip(
                                                                            selected = selectedCategory == cat,
                                                                            onClick = { selectedCategory = cat },
                                                                            label = { Text(if (count > 0 && cat != "Todos") "$cat ($count)" else cat, fontSize = 10.sp) },
                                                                            modifier = Modifier.height(26.dp)
                                                                        )
                                                                    }
                                                                }

                                                                val filteredSf = sfVoices.filter { sf ->
                                                                    val matchesCat = (selectedCategory == "Todos") || sf.category.equals(selectedCategory, ignoreCase = true)
                                                                    val matchesQuery = if (sfSearchQuery.isBlank()) true else {
                                                                        val q = sfSearchQuery.trim().lowercase()
                                                                        sf.name.lowercase().contains(q) || sf.category.lowercase().contains(q) || sf.filePath.lowercase().contains(q)
                                                                    }
                                                                    matchesCat && matchesQuery
                                                                }

                                                                Text("${filteredSf.size} de ${sfVoices.size} instrumentos", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))

                                                                LazyColumn(modifier = Modifier.height(280.dp)) {
                                                                    items(filteredSf, key = { it.id }) { sf ->
                                                                        val isLoaded = MidiState.soundFontRepository.isVoiceLoaded(sf)
                                                                        val isSelected = track.soundFontVoice?.id == sf.id
                                                                        Card(
                                                                            onClick = {
                                                                                track.soundFontVoice = sf
                                                                                MidiRhythmState.applyTrackVoice(track, force = true)
                                                                                MidiState.ensureSoundFontAudioActive(context)
                                                                                if (MidiState.soundOutputEngine == com.example.domain.soundfont.model.SoundOutputEngine.YAMAHA_HARDWARE) {
                                                                                    MidiState.setSoundOutput(com.example.domain.soundfont.model.SoundOutputEngine.SOUNDFONT_INTERNAL, context)
                                                                                }
                                                                                showVoiceDialog = false
                                                                            },
                                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                                            colors = CardDefaults.cardColors(
                                                                                containerColor = if (isSelected) Color(0xFFEDE7F6) else MaterialTheme.colorScheme.surfaceVariant
                                                                            )
                                                                        ) {
                                                                            Row(
                                                                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                                verticalAlignment = Alignment.CenterVertically
                                                                            ) {
                                                                                Column(modifier = Modifier.weight(1f)) {
                                                                                    Text(sf.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF4A148C))
                                                                                    Text("${sf.category} • ${sf.fileType.name} Stereo • ${sf.fileSizeFormatted}", fontSize = 10.sp, color = Color.Gray)
                                                                                }

                                                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                                    // Audition preview note
                                                                                    IconButton(
                                                                                        onClick = {
                                                                                            kotlinx.coroutines.GlobalScope.launch {
                                                                                                try {
                                                                                                    val ch = track.channel - 1
                                                                                                    MidiState.ensureSoundFontAudioActive(context)
                                                                                                    MidiState.soundFontSynth.setChannelVoice(ch, sf)
                                                                                                    val previewNote = if (track.isDrumTrack || sf.category.contains("bateria", ignoreCase = true) || sf.category.contains("drum", ignoreCase = true)) 36 else 60
                                                                                                    MidiState.soundFontSynth.noteOn(ch, previewNote, 105)
                                                                                                    kotlinx.coroutines.delay(420)
                                                                                                    MidiState.soundFontSynth.noteOff(ch, previewNote)
                                                                                                } catch (e: Exception) {}
                                                                                            }
                                                                                        },
                                                                                        modifier = Modifier.size(28.dp)
                                                                                    ) {
                                                                                        Text("🔊", fontSize = 12.sp)
                                                                                    }

                                                                                    // Reclassify category button
                                                                                    IconButton(
                                                                                        onClick = { voiceToReclassifyInTrack = sf },
                                                                                        modifier = Modifier.size(28.dp)
                                                                                    ) {
                                                                                        Text("🏷️", fontSize = 12.sp)
                                                                                    }

                                                                                    Surface(
                                                                                        color = if (isLoaded) Color(0xFFC8E6C9) else Color(0xFFE0E0E0),
                                                                                        shape = RoundedCornerShape(4.dp)
                                                                                    ) {
                                                                                        Text(
                                                                                            text = if (isLoaded) "⚡ RAM" else "💾 Sob Demanda",
                                                                                            fontSize = 9.sp,
                                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                                                            color = Color.Black
                                                                                        )
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                                voiceToReclassifyInTrack?.let { targetSf ->
                                                                    com.example.ui.arranger.ReclassifyCategoryDialog(
                                                                        voice = targetSf,
                                                                        categories = com.example.domain.soundfont.repository.SoundFontCategoryManager.getAllCategories(),
                                                                        onDismiss = { voiceToReclassifyInTrack = null },
                                                                        onCategoryAssigned = { newCat ->
                                                                            com.example.domain.soundfont.repository.SoundFontCategoryManager.assignCategory(context, targetSf, newCat)
                                                                            MidiState.soundFontRepository.updateVoiceCategory(targetSf.id, newCat)
                                                                            voiceToReclassifyInTrack = null
                                                                            android.widget.Toast.makeText(context, "\"${targetSf.name}\" agora é: $newCat", android.widget.Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            confirmButton = { Button(onClick = { showVoiceDialog = false }) { Text("Fechar") } }
                                        )
                                    }
                                    val yVoice = remember(track.bankMsb, track.bankLsb, track.program) {
                                        com.example.InstrumentDatabase.instruments.find { it.msb == track.bankMsb && it.lsb == track.bankLsb && it.pc == track.program }
                                            ?: com.example.InstrumentDatabase.drumKits.find { it.msb == track.bankMsb && it.lsb == track.bankLsb && it.pc == track.program }
                                    }
                                    Button(
                                        onClick = { showVoiceDialog = true },
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (track.soundFontVoice != null) Color(0xFF6A1B9A) else Color(0xFF00838F)
                                        )
                                    ) {
                                        val label = if (track.soundFontVoice != null) {
                                            "🎵 ${track.soundFontVoice?.name?.take(14)}"
                                        } else {
                                            "🎹 ${yVoice?.name?.take(12) ?: "${track.bankMsb}-${track.bankLsb}-${track.program}"}"
                                        }
                                        Text(label, fontSize = 10.sp, color = Color.White)
                                    }
                                    
                                    Button(onClick = { track.isMuted = !track.isMuted }, colors = ButtonDefaults.buttonColors(containerColor = if (track.isMuted) Color.Red else Color.Gray), modifier = Modifier.height(28.dp).width(36.dp), contentPadding = PaddingValues(0.dp)) { Text("M", fontSize = 10.sp) }
                                    
                                    Button(onClick = { 
                                        track.isDrumTrack = !track.isDrumTrack 
                                        if (track.isDrumTrack) {
                                            val xgPart = track.channel - 1
                                            val setupNum = if (track.channel == 9) 0x02.toByte() else 0x01.toByte()
                                            MidiState.sendSysEx(byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, 0x08, xgPart.toByte(), 0x07, setupNum, 0xF7.toByte()))
                                        } else {
                                            val xgPart = track.channel - 1
                                            MidiState.sendSysEx(byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, 0x08, xgPart.toByte(), 0x07, 0x00, 0xF7.toByte()))
                                        }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = if (track.isDrumTrack) Color.Blue else Color.Gray), modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("DRUM", fontSize = 10.sp) }

                                    if (!track.isDrumTrack) {
                                        val isBassTrack = track.channel == 11 || track.name.contains("Bass", true)
                                        val rootLabel = if (isBassTrack) "C (Fixo)" else (track.sourceRoot?.let { com.example.domain.chord.NoteUtils.NOTE_NAMES[it.rootKey % 12] } ?: "C")
                                        Text("Ref: $rootLabel", fontSize = 10.sp, color = if (isSelected) Color.Cyan else Color.DarkGray, modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp))
                                    }

                                    Button(onClick = { 
                                        if (track.notes.isNotEmpty()) {
                                            track.notes = track.notes.dropLast(1)
                                        }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray), modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Undo", fontSize = 10.sp) }

                                    Button(onClick = { track.notes = emptyList() }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray), modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Del", fontSize = 10.sp) }
                                    
                                    Button(onClick = {
                                        MidiRhythmState.quantizeTrack(index)
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)), modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Q", fontSize = 10.sp) }
                                    
                                    Button(onClick = {
                                        MidiRhythmState.unquantizeTrack(index)
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("U", fontSize = 10.sp) }
                                    
                                    var showCopyDialog by remember { mutableStateOf(false) }
                                    if (showCopyDialog) {
                                        var selectedSection by remember { mutableStateOf(MidiRhythmState.currentSection) }
                                        var targetTrackIdx by remember { mutableStateOf(index) }
                                        androidx.compose.material3.AlertDialog(
                                            onDismissRequest = { showCopyDialog = false },
                                            title = { Text("Copy ${track.name}") },
                                            text = {
                                                Column {
                                                    Text("To Section:")
                                                    var secMenu by remember { mutableStateOf(false) }
                                                    Box {
                                                        Button(onClick = { secMenu = true }) { Text(selectedSection.name) }
                                                        DropdownMenu(expanded = secMenu, onDismissRequest = { secMenu = false }) {
                                                            com.example.StyleSection.values().forEach { sec ->
                                                                DropdownMenuItem(text = { Text(sec.name) }, onClick = { selectedSection = sec; secMenu = false })
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Text("To Track:")
                                                    var trkMenu by remember { mutableStateOf(false) }
                                                    Box {
                                                        val targetTracks = MidiRhythmState.sectionTracks[selectedSection] ?: emptyList()
                                                        val trkName = targetTracks.getOrNull(targetTrackIdx)?.name ?: "Track ${targetTrackIdx+1}"
                                                        Button(onClick = { trkMenu = true }) { Text(trkName) }
                                                        DropdownMenu(expanded = trkMenu, onDismissRequest = { trkMenu = false }) {
                                                            targetTracks.forEachIndexed { i, t ->
                                                                DropdownMenuItem(text = { Text(t.name) }, onClick = { targetTrackIdx = i; trkMenu = false })
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                Button(onClick = {
                                                    val targetTracks = MidiRhythmState.sectionTracks[selectedSection]
                                                    if (targetTracks != null) {
                                                        val targetTrack = targetTracks.getOrNull(targetTrackIdx)
                                                        if (targetTrack != null) {
                                                            targetTrack.notes = track.notes.map { it.copy() }
                                                            targetTrack.ccEvents = track.ccEvents.map { it.copy() }
                                                        }
                                                    }
                                                    showCopyDialog = false
                                                }) { Text("Copy") }
                                            },
                                            dismissButton = {
                                                Button(onClick = { showCopyDialog = false }) { Text("Cancel") }
                                            }
                                        )
                                    }
                                    Button(onClick = { showCopyDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)), modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Copy", fontSize = 10.sp) }
                                }
                                
                                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val sliderColors = SliderDefaults.colors(
                                        thumbColor = if (isSelected) Color.Cyan else Color.Blue,
                                        activeTrackColor = if (isSelected) Color.Cyan else Color.Blue,
                                        inactiveTrackColor = if (isSelected) Color.Gray else Color.LightGray
                                    )
                                    val textColor = if (isSelected) Color.White else Color.Black
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(60.dp)) {
                                        Text("Vol", fontSize = 8.sp, color = textColor, modifier = Modifier.width(20.dp))
                                        Slider(value = track.volume.toFloat(), valueRange = 0f..127f, onValueChange = { track.volume = it.toInt(); MidiState.sendControlChange(track.channel - 1, 7, track.volume) }, colors = sliderColors, modifier = Modifier.height(24.dp).weight(1f))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(60.dp)) {
                                        Text("Pan", fontSize = 8.sp, color = textColor, modifier = Modifier.width(20.dp))
                                        Slider(value = track.pan.toFloat(), valueRange = 0f..127f, onValueChange = { track.pan = it.toInt(); MidiState.sendControlChange(track.channel - 1, 10, track.pan) }, colors = sliderColors, modifier = Modifier.height(24.dp).weight(1f))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(60.dp)) {
                                        Text("Rev", fontSize = 8.sp, color = textColor, modifier = Modifier.width(20.dp))
                                        Slider(value = track.reverb.toFloat(), valueRange = 0f..127f, onValueChange = { track.reverb = it.toInt(); MidiState.sendControlChange(track.channel - 1, 91, track.reverb) }, colors = sliderColors, modifier = Modifier.height(24.dp).weight(1f))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(60.dp)) {
                                        Text("Cho", fontSize = 8.sp, color = textColor, modifier = Modifier.width(20.dp))
                                        Slider(value = track.chorus.toFloat(), valueRange = 0f..127f, onValueChange = { track.chorus = it.toInt(); MidiState.sendControlChange(track.channel - 1, 93, track.chorus) }, colors = sliderColors, modifier = Modifier.height(24.dp).weight(1f))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(60.dp)) {
                                        Text("DSP", fontSize = 8.sp, color = textColor, modifier = Modifier.width(20.dp))
                                        Slider(value = track.dsp.toFloat(), valueRange = 0f..127f, onValueChange = { track.dsp = it.toInt(); MidiState.sendControlChange(track.channel - 1, 94, track.dsp) }, colors = sliderColors, modifier = Modifier.height(24.dp).weight(1f))
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Timeline Canvas
                                    Canvas(modifier = Modifier.fillMaxWidth().height(40.dp).background(Color.White).clip(androidx.compose.ui.graphics.RectangleShape)) {
                                        val w = size.width
                                        val h = size.height
                                        val measWidth = w / MidiRhythmState.measures
                                        for (i in 0..MidiRhythmState.measures) {
                                            drawLine(Color.DarkGray, start = androidx.compose.ui.geometry.Offset(i * measWidth, 0f), end = androidx.compose.ui.geometry.Offset(i * measWidth, h), strokeWidth = 2f)
                                        }
                                        val beatsPerMeas = MidiRhythmState.timeSigNumerator
                                        val beatWidth = measWidth / beatsPerMeas
                                        for (m in 0 until MidiRhythmState.measures) {
                                            for (b in 1 until beatsPerMeas) {
                                                val x = (m * measWidth) + (b * beatWidth)
                                                drawLine(Color(0xFF333333), start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, h), strokeWidth = 1f)
                                            }
                                        }
                                        val trackNotes = track.notes
                                        for (i in trackNotes.indices) {
                                            val note = trackNotes[i]
                                            val nx = (note.tick.toFloat() / totalTicks.toFloat()) * w
                                            val nw = kotlin.math.max(2f, (note.durationTicks.toFloat() / totalTicks.toFloat()) * w)
                                            drawRect(Color(0xFF03A9F4), topLeft = androidx.compose.ui.geometry.Offset(nx, 4f), size = androidx.compose.ui.geometry.Size(nw, h - 8f))
                                        }
                                        val px = (MidiRhythmState.uiTick.toFloat() / totalTicks.toFloat()).coerceIn(0f, 1f) * w
                                        drawLine(Color.Red, start = androidx.compose.ui.geometry.Offset(px, 0f), end = androidx.compose.ui.geometry.Offset(px, h), strokeWidth = 2f)
                                    }
                                }
                            }
                        }
                    }
                    
                                    }

                Box(
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(kbdOffsetX.toInt(), kbdOffsetY.toInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                kbdOffsetX += dragAmount.x
                                kbdOffsetY += dragAmount.y
                            }
                        }
                ) {
                        if (showVirtualKeyboardDialog) {
                            val selectedTrk = MidiRhythmState.tracks.getOrNull(MidiRhythmState.selectedTrackIndex)
                            androidx.compose.material3.Card(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 6.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFF212121))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("🎹 Teclado Virtual & Validador de Gravação", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                        androidx.compose.material3.IconButton(onClick = { showVirtualKeyboardDialog = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "Fechar Teclado", tint = Color.White)
                                        }
                                    }
                                    
                                    com.example.ui.arranger.VirtualKeyboard(
                                        track = selectedTrk,
                                        onConfigChange = { rKey, cType ->
                                            selectedTrk?.sourceRoot = com.example.domain.models.Chord(rKey, cType)
                                        },
                                        onNoteClick = { note ->
                                            if (selectedTrk != null) {
                                                val ch = selectedTrk.channel - 1
                                                val midiPitch = note + 48
                                                val now = System.nanoTime()
                                                com.example.domain.midi_core.MidiDispatcher.sendNoteOn(ch, midiPitch, 100, now)
                                                // Trigger recording flow if in REC mode
                                                if (com.example.MidiRhythmState.isRecording) {
                                                    val status = 0x90 or (ch and 0x0F)
                                                    com.example.MidiRhythmState.processIncomingMidi(status, midiPitch, 100, now)
                                                }
                                                kotlinx.coroutines.GlobalScope.launch {
                                                    kotlinx.coroutines.delay(200)
                                                    val offNow = System.nanoTime()
                                                    com.example.domain.midi_core.MidiDispatcher.sendNoteOff(ch, midiPitch, 0, offNow)
                                                    if (com.example.MidiRhythmState.isRecording) {
                                                        val offStatus = 0x80 or (ch and 0x0F)
                                                        com.example.MidiRhythmState.processIncomingMidi(offStatus, midiPitch, 0, offNow)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                }
            } // Closes outer Box(modifier = Modifier.fillMaxSize())
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}
// Triggering fresh deployment
