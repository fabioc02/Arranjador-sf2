package com.example.ui.validation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.domain.models.StyleSection
import com.example.domain.audio.AssetHelper
import com.example.presentation.viewmodel.ValidationPlayerViewModel
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ValidationPlayerScreen(viewModel: ValidationPlayerViewModel = viewModel(), onOpenRhythmCreator: (() -> Unit)? = null) {
    val context = LocalContext.current
    
    val styFile by viewModel.styFile.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentSection by viewModel.currentSection.collectAsState()
    val bpm by viewModel.bpm.collectAsState()
    val currentChord by viewModel.currentChord.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val transLog by viewModel.transpositionLogs.collectAsState()
    
    DisposableEffect(Unit) {
        val listener: (Int, Int, Int) -> Unit = { status, data1, data2 ->
            viewModel.handleMidiInput(status, data1, data2)
        }
        com.example.MidiState.midiListeners.add(listener)
        onDispose {
            com.example.MidiState.midiListeners.remove(listener)
        }
    }
    
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            try {
                val path = AssetHelper.copyAssetIfNotExist(context, "default.sf2", "default.sf2")
                viewModel.loadSoundFont(path)
            } catch (e: Exception) {
                // Ignore missing soundfont
            }
        }
    }
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Player", "Inspector", "CASM Trace", "MIDI Monitor", "EXT MIDI")

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                inputStream?.use { stream ->
                    val bytes = stream.readBytes()
                    val fileName = it.lastPathSegment ?: "unknown.sty"
                    viewModel.loadStyle(bytes, fileName)
                }
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }

    val sf2PickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                inputStream?.use { stream ->
                    val file = java.io.File(context.filesDir, "custom.sf2")
                    java.io.FileOutputStream(file).use { output ->
                        stream.copyTo(output)
                    }
                    viewModel.loadSoundFont(file.absolutePath)
                }
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }
        
        when (selectedTabIndex) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("PSR Arranger Studio", style = MaterialTheme.typography.headlineSmall)

                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Style")
                    }
                    
                    // Removed Open SoundFont button per P0 requirements
                    
                    Text("Loaded: ${styFile?.name ?: "None"}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.play() },
                            enabled = styFile != null && !isPlaying,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier.weight(1f)
                        ) { Text("Preview Playback") }
                        
                        Button(
                            onClick = { viewModel.stop() },
                            enabled = isPlaying,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                            modifier = Modifier.weight(1f)
                        ) { Text("Stop") }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Sections", style = MaterialTheme.typography.labelLarge)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { viewModel.setSection(StyleSection.IntroA) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Int A", maxLines = 1, fontSize = 10.sp) }
                        Button(onClick = { viewModel.setSection(StyleSection.IntroB) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Int B", maxLines = 1, fontSize = 10.sp) }
                        Button(onClick = { viewModel.setSection(StyleSection.IntroC) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Int C", maxLines = 1, fontSize = 10.sp) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { viewModel.setSection(StyleSection.MainA) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Main A", maxLines = 1, fontSize = 10.sp) }
                        Button(onClick = { viewModel.setSection(StyleSection.MainB) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Main B", maxLines = 1, fontSize = 10.sp) }
                        Button(onClick = { viewModel.setSection(StyleSection.MainC) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Main C", maxLines = 1, fontSize = 10.sp) }
                        Button(onClick = { viewModel.setSection(StyleSection.MainD) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Main D", maxLines = 1, fontSize = 10.sp) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { viewModel.setSection(StyleSection.FillInAA) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Fill A", maxLines = 1, fontSize = 10.sp) }
                        Button(onClick = { viewModel.setSection(StyleSection.FillInBB) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Fill B", maxLines = 1, fontSize = 10.sp) }
                        Button(onClick = { viewModel.setSection(StyleSection.FillInCC) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Fill C", maxLines = 1, fontSize = 10.sp) }
                        Button(onClick = { viewModel.setSection(StyleSection.FillInDD) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Fill D", maxLines = 1, fontSize = 10.sp) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { viewModel.setSection(StyleSection.Break) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Break", maxLines = 1, fontSize = 10.sp) }
                        Button(onClick = { viewModel.setSection(StyleSection.EndingA) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("End A", maxLines = 1, fontSize = 10.sp) }
                        Button(onClick = { viewModel.setSection(StyleSection.EndingB) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("End B", maxLines = 1, fontSize = 10.sp) }
                        Button(onClick = { viewModel.setSection(StyleSection.EndingC) }, enabled = isPlaying, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("End C", maxLines = 1, fontSize = 10.sp) }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("BPM: ${bpm.toInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = bpm.toFloat(),
                        onValueChange = { viewModel.setBpm(it) },
                        valueRange = 40f..240f
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Keyboard Transpose: ", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { com.example.MidiState.keyboardTranspose -= 1 }) { Text("-") }
                        Text("${if (com.example.MidiState.keyboardTranspose > 0) "+" else ""}${com.example.MidiState.keyboardTranspose}")
                        IconButton(onClick = { com.example.MidiState.keyboardTranspose += 1 }) { Text("+") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    
                    val activeLeftHandNotes by viewModel.activeLeftHandNotes.collectAsState()
                    ChordEducationalPanel(activeNotes = activeLeftHandNotes, currentChord = currentChord)
                    ChordDictionaryPanel()
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Chords", style = MaterialTheme.typography.labelLarge)
                    ChordSelector(
                        currentChord = currentChord,
                        onChordSelected = { viewModel.setChord(it) }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Status", style = MaterialTheme.typography.titleMedium)
                            Text("Chord Atual: ${currentChord.displayName}", style = MaterialTheme.typography.bodyMedium)
                            Text("Seção Atual: ${currentSection.name}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Log de Erros / Eventos", style = MaterialTheme.typography.titleMedium)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .background(Color.Black)
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            logs.forEach { logMsg ->
                                Text(logMsg, color = Color.Green, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Transposition Verification", style = MaterialTheme.typography.titleMedium)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color(0xFF1E1E1E))
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            transLog.forEach { log ->
                                val isDrum = log.channel == 9
                                val chStr = if (isDrum) "[Drum/Ch 10]" else "[Ch ${log.channel + 1}]"
                                val ruleStr = if (log.ntr.isNotEmpty() || log.ntt.isNotEmpty()) {
                                    " [${log.ntr}/${log.ntt}]"
                                } else if (isDrum) {
                                    " [Bypass]"
                                } else ""
                                Text(
                                    "$chStr$ruleStr ${log.originalNoteName} -> ${log.chordName} -> ${log.transposedNoteName}",
                                    color = if (isDrum) Color(0xFFFFB300) else Color(0xFF00FFCC),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            1 -> {
                StyleInspectorPanel(styFile, viewModel)
            }
            2 -> {
                CasmTracePanel(currentChord, transLog)
            }
            3 -> {
                MidiMonitorTab(viewModel)
            }
            4 -> {
                ExternalMidiControlPanel(onOpenRhythmCreator = { if (onOpenRhythmCreator != null) onOpenRhythmCreator() })
            }
        }
    }
}

@Composable
fun MidiMonitorTab(viewModel: ValidationPlayerViewModel) {
    val generatedEvents by viewModel.midiMonitor.generatedMessages.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("MIDI Monitor", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            val scrollState = rememberScrollState()
            LaunchedEffect(generatedEvents.size) {
                if (generatedEvents.isNotEmpty()) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }

            Column(modifier = Modifier.verticalScroll(scrollState)) {
                generatedEvents.forEach { event ->
                    val color = when (event.eventType) {
                        "NoteOn" -> Color(0xFF98C379)
                        "NoteOff" -> Color(0xFFE06C75)
                        "CC" -> Color(0xFFE5C07B)
                        "ProgramChange" -> Color(0xFF61AFEF)
                        "PitchBend" -> Color(0xFFC678DD)
                        "SysEx" -> Color(0xFF56B6C2)
                        else -> Color.White
                    }
                    
                    val text = StringBuilder("Tick ${event.tick} | CH${event.channel.toString().padStart(2, '0')} | ${event.eventType.padEnd(12)} ")
                    when (event.eventType) {
                        "NoteOn", "NoteOff" -> text.append("Note ${event.note} (Vel ${event.velocity})")
                        "CC" -> text.append("Ctrl ${event.ccController} (Val ${event.ccValue})")
                        "ProgramChange" -> text.append("Prog ${event.program}")
                        "PitchBend" -> text.append("Val ${event.pitchBendValue}")
                        "SysEx" -> text.append("Data ${event.sysExData?.size ?: 0} bytes")
                    }
                    
                    if (event.ntrApplied.isNotEmpty() || event.nttApplied.isNotEmpty()) {
                        text.append(" | [${event.ntrApplied} / ${event.nttApplied}]")
                    }

                    Text(
                        text = text.toString(),
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
