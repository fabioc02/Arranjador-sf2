package com.example.ui.arranger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.metronome.MetronomeConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(MetronomeConfig.enabled) }
    var selectedPresetId by remember { mutableStateOf(MetronomeConfig.selectedPresetId) }
    var downbeatNote by remember { mutableIntStateOf(MetronomeConfig.downbeatNote) }
    var upbeatNote by remember { mutableIntStateOf(MetronomeConfig.upbeatNote) }
    var volume by remember { mutableIntStateOf(MetronomeConfig.volume) }
    var accentVel by remember { mutableIntStateOf(MetronomeConfig.accentVelocity) }
    var normalVel by remember { mutableIntStateOf(MetronomeConfig.normalVelocity) }

    Dialog(
        onDismissRequest = {
            MetronomeConfig.save(context)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Configuração do Metrônomo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            MetronomeConfig.enabled = it
                            MetronomeConfig.save(context)
                        }
                    )
                }

                Text(
                    "Funciona no gerador do Yamaha PSR-S750 e nos SoundFonts com suporte a peças de bateria personalizáveis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Presets
                Text(
                    "Timbre do Click (Peça de Bateria):",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                MetronomeConfig.PRESETS.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedPresetId == preset.id),
                            onClick = {
                                selectedPresetId = preset.id
                                if (preset.id != "custom") {
                                    downbeatNote = preset.downbeatNote
                                    upbeatNote = preset.upbeatNote
                                    MetronomeConfig.applyPreset(preset)
                                    MetronomeConfig.save(context)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(preset.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Custom note picker if Custom selected
                if (selectedPresetId == "custom") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Peça do Tempo 1 (Acento / Downbeat):", style = MaterialTheme.typography.labelMedium)
                            var expandedDownbeat by remember { mutableStateOf(false) }
                            val currentDownbeatName = MetronomeConfig.AVAILABLE_DRUM_PIECES.find { it.first == downbeatNote }?.second ?: "Nota $downbeatNote"

                            OutlinedButton(
                                onClick = { expandedDownbeat = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$downbeatNote - $currentDownbeatName")
                            }
                            DropdownMenu(
                                expanded = expandedDownbeat,
                                onDismissRequest = { expandedDownbeat = false }
                            ) {
                                MetronomeConfig.AVAILABLE_DRUM_PIECES.forEach { (note, name) ->
                                    DropdownMenuItem(
                                        text = { Text("$note: $name") },
                                        onClick = {
                                            downbeatNote = note
                                            MetronomeConfig.downbeatNote = note
                                            MetronomeConfig.save(context)
                                            expandedDownbeat = false
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Peça dos Demais Tempos (Upbeat):", style = MaterialTheme.typography.labelMedium)
                            var expandedUpbeat by remember { mutableStateOf(false) }
                            val currentUpbeatName = MetronomeConfig.AVAILABLE_DRUM_PIECES.find { it.first == upbeatNote }?.second ?: "Nota $upbeatNote"

                            OutlinedButton(
                                onClick = { expandedUpbeat = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$upbeatNote - $currentUpbeatName")
                            }
                            DropdownMenu(
                                expanded = expandedUpbeat,
                                onDismissRequest = { expandedUpbeat = false }
                            ) {
                                MetronomeConfig.AVAILABLE_DRUM_PIECES.forEach { (note, name) ->
                                    DropdownMenuItem(
                                        text = { Text("$note: $name") },
                                        onClick = {
                                            upbeatNote = note
                                            MetronomeConfig.upbeatNote = note
                                            MetronomeConfig.save(context)
                                            expandedUpbeat = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Volume & Velocities
                Text("Volume & Dinâmica do Click:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Volume Geral: $volume", modifier = Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = volume.toFloat(),
                        onValueChange = {
                            volume = it.toInt()
                            MetronomeConfig.volume = volume
                            MetronomeConfig.save(context)
                        },
                        valueRange = 0f..127f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Velocidade Tempo 1: $accentVel", modifier = Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = accentVel.toFloat(),
                        onValueChange = {
                            accentVel = it.toInt()
                            MetronomeConfig.accentVelocity = accentVel
                            MetronomeConfig.save(context)
                        },
                        valueRange = 1f..127f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Velocidade Demais: $normalVel", modifier = Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = normalVel.toFloat(),
                        onValueChange = {
                            normalVel = it.toInt()
                            MetronomeConfig.normalVelocity = normalVel
                            MetronomeConfig.save(context)
                        },
                        valueRange = 1f..127f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Test click buttons
                Text("Teste de Áudio Imediato:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { MetronomeConfig.testClick(isDownbeat = true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔊 Testar Acento")
                    }
                    OutlinedButton(
                        onClick = { MetronomeConfig.testClick(isDownbeat = false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔊 Testar Normal")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(16.dp))

                // Close Button
                Button(
                    onClick = {
                        MetronomeConfig.save(context)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salvar e Fechar")
                }
            }
        }
    }
}
