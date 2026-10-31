package com.example.ui.arranger

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.InstrumentDatabase
import com.example.MidiState
import com.example.domain.arranger.ArrangerViewModel
import com.example.domain.arranger.ChannelVoiceMode
import com.example.domain.models.YamahaVoice
import com.example.domain.soundfont.model.SoundFontType
import com.example.domain.soundfont.model.SoundFontVoice
import com.example.domain.soundfont.model.SoundOutputEngine
import com.example.domain.soundfont.repository.SoundFontStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicesScreen(
    viewModel: ArrangerViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Map between InstrumentDatabase.YamahaVoice and com.example.domain.models.YamahaVoice
    val drumKits = remember { InstrumentDatabase.drumKits.map { YamahaVoice(it.name, it.msb, it.lsb, it.pc) } }
    val instruments = remember { InstrumentDatabase.instruments.map { YamahaVoice(it.name, it.msb, it.lsb, it.pc) } }

    val channels = listOf(
        Pair(0, "Right 1 (Main)"),
        Pair(1, "Right 2 (Layer)"),
        Pair(2, "Left (Split)"),
        Pair(8, "Rhythm Sub (Bateria 1)"),
        Pair(9, "Rhythm Main (Bateria 2)"),
        Pair(10, "Bass (Baixo)"),
        Pair(11, "Chord 1 (Acorde 1)"),
        Pair(12, "Chord 2 (Acorde 2)"),
        Pair(13, "Pad (Fundo)"),
        Pair(14, "Phrase 1 (Frase 1)"),
        Pair(15, "Phrase 2 (Frase 2)")
    )

    var selectedChannelIndex by remember { mutableStateOf(9) } // Default to Rhythm Main
    var triggerRecompose by remember { mutableStateOf(0) }
    val dummy = triggerRecompose

    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var showDrumMapDialog by remember { mutableStateOf(false) }

    // Mode tab for selecting instruments: 0 = Yamaha PSR-S750, 1 = SoundFont (Korg Triton VST)
    val initialVoiceMode = viewModel.voiceManager.channelVoiceMode[selectedChannelIndex] ?: ChannelVoiceMode.YAMAHA_HARDWARE
    var selectedSourceTab by remember { mutableStateOf(if (initialVoiceMode == ChannelVoiceMode.SOUNDFONT) 1 else 0) }

    // Status messages for async imports
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    // File Pickers
    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { treeUri ->
            isImporting = true
            importStatusMessage = "Lendo pasta e arquivos de SoundFont..."
            scope.launch(Dispatchers.IO) {
                try {
                    val count = SoundFontStorageManager.importFolderTree(context, treeUri) { fileName, current ->
                        launch(Dispatchers.Main) {
                            importStatusMessage = "Indexando: $fileName ($current)..."
                        }
                    }
                    launch(Dispatchers.Main) {
                        isImporting = false
                        importStatusMessage = "$count arquivos indexados com sucesso!"
                        triggerRecompose++
                        Toast.makeText(context, "$count SoundFonts indexados na biblioteca!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        isImporting = false
                        importStatusMessage = "Erro ao importar: ${e.message}"
                        Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val openFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isImporting = true
            importStatusMessage = "Importando ${uris.size} arquivo(s)..."
            scope.launch(Dispatchers.IO) {
                try {
                    val count = SoundFontStorageManager.importFiles(context, uris) { fileName, curr, tot ->
                        launch(Dispatchers.Main) {
                            importStatusMessage = "Importando ($curr/$tot): $fileName"
                        }
                    }
                    launch(Dispatchers.Main) {
                        isImporting = false
                        importStatusMessage = "$count SoundFont(s) importado(s)!"
                        triggerRecompose++
                        Toast.makeText(context, "$count SoundFont(s) importado(s)!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        isImporting = false
                        importStatusMessage = "Erro ao importar arquivos: ${e.message}"
                    }
                }
            }
        }
    }

    if (showDiagnosticDialog) {
        DiagnosticDialog(viewModel = viewModel, onDismiss = { showDiagnosticDialog = false })
    }

    if (showDrumMapDialog) {
        DrumPiecesDialog(viewModel = viewModel, onDismiss = { showDrumMapDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mixer & Timbres") },
                actions = {
                    // Sound output indicator / switch button
                    val currentOut = MidiState.soundOutputEngine
                    AssistChip(
                        onClick = {
                            val nextOut = when (currentOut) {
                                SoundOutputEngine.YAMAHA_HARDWARE -> SoundOutputEngine.SOUNDFONT_INTERNAL
                                SoundOutputEngine.SOUNDFONT_INTERNAL -> SoundOutputEngine.HYBRID_BOTH
                                SoundOutputEngine.HYBRID_BOTH -> SoundOutputEngine.YAMAHA_HARDWARE
                            }
                            MidiState.setSoundOutput(nextOut, context)
                            triggerRecompose++
                        },
                        label = {
                            Text(
                                text = "Saída: ${currentOut.shortName}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    TextButton(onClick = { showDiagnosticDialog = true }) {
                        Text("Diagnóstico")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Text(
                "Mapeamento & Gerenciamento de Sons",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Configure timbres do Yamaha PSR-S750 ou SoundFonts Korg Triton em stereo de alta qualidade.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Channel selection tabs
            Text("Selecione a Track / Canal:", style = MaterialTheme.typography.labelMedium)
            ScrollableTabRow(
                selectedTabIndex = channels.indexOfFirst { it.first == selectedChannelIndex }.coerceAtLeast(0),
                edgePadding = 0.dp
            ) {
                channels.forEach { (chIndex, chName) ->
                    Tab(
                        selected = selectedChannelIndex == chIndex,
                        onClick = {
                            selectedChannelIndex = chIndex
                            val mode = viewModel.voiceManager.channelVoiceMode[chIndex] ?: ChannelVoiceMode.YAMAHA_HARDWARE
                            selectedSourceTab = if (mode == ChannelVoiceMode.SOUNDFONT) 1 else 0
                        },
                        text = { Text(chName, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val isDrumChannel = selectedChannelIndex == 8 || selectedChannelIndex == 9
            val currentMode = viewModel.voiceManager.channelVoiceMode[selectedChannelIndex] ?: ChannelVoiceMode.YAMAHA_HARDWARE
            val currentYamahaVoice = viewModel.voiceManager.channelRemap[selectedChannelIndex]
            val currentSoundFontVoice = viewModel.voiceManager.channelSoundFontRemap[selectedChannelIndex]

            // Main Mixer & Instrument Card
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxSize()
                ) {
                    // Active Voice status & action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Instrumento Ativo no Canal", style = MaterialTheme.typography.titleSmall)
                            when (currentMode) {
                                ChannelVoiceMode.SOUNDFONT -> {
                                    if (currentSoundFontVoice != null) {
                                        val isLoaded = MidiState.soundFontRepository.isVoiceLoaded(currentSoundFontVoice)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "🎵 SoundFont: ${currentSoundFontVoice.name}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = if (isLoaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (isLoaded) "⚡ Na RAM" else "💾 Sob Demanda",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            "${currentSoundFontVoice.category} • ${currentSoundFontVoice.fileType.name} Stereo • ${currentSoundFontVoice.fileSizeFormatted}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text("SoundFont (Não atribuído)", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                ChannelVoiceMode.YAMAHA_HARDWARE -> {
                                    if (currentYamahaVoice != null) {
                                        Text(
                                            "🎹 Yamaha: ${currentYamahaVoice.name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "MSB: ${currentYamahaVoice.msb} • LSB: ${currentYamahaVoice.lsb} • PC: ${currentYamahaVoice.pc}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text("Padrão do Estilo (.sty)", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isDrumChannel) {
                                Button(
                                    onClick = { showDrumMapDialog = true },
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text("Afinação XG")
                                }
                            }

                            if (currentYamahaVoice != null || currentSoundFontVoice != null) {
                                Button(
                                    onClick = {
                                        viewModel.voiceManager.clearMapping(selectedChannelIndex)
                                        triggerRecompose++
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Resetar")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Mixer Sliders (Volume, Pan, Reverb Send, Chorus Send)
                    val currentVol = viewModel.voiceManager.channelVolumes[selectedChannelIndex] ?: 100
                    val currentPan = viewModel.voiceManager.channelPans[selectedChannelIndex] ?: 64
                    val currentRev = viewModel.voiceManager.channelReverbs[selectedChannelIndex] ?: 40
                    val currentCho = viewModel.voiceManager.channelChoruses[selectedChannelIndex] ?: 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Volume Slider
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vol: $currentVol", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = currentVol.toFloat(),
                                onValueChange = {
                                    val newVal = it.toInt()
                                    viewModel.voiceManager.setVolume(selectedChannelIndex, newVal)
                                    com.example.domain.midi_core.MidiDispatcher.sendControlChange(selectedChannelIndex, 7, newVal, 0L)
                                    triggerRecompose++
                                },
                                valueRange = 0f..127f
                            )
                        }

                        // Pan Slider
                        Column(modifier = Modifier.weight(1f)) {
                            val panStr = if (currentPan < 64) "L${64 - currentPan}" else if (currentPan > 64) "R${currentPan - 64}" else "C"
                            Text("Pan: $panStr", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = currentPan.toFloat(),
                                onValueChange = {
                                    val newVal = it.toInt()
                                    viewModel.voiceManager.setPan(selectedChannelIndex, newVal)
                                    com.example.domain.midi_core.MidiDispatcher.sendControlChange(selectedChannelIndex, 10, newVal, 0L)
                                    triggerRecompose++
                                },
                                valueRange = 0f..127f
                            )
                        }

                        // Reverb Send (CC 91)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reverb: $currentRev", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = currentRev.toFloat(),
                                onValueChange = {
                                    val newVal = it.toInt()
                                    viewModel.voiceManager.setReverb(selectedChannelIndex, newVal)
                                    com.example.domain.midi_core.MidiDispatcher.sendControlChange(selectedChannelIndex, 91, newVal, 0L)
                                    triggerRecompose++
                                },
                                valueRange = 0f..127f
                            )
                        }

                        // Chorus Send (CC 93)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chorus: $currentCho", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = currentCho.toFloat(),
                                onValueChange = {
                                    val newVal = it.toInt()
                                    viewModel.voiceManager.setChorus(selectedChannelIndex, newVal)
                                    com.example.domain.midi_core.MidiDispatcher.sendControlChange(selectedChannelIndex, 93, newVal, 0L)
                                    triggerRecompose++
                                },
                                valueRange = 0f..127f
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Source Selection Tabs (Separation between Yamaha PSR-S750 and SoundFonts)
                    TabRow(
                        selectedTabIndex = selectedSourceTab,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    ) {
                        Tab(
                            selected = selectedSourceTab == 0,
                            onClick = { selectedSourceTab = 0 },
                            text = { Text("🎹 Timbres Yamaha PSR-S750") }
                        )
                        Tab(
                            selected = selectedSourceTab == 1,
                            onClick = { selectedSourceTab = 1 },
                            text = { Text("🎵 SoundFonts (Korg Triton VST)") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // VIEW 1: YAMAHA PSR-S750 BROWSER
                    if (selectedSourceTab == 0) {
                        YamahaBrowserView(
                            isDrumChannel = isDrumChannel,
                            instruments = instruments,
                            drumKits = drumKits,
                            currentYamahaVoice = currentYamahaVoice,
                            onSelectVoice = { voice ->
                                viewModel.voiceManager.setMapping(selectedChannelIndex, voice)
                                MidiState.sendProgramChange(selectedChannelIndex, voice.msb, voice.lsb, voice.pc)
                                triggerRecompose++
                            }
                        )
                    } else {
                        // VIEW 2: SOUNDFONT (KORG TRITON) BROWSER
                        SoundFontBrowserView(
                            selectedChannelIndex = selectedChannelIndex,
                            currentSoundFontVoice = currentSoundFontVoice,
                            isImporting = isImporting,
                            importStatusMessage = importStatusMessage,
                            onOpenFolder = { openFolderLauncher.launch(null) },
                            onOpenFiles = { openFilesLauncher.launch(arrayOf("*/*")) },
                            onSelectSoundFont = { sfVoice ->
                                viewModel.voiceManager.setSoundFontMapping(selectedChannelIndex, sfVoice)
                                MidiState.ensureSoundFontAudioActive(context)
                                if (MidiState.soundOutputEngine == SoundOutputEngine.YAMAHA_HARDWARE) {
                                    MidiState.setSoundOutput(SoundOutputEngine.SOUNDFONT_INTERNAL, context)
                                }
                                triggerRecompose++
                                Toast.makeText(context, "Canal ${selectedChannelIndex + 1} mapeado para ${sfVoice.name}", Toast.LENGTH_SHORT).show()
                            },
                            onClearRam = {
                                MidiState.soundFontRepository.clearCache()
                                triggerRecompose++
                                Toast.makeText(context, "Memória RAM de amostras liberada!", Toast.LENGTH_SHORT).show()
                            },
                            onRescan = {
                                scope.launch(Dispatchers.IO) {
                                    SoundFontStorageManager.scanAll(context)
                                    launch(Dispatchers.Main) {
                                        triggerRecompose++
                                        Toast.makeText(context, "Biblioteca re-escaneada!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YamahaBrowserView(
    isDrumChannel: Boolean,
    instruments: List<YamahaVoice>,
    drumKits: List<YamahaVoice>,
    currentYamahaVoice: YamahaVoice?,
    onSelectVoice: (YamahaVoice) -> Unit
) {
    val voiceList = if (isDrumChannel) drumKits else instruments
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }

    val categories = if (isDrumChannel) listOf("Todos") else listOf(
        "Todos", "Piano", "E.Piano", "Órgão", "Guitarra", "Baixo", "Cordas", "Metais", "Sax", "Pad / Coro", "Sintetizador"
    )

    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Buscar timbre Yamaha...") },
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        singleLine = true
    )

    if (!isDrumChannel) {
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
            edgePadding = 0.dp,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            categories.forEach { cat ->
                Tab(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    text = { Text(cat, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }
    }

    val filteredList = voiceList.filter { voice ->
        (searchQuery.isEmpty() || voice.name.contains(searchQuery, ignoreCase = true)) &&
        (selectedCategory == "Todos" || when (selectedCategory) {
            "Piano" -> voice.name.contains("Piano", true) || voice.name.contains("Grand", true)
            "E.Piano" -> voice.name.contains("EP", true) || voice.name.contains("Clavi", true) || voice.name.contains("Tine", true)
            "Órgão" -> voice.name.contains("Organ", true) || voice.name.contains("Org", true) || voice.name.contains("Draw", true) || voice.name.contains("Bars", true)
            "Guitarra" -> voice.name.contains("Guitar", true) || voice.name.contains("Gtr", true)
            "Baixo" -> voice.name.contains("Bass", true)
            "Cordas" -> voice.name.contains("String", true) || voice.name.contains("Violin", true) || voice.name.contains("Orch", true)
            "Metais" -> voice.name.contains("Brass", true) || voice.name.contains("Trumpet", true) || voice.name.contains("Trombone", true) || voice.name.contains("Horn", true)
            "Sax" -> voice.name.contains("Sax", true)
            "Pad / Coro" -> voice.name.contains("Pad", true) || voice.name.contains("Choir", true) || voice.name.contains("Vox", true)
            "Sintetizador" -> voice.name.contains("Synth", true) || voice.name.contains("Saw", true) || voice.name.contains("Lead", true)
            else -> true
        })
    }

    Text("Timbres Yamaha (${filteredList.size}):", style = MaterialTheme.typography.labelSmall)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(filteredList) { voice ->
            val isSelected = currentYamahaVoice?.name == voice.name
            OutlinedButton(
                onClick = { onSelectVoice(voice) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(voice.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    Text(
                        "${voice.msb}-${voice.lsb}-${voice.pc}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundFontBrowserView(
    selectedChannelIndex: Int,
    currentSoundFontVoice: SoundFontVoice?,
    isImporting: Boolean,
    importStatusMessage: String?,
    onOpenFolder: () -> Unit,
    onOpenFiles: () -> Unit,
    onSelectSoundFont: (SoundFontVoice) -> Unit,
    onClearRam: () -> Unit,
    onRescan: () -> Unit
) {
    val repository = MidiState.soundFontRepository
    val allVoices = repository.availableVoices

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }

    // Actions & Tools Bar
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onOpenFolder,
            modifier = Modifier.weight(1.2f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("📁 Abrir Pasta (Triton)")
        }

        OutlinedButton(
            onClick = onOpenFiles,
            modifier = Modifier.weight(0.9f),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Adicionar", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("SF2/SFZ")
        }

        IconButton(onClick = onRescan) {
            Icon(Icons.Default.Refresh, contentDescription = "Re-escanear")
        }

        IconButton(onClick = onClearRam) {
            Icon(Icons.Default.Delete, contentDescription = "Liberar RAM", tint = MaterialTheme.colorScheme.error)
        }
    }

    // RAM & Performance Status Banner
    val loadedCount = repository.getLoadedCount()
    val loadedMb = repository.getLoadedRamMb()
    val catalogCount = repository.getCatalogCount()

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "⚡ RAM: $loadedCount ativo(s) (${String.format(java.util.Locale.US, "%.1f", loadedMb)} MB) | 💾 Indexados: ${allVoices.size} (Zero RAM)",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "Sob Demanda",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (isImporting && importStatusMessage != null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
        Text(
            text = importStatusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }

    // Empty state instructions
    if (allVoices.isEmpty() && !isImporting) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Informação",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Nenhum SoundFont (.sf2 / .sfz) carregado ainda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Clique no botão '📁 Abrir Pasta (Triton)' acima para selecionar a pasta com seus instrumentos do VST Triton no celular/Drive.\n\nO app indexará os nomes dos instrumentos e pastas instantaneamente sem carregar dados pesados de áudio na RAM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpenFolder) {
                    Text("Selecionar Pasta com SF2 / SFZ")
                }
            }
        }
        return
    }

    // Dynamic Categories
    val categories = remember(allVoices.size) {
        listOf("Todos") + com.example.domain.soundfont.repository.SoundFontCategoryManager.getAllCategories()
    }
    var voiceToReclassifyInVoices by remember { mutableStateOf<SoundFontVoice?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Buscar no banco Triton...") },
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        singleLine = true
    )

    if (categories.size > 1) {
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
            edgePadding = 0.dp,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            categories.forEach { cat ->
                Tab(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    text = { Text(cat, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }
    }

    val filteredList = allVoices.filter { voice ->
        (searchQuery.isEmpty() || voice.name.contains(searchQuery, ignoreCase = true) || voice.category.contains(searchQuery, ignoreCase = true)) &&
        (selectedCategory == "Todos" || voice.category.equals(selectedCategory, ignoreCase = true))
    }

    Text("Timbres SoundFont (${filteredList.size}):", style = MaterialTheme.typography.labelSmall)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(filteredList) { voice ->
            val isSelected = currentSoundFontVoice?.id == voice.id
            val isLoaded = repository.isVoiceLoaded(voice)

            OutlinedButton(
                onClick = { onSelectSoundFont(voice) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = voice.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${voice.category} • ${voice.fileType.name} ${if (voice.isStereo) "Stereo" else "Mono"} • ${voice.fileSizeFormatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        MidiState.ensureSoundFontAudioActive(context)
                                        MidiState.soundFontSynth.setChannelVoice(selectedChannelIndex, voice)
                                        val auditionNote = if (voice.category.contains("bateria", ignoreCase = true) || voice.category.contains("drum", ignoreCase = true)) 36 else 60
                                        MidiState.soundFontSynth.noteOn(selectedChannelIndex, auditionNote, 105)
                                        kotlinx.coroutines.delay(420)
                                        MidiState.soundFontSynth.noteOff(selectedChannelIndex, auditionNote)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("🔊", fontSize = 13.sp)
                        }

                        IconButton(
                            onClick = { voiceToReclassifyInVoices = voice },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("🏷️", fontSize = 13.sp)
                        }

                        if (isLoaded) {
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "⚡ RAM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    voiceToReclassifyInVoices?.let { targetVoice ->
        ReclassifyCategoryDialog(
            voice = targetVoice,
            categories = com.example.domain.soundfont.repository.SoundFontCategoryManager.getAllCategories(),
            onDismiss = { voiceToReclassifyInVoices = null },
            onCategoryAssigned = { newCat ->
                com.example.domain.soundfont.repository.SoundFontCategoryManager.assignCategory(context, targetVoice, newCat)
                repository.updateVoiceCategory(targetVoice.id, newCat)
                voiceToReclassifyInVoices = null
                Toast.makeText(context, "\"${targetVoice.name}\" reclassificado para: $newCat", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
