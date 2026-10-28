package com.example.ui.arranger

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.arranger.ArrangerViewModel
import com.example.domain.models.StyleSection
import com.example.domain.audio.AssetHelper
import com.example.data.parser.StyReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import java.io.InputStream


@Composable
fun ArrangerScreen(
    viewModel: ArrangerViewModel = viewModel(),
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    
    val currentTick by viewModel.currentTick.collectAsState()
    
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentSection by viewModel.currentSection.collectAsState()
    val currentChord by viewModel.currentChord.collectAsState()
    val bpm by viewModel.bpm.collectAsState()
    val isMetronomeOn by viewModel.metronomeEnabled.collectAsState()
    
    var showMetronomeDialog by remember { mutableStateOf(false) }
    if (showMetronomeDialog) {
        MetronomeDialog(onDismiss = { showMetronomeDialog = false })
    }
    
    var loadedStyleName by remember { mutableStateOf("Nenhum estilo carregado") }

    

    val scope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment ?: "unknown.sty"
            loadedStyleName = "Carregando..."
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                    inputStream?.use { stream ->
                        val bytes = stream.readBytes()
                        val reader = StyReader()
                        val styFile = reader.read(bytes, fileName)
                        launch(Dispatchers.Main) {
                            loadedStyleName = fileName
                            viewModel.loadStyle(styFile)
                        }
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        loadedStyleName = "Erro ao carregar: ${e.message}"
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER - Style info and basic controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Motor de Tempo Real (100% Kotlin)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Estilo: $loadedStyleName", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) {
                            Text("Carregar STY")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Configurações MIDI")
                        }
                    }
                }
            }
        }

        // PLAYER CONTROLS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (isPlaying) viewModel.stop() else viewModel.play() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaying) Color.Red else Color(0xFF4CAF50)
                        ),
                        modifier = Modifier.size(100.dp, 60.dp)
                    ) {
                        Text(if (isPlaying) "STOP" else "START")
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BPM: ${bpm.toInt()}")
                        Row {
                            Button(onClick = { viewModel.setBpm(bpm - 1f) }) { Text("-") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { viewModel.setBpm(bpm + 1f) }) { Text("+") }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = { viewModel.toggleMetronome() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMetronomeOn) Color(0xFFE91E63) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (isMetronomeOn) "⏱ Click ON" else "⏱ Click OFF",
                                color = if (isMetronomeOn) Color.White else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        TextButton(
                            onClick = { showMetronomeDialog = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("⚙ Timbre/Peça", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // ARRANGEMENT BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArrangementButton("Intro A", StyleSection.IntroA, currentSection, viewModel)
                    ArrangementButton("Intro B", StyleSection.IntroB, currentSection, viewModel)
                    ArrangementButton("Intro C", StyleSection.IntroC, currentSection, viewModel)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArrangementButton("Main A", StyleSection.MainA, currentSection, viewModel)
                    ArrangementButton("Main B", StyleSection.MainB, currentSection, viewModel)
                    ArrangementButton("Main C", StyleSection.MainC, currentSection, viewModel)
                    ArrangementButton("Main D", StyleSection.MainD, currentSection, viewModel)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { viewModel.autoFill() }) {
                        Text("Auto Fill")
                    }
                    ArrangementButton("Break", StyleSection.Break, currentSection, viewModel)
                    ArrangementButton("Ending A", StyleSection.EndingA, currentSection, viewModel)
                    ArrangementButton("Ending B", StyleSection.EndingB, currentSection, viewModel)
                    ArrangementButton("Ending C", StyleSection.EndingC, currentSection, viewModel)
                }
            }
        }

        
        // CHORD DETECTOR & REALTIME STATE
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Detecção de Acordes (Left Hold / Chord Engine)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Acorde Atual:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        val chordStr = currentChord?.let { "${it.displayName} (${com.example.ui.validation.translateChordName(it.displayName)})" } ?: "Nenhum"
                        Text(
                            text = chordStr,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Virtual Chord Keyboard (for testing)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VirtualChordButton("C", listOf(48, 52, 55), viewModel)
                    VirtualChordButton("F", listOf(53, 57, 60), viewModel)
                    VirtualChordButton("G", listOf(55, 59, 62), viewModel)
                    VirtualChordButton("Am", listOf(57, 60, 64), viewModel)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Tick Musical Atual: $currentTick", style = MaterialTheme.typography.bodyMedium)
                
                // The progress visualizer is now purely a visual observer:
                LinearProgressIndicator(
                    progress = { (currentTick % 1920) / 1920f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Tracks 
        val style = viewModel.state.loadedStyle.get()
        if (style != null) {
            val sectionData = style.sections[currentSection]
            if (sectionData != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Trilhas do Estilo:", style = MaterialTheme.typography.titleSmall)
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    val tracksEntries = sectionData.tracks.entries.toList()
                    tracksEntries.forEachIndexed { idx, entry ->
                        val ch = entry.key
                        val track = entry.value
                        val name = when(ch) {
                            8, 9 -> "Drums"
                            10 -> "Bass"
                            11, 12, 13 -> "Chord"
                            14 -> "Pad"
                            15, 16 -> "Phrase"
                            else -> "Track"
                        }
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Text("Ch: ${ch + 1} (${track.eventCount} eventos)", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArrangementButton(
    label: String, 
    section: StyleSection, 
    currentSection: StyleSection,
    viewModel: ArrangerViewModel
) {
    val isSelected = currentSection == section
    val style = viewModel.state.loadedStyle.get()
    val existsInStyle = style?.sections?.containsKey(section) == true

    Button(
        onClick = { if (existsInStyle) viewModel.setSection(section) },
        enabled = existsInStyle,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(label)
    }
}

@Composable
fun VirtualChordButton(
    label: String,
    notes: List<Int>,
    viewModel: ArrangerViewModel
) {
    var isPressed by remember { mutableStateOf(false) }

    Button(
        onClick = { },
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    viewModel.engine.chordDetector.clearLeftHold() // Clear previous notes to avoid stacking
                    notes.forEach { viewModel.pressLeftNote(it) }
                    tryAwaitRelease()
                    isPressed = false
                    notes.forEach { viewModel.releaseLeftNote(it) }
                }
            )
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isPressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(label)
    }
}
