package com.example.ui.arranger

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.MidiState
import com.example.domain.midi_core.MidiDispatcher
import com.example.domain.soundfont.model.SoundFontVoice
import com.example.domain.soundfont.repository.SoundFontCategoryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Free-search, filterable SoundFont browser modal.
 * Allows quick auditioning, instant search across 70+ Triton / GM soundfonts,
 * category filtering, and direct user-customized category re-indexing.
 */
@Composable
fun SoundFontPickerModal(
    title: String = "Selecionar SoundFont",
    currentVoice: SoundFontVoice?,
    previewChannel: Int = 0,
    onVoiceSelected: (SoundFontVoice) -> Unit,
    onDismiss: () -> Unit,
    onOpenImporter: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }
    var voiceToReclassify by remember { mutableStateOf<SoundFontVoice?>(null) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }

    val allVoices = MidiState.soundFontRepository.availableVoices
    val allCategories = remember(allVoices.size, voiceToReclassify) {
        listOf("Todos") + SoundFontCategoryManager.getAllCategories()
    }

    val filteredVoices = remember(allVoices.toList(), searchQuery, selectedCategory) {
        allVoices.filter { voice ->
            val matchesCategory = (selectedCategory == "Todos") || voice.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                voice.name.lowercase().contains(q) ||
                voice.category.lowercase().contains(q) ||
                voice.filePath.lowercase().contains(q) ||
                "${voice.bank}-${voice.program}".contains(q)
            }
            matchesCategory && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = Color(0xFF18181B),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Total: ${allVoices.size} SoundFonts indexados (${filteredVoices.size} visíveis)",
                            fontSize = 11.sp,
                            color = Color(0xFFB0BEC5)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("🔍 Buscar por nome, Triton, Rhodes, kit...", fontSize = 13.sp, color = Color.Gray)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar busca", tint = Color.LightGray)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF7E57C2),
                        unfocusedBorderColor = Color(0xFF424242),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF212124),
                        unfocusedContainerColor = Color(0xFF212124)
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Chips Row (Horizontal Scroll)
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    allCategories.forEach { category ->
                        val count = if (category == "Todos") allVoices.size else allVoices.count { it.category.equals(category, ignoreCase = true) }
                        val isSelected = selectedCategory == category

                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    if (count > 0 && category != "Todos") "$category ($count)" else category,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF6A1B9A),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF27272A),
                                labelColor = Color(0xFFD4D4D8)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color(0xFFBA68C8) else Color(0xFF3F3F46)
                            )
                        )
                    }

                    // Button to add new custom category
                    OutlinedButton(
                        onClick = { showNewCategoryDialog = true },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCE93D8)),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF8E24AA)))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Nova Categoria", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nova Categoria", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // SoundFont List
                if (filteredVoices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Nenhum SoundFont encontrado para este filtro.",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                            if (onOpenImporter != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onOpenImporter,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8))
                                ) {
                                    Text("📁 Importar Novos SoundFonts / SF2", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredVoices, key = { it.id }) { voice ->
                            val isSelected = currentVoice?.id == voice.id
                            val isLoadedInRam = MidiState.soundFontRepository.isVoiceLoaded(voice)

                            SoundFontVoiceCard(
                                voice = voice,
                                isSelected = isSelected,
                                isLoadedInRam = isLoadedInRam,
                                onAudition = {
                                    coroutineScope.launch {
                                        try {
                                            MidiState.ensureSoundFontAudioActive(context)
                                            MidiState.soundFontSynth.setChannelVoice(previewChannel, voice)
                                            val auditionNote = if (voice.category.contains("bateria", ignoreCase = true) || voice.category.contains("drum", ignoreCase = true)) 36 else 60
                                            MidiState.soundFontSynth.noteOn(previewChannel, auditionNote, 105)
                                            delay(420)
                                            MidiState.soundFontSynth.noteOff(previewChannel, auditionNote)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                },
                                onSelect = {
                                    onVoiceSelected(voice)
                                    onDismiss()
                                },
                                onReclassify = {
                                    voiceToReclassify = voice
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenImporter != null) {
                        TextButton(
                            onClick = onOpenImporter,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCE93D8))
                        ) {
                            Text("📁 Importar Pasta / Arquivos SF2", fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F3F46))
                    ) {
                        Text("Fechar", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }

    // Reclassify Dialog
    voiceToReclassify?.let { targetVoice ->
        ReclassifyCategoryDialog(
            voice = targetVoice,
            categories = SoundFontCategoryManager.getAllCategories(),
            onDismiss = { voiceToReclassify = null },
            onCategoryAssigned = { newCat ->
                SoundFontCategoryManager.assignCategory(context, targetVoice, newCat)
                MidiState.soundFontRepository.updateVoiceCategory(targetVoice.id, newCat)
                voiceToReclassify = null
                Toast.makeText(context, "\"${targetVoice.name}\" reclassificado para: $newCat", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Add New Category Dialog
    if (showNewCategoryDialog) {
        AddNewCategoryDialog(
            onDismiss = { showNewCategoryDialog = false },
            onAdd = { newCatName ->
                SoundFontCategoryManager.addCustomCategory(context, newCatName)
                selectedCategory = newCatName
                showNewCategoryDialog = false
                Toast.makeText(context, "Categoria \"$newCatName\" criada!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun SoundFontVoiceCard(
    voice: SoundFontVoice,
    isSelected: Boolean,
    isLoadedInRam: Boolean,
    onAudition: () -> Unit,
    onSelect: () -> Unit,
    onReclassify: () -> Unit
) {
    val categoryColor = when (voice.category) {
        "Piano" -> Color(0xFF1976D2)
        "E.Piano" -> Color(0xFF7B1FA2)
        "Korg Triton" -> Color(0xFFD81B60)
        "Acordeon" -> Color(0xFFE65100)
        "Órgão" -> Color(0xFF00796B)
        "Violão / Guitarra" -> Color(0xFF388E3C)
        "Baixo" -> Color(0xFF5D4037)
        "Cordas" -> Color(0xFF512DA8)
        "Metais" -> Color(0xFFF57C00)
        "Sax / Sopros" -> Color(0xFF0288D1)
        "Pad / Coro" -> Color(0xFF0097A7)
        "Sintetizador" -> Color(0xFFC2185B)
        "Baterias" -> Color(0xFFC62828)
        else -> Color(0xFF616161)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2E1045) else Color(0xFF202024)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isSelected) Color(0xFFBA68C8) else Color(0xFF2D2D32)
            ),
            width = if (isSelected) 1.5.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        voice.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF4CAF50),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "SELECIONADO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Badge with clickable reclassify affordance
                    Surface(
                        color = categoryColor.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, categoryColor.copy(alpha = 0.6f)),
                        modifier = Modifier.clickable(onClick = onReclassify)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                voice.category,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("✎", fontSize = 9.sp, color = categoryColor)
                        }
                    }

                    Text(
                        "${voice.fileType.name} Stereo • ${voice.fileSizeFormatted}",
                        fontSize = 10.sp,
                        color = Color(0xFFA1A1AA)
                    )

                    Surface(
                        color = if (isLoadedInRam) Color(0xFF1B5E20) else Color(0xFF37474F),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isLoadedInRam) "⚡ RAM" else "💾 Sob Demanda",
                            fontSize = 9.sp,
                            color = if (isLoadedInRam) Color(0xFFA5D6A7) else Color(0xFFCFD8DC),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons: Audition and Reclassify
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Audition Preview Button
                FilledTonalIconButton(
                    onClick = onAudition,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF4A148C),
                        contentColor = Color.White
                    )
                ) {
                    Text("🔊", fontSize = 14.sp)
                }

                // Reclassify Category Button
                OutlinedIconButton(
                    onClick = onReclassify,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        contentColor = Color(0xFFE1BEE7)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7B1FA2))
                ) {
                    Text("🏷️", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ReclassifyCategoryDialog(
    voice: SoundFontVoice,
    categories: List<String>,
    onDismiss: () -> Unit,
    onCategoryAssigned: (String) -> Unit
) {
    var selectedCat by remember { mutableStateOf(voice.category) }
    var customText by remember { mutableStateOf("") }
    var isCustomMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Indexar Categoria", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(voice.name, fontSize = 12.sp, color = Color(0xFFCE93D8))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Selecione a categoria correta para este SoundFont ou digite uma nova categoria personalizada:",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Category List
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(categories.filter { it != "Todos" }) { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCat = cat
                                    isCustomMode = false
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (!isCustomMode && selectedCat == cat),
                                onClick = {
                                    selectedCat = cat
                                    isCustomMode = false
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(cat, fontSize = 13.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom category field
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isCustomMode,
                        onClick = { isCustomMode = true }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Outra / Nova Categoria:", fontSize = 12.sp, color = Color.White)
                }

                if (isCustomMode) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        placeholder = { Text("Ex: Korg Triton, Vintage Keys, etc.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalCategory = if (isCustomMode && customText.isNotBlank()) customText.trim() else selectedCat
                    onCategoryAssigned(finalCategory)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun AddNewCategoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var newCatName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Criar Nova Categoria", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column {
                Text("Digite o nome da nova categoria (ex: Korg Triton, Acordeon Nordestino, Synth Leads):", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    singleLine = true,
                    placeholder = { Text("Nome da Categoria") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newCatName.isNotBlank()) {
                        onAdd(newCatName.trim())
                    }
                },
                enabled = newCatName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
            ) {
                Text("Adicionar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
