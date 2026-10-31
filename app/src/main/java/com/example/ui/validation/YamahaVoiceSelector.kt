package com.example.ui.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MidiState
import com.example.data.YamahaVoiceRepository
import com.example.domain.models.YamahaVoice
import com.example.domain.soundfont.model.SoundFontVoice
import com.example.ui.arranger.SoundFontPickerModal
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun InterchangeableVoiceSelector(
    label: String,
    channel: Int,
    isSoundFont: Boolean,
    soundFontVoice: SoundFontVoice?,
    yamahaVoiceName: String,
    yamahaMsb: Int,
    yamahaLsb: Int,
    yamahaPc: Int,
    onModeChange: (isSoundFont: Boolean) -> Unit,
    onYamahaSelected: (YamahaVoice) -> Unit,
    onSoundFontSelected: (SoundFontVoice) -> Unit,
    onOpenSoundFontDialog: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var expandedCategory by remember { mutableStateOf(false) }
    var expandedVoice by remember { mutableStateOf(false) }
    
    var selectedCategory by remember { mutableStateOf(YamahaVoiceRepository.categories.first()) }
    var selectedVoice by remember { mutableStateOf(YamahaVoiceRepository.getVoicesByCategory(selectedCategory).first()) }

    val sfVoices = MidiState.soundFontRepository.availableVoices
    val availableSfCategories = remember(sfVoices.size) {
        listOf("Todos") + sfVoices.map { it.category }.distinct().sorted()
    }
    var selectedSfCategory by remember { mutableStateOf("Todos") }
    var expandedSfCategory by remember { mutableStateOf(false) }
    var expandedSfVoice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .border(1.dp, if (isSoundFont) Color(0xFF7E57C2) else Color(0xFF00ACC1), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        // Header Row: Label & Interchangeable Switch Tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                Text(
                    text = if (isSoundFont) "Origem: SoundFont Interno (Stereo)" else "Origem: Yamaha PSR-S750 Hardware",
                    fontSize = 10.sp,
                    color = if (isSoundFont) Color(0xFFCE93D8) else Color(0xFF80DEEA)
                )
            }

            // Mode Selector Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { onModeChange(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isSoundFont) Color(0xFF00838F) else Color(0xFF333333)
                    ),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("🎹 PSR-S750", fontSize = 10.sp, color = Color.White)
                }

                Button(
                    onClick = { onModeChange(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSoundFont) Color(0xFF6A1B9A) else Color(0xFF333333)
                    ),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(
                        if (sfVoices.isNotEmpty()) "🎵 SoundFont (${sfVoices.size})" else "🎵 SoundFont",
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }
        }

        if (!isSoundFont) {
            // YAMAHA PSR-S750 MODE
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Button(
                        onClick = { expandedCategory = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(selectedCategory, fontSize = 12.sp, color = Color.White)
                    }
                    DropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        YamahaVoiceRepository.categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    val firstVoice = YamahaVoiceRepository.getVoicesByCategory(category).first()
                                    selectedVoice = firstVoice
                                    onYamahaSelected(firstVoice)
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }
                
                Box(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Button(
                        onClick = { expandedVoice = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(yamahaVoiceName.ifEmpty { selectedVoice.name }, fontSize = 12.sp, color = Color.White)
                    }
                    DropdownMenu(
                        expanded = expandedVoice,
                        onDismissRequest = { expandedVoice = false }
                    ) {
                        YamahaVoiceRepository.getVoicesByCategory(selectedCategory).forEach { voice ->
                            DropdownMenuItem(
                                text = { Text("${voice.name} (${voice.msb}-${voice.lsb}-${voice.pc})") },
                                onClick = {
                                    selectedVoice = voice
                                    onYamahaSelected(voice)
                                    expandedVoice = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "🎹 PSR-S750 • MSB: $yamahaMsb | LSB: $yamahaLsb | PRG: $yamahaPc",
                fontSize = 11.sp,
                color = Color(0xFF80DEEA),
                modifier = Modifier.padding(top = 2.dp)
            )

        } else {
            // SOUNDFONT MODE (SF2 / SFZ / KORG TRITON)
            var showSoundFontPicker by remember { mutableStateOf(false) }

            if (sfVoices.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Nenhum SoundFont indexado ainda.", fontSize = 11.sp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onOpenSoundFontDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8)),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text("📁 Importar Pasta com SoundFonts (Triton / SF2)", fontSize = 11.sp, color = Color.White)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Selected Voice Card with Audition
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF261D36)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = soundFontVoice?.name ?: "Nenhum selecionado",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Categoria: ${soundFontVoice?.category ?: "Geral"} • ${soundFontVoice?.fileType?.name ?: "SF2"} Stereo",
                                    fontSize = 10.sp,
                                    color = Color(0xFFCE93D8)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Quick audition button
                                if (soundFontVoice != null) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    MidiState.ensureSoundFontAudioActive(context)
                                                    MidiState.soundFontSynth.setChannelVoice(channel, soundFontVoice)
                                                    val previewNote = if (soundFontVoice.category.contains("bateria", ignoreCase = true) || soundFontVoice.category.contains("drum", ignoreCase = true)) 36 else 60
                                                    MidiState.soundFontSynth.noteOn(channel, previewNote, 105)
                                                    delay(420)
                                                    MidiState.soundFontSynth.noteOff(channel, previewNote)
                                                } catch (e: Exception) {}
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = Color(0xFF512DA8),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("🔊", fontSize = 13.sp)
                                    }
                                }

                                Button(
                                    onClick = { showSoundFontPicker = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("🔍 Buscar...", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${sfVoices.size} SoundFonts indexados (Korg Triton, Pianos, etc.)",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                        TextButton(
                            onClick = onOpenSoundFontDialog,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("📁 Pastas / Organizar", fontSize = 10.sp, color = Color(0xFFB39DDB))
                        }
                    }
                }

                if (showSoundFontPicker) {
                    SoundFontPickerModal(
                        title = "Selecionar SoundFont ($label)",
                        currentVoice = soundFontVoice,
                        previewChannel = channel,
                        onVoiceSelected = { sf ->
                            onSoundFontSelected(sf)
                            showSoundFontPicker = false
                        },
                        onDismiss = { showSoundFontPicker = false },
                        onOpenImporter = onOpenSoundFontDialog
                    )
                }
            }
        }
    }
}

// Backwards compatibility wrapper
@Composable
fun YamahaVoiceSelector(
    label: String,
    onVoiceSelected: (YamahaVoice) -> Unit
) {
    var selectedCat by remember { mutableStateOf(YamahaVoiceRepository.categories.first()) }
    var selectedVce by remember { mutableStateOf(YamahaVoiceRepository.getVoicesByCategory(selectedCat).first()) }

    InterchangeableVoiceSelector(
        label = label,
        channel = 0,
        isSoundFont = false,
        soundFontVoice = null,
        yamahaVoiceName = selectedVce.name,
        yamahaMsb = selectedVce.msb,
        yamahaLsb = selectedVce.lsb,
        yamahaPc = selectedVce.pc,
        onModeChange = {},
        onYamahaSelected = {
            selectedVce = it
            onVoiceSelected(it)
        },
        onSoundFontSelected = {},
        onOpenSoundFontDialog = {}
    )
}
