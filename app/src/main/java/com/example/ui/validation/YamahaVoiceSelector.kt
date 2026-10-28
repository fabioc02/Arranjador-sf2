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
            if (sfVoices.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Nenhum SoundFont indexado ainda.", fontSize = 11.sp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onOpenSoundFontDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8)),
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("📁 Abrir Pasta / Adicionar Triton SF2 / SFZ", fontSize = 11.sp, color = Color.White)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // Category Filter
                    Box(modifier = Modifier.weight(0.9f).padding(end = 4.dp)) {
                        Button(
                            onClick = { expandedSfCategory = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A148C)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(selectedSfCategory, fontSize = 12.sp, color = Color.White)
                        }
                        DropdownMenu(
                            expanded = expandedSfCategory,
                            onDismissRequest = { expandedSfCategory = false }
                        ) {
                            availableSfCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        selectedSfCategory = cat
                                        val firstSf = if (cat == "Todos") sfVoices.first() else sfVoices.firstOrNull { it.category == cat } ?: sfVoices.first()
                                        onSoundFontSelected(firstSf)
                                        expandedSfCategory = false
                                    }
                                )
                            }
                        }
                    }

                    // Instrument Selector
                    val filteredVoices = if (selectedSfCategory == "Todos") sfVoices else sfVoices.filter { it.category == selectedSfCategory }
                    Box(modifier = Modifier.weight(1.3f).padding(start = 4.dp)) {
                        Button(
                            onClick = { expandedSfVoice = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF311B92)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            val displayName = soundFontVoice?.name ?: filteredVoices.firstOrNull()?.name ?: "Selecionar Timbre"
                            Text(displayName, fontSize = 12.sp, color = Color.White)
                        }
                        DropdownMenu(
                            expanded = expandedSfVoice,
                            onDismissRequest = { expandedSfVoice = false }
                        ) {
                            filteredVoices.forEach { sf ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(sf.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("${sf.category} • ${sf.fileType.name} Stereo • ${sf.fileSizeFormatted}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                    },
                                    onClick = {
                                        onSoundFontSelected(sf)
                                        expandedSfVoice = false
                                    }
                                )
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
                    val currentName = soundFontVoice?.name ?: "Nenhum"
                    val currentCat = soundFontVoice?.category ?: ""
                    Text(
                        "🎵 SoundFont: $currentName ($currentCat)",
                        fontSize = 11.sp,
                        color = Color(0xFFCE93D8)
                    )
                    TextButton(
                        onClick = onOpenSoundFontDialog,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("📁 Pastas", fontSize = 10.sp, color = Color(0xFFB39DDB))
                    }
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
