package com.example.ui.arranger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.arranger.ArrangerViewModel
import com.example.domain.midi_core.MidiAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ArrangerViewModel,
    onBack: () -> Unit
) {
    var triggerRecompose by remember { mutableStateOf(0) }

    // Callback para atualizar a tela quando um mapeamento mudar
    DisposableEffect(Unit) {
        viewModel.midiMappingManager.onMappingChanged = {
            triggerRecompose++
        }
        onDispose {
            viewModel.midiMappingManager.onMappingChanged = null
            viewModel.midiMappingManager.cancelLearning()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações MIDI (MIDI Learn)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Fonte Geradora de Som (Motor de Áudio)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Escolha se o som é gerado pelo hardware do Yamaha ou pelos SoundFonts (Korg Triton) no celular/tablet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val currentEngine = com.example.MidiState.soundOutputEngine
                    val context = androidx.compose.ui.platform.LocalContext.current

                    com.example.domain.soundfont.model.SoundOutputEngine.values().forEach { engine ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (currentEngine == engine),
                                onClick = { com.example.MidiState.setSoundOutput(engine, context) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(engine.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    engine.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val usbAudio = com.example.MidiState.audioTrackBackend.connectedUsbDeviceName
                    Surface(
                        color = if (usbAudio != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                if (usbAudio != null) "✓ Interface de Áudio USB: $usbAudio (Sem delay / 32-bit Float)"
                                else "Saída de Áudio: Sistema / Fone de Ouvido (Oboe Low Latency)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Polifonia do Motor Sonoro (SoundFonts / C++ Oboe)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Selecione 128 vozes para celulares potentes ou 64 vozes para economia de bateria.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val currentPolyphony = com.example.MidiState.polyphonyMode
                    val polyphonyOptions = listOf(
                        128 to "128 Vozes (Alta Potência / Celulares Potentes - Sem corte de notas)",
                        64 to "64 Vozes (Padrão balanceado / Econômico)"
                    )

                    polyphonyOptions.forEach { (voices, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (currentPolyphony == voices),
                                onClick = { com.example.MidiState.setPolyphony(voices) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Modo de Desempenho MIDI",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Resolução musical de 1920 PPQ em todos os modos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val currentMode = com.example.MidiState.midiSchedulerMode
                    val modes = listOf(
                        com.example.domain.midi.MidiSchedulerMode.AUTO to "Automático (detecta dispositivo)",
                        com.example.domain.midi.MidiSchedulerMode.J5_COMPATIBILITY to "Compatibilidade / J5 (CPU econômica)",
                        com.example.domain.midi.MidiSchedulerMode.HIGH_PRECISION to "Alta Precisão (dispositivos rápidos)"
                    )

                    modes.forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (currentMode == mode),
                                onClick = { com.example.MidiState.midiSchedulerMode = mode }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            var showMetronomeDialog by remember { mutableStateOf(false) }
            if (showMetronomeDialog) {
                MetronomeDialog(onDismiss = { showMetronomeDialog = false; triggerRecompose++ })
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Metrônomo Musical & Click",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val presetName = com.example.domain.metronome.MetronomeConfig.PRESETS.find { it.id == com.example.domain.metronome.MetronomeConfig.selectedPresetId }?.displayName ?: "Personalizado"
                            Text(
                                "Timbre: $presetName (Peças: ${com.example.domain.metronome.MetronomeConfig.downbeatNote} / ${com.example.domain.metronome.MetronomeConfig.upbeatNote})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { showMetronomeDialog = true }) {
                            Text("Configurar")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Mapeamento de Botões Físicos",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Clique em 'Mapear' e pressione um botão físico no seu Yamaha PSR-S750 para associar o sinal.",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            val currentLearning = viewModel.midiMappingManager.learningAction

            if (currentLearning != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Aguardando sinal MIDI para: ${currentLearning.displayName}...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.midiMappingManager.cancelLearning(); triggerRecompose++ },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Exibe lista de Ações
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Lê triggerRecompose apenas para re-renderizar a lista quando mudar
                val dummy = triggerRecompose

                items(MidiAction.values()) { action ->
                    val trigger = viewModel.midiMappingManager.getTriggerForAction(action)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(action.displayName, style = MaterialTheme.typography.titleMedium)
                                if (trigger != null) {
                                    Text(
                                        "Mapeado: ${trigger}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        "Não mapeado",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            
                            Row {
                                if (trigger != null) {
                                    IconButton(
                                        onClick = {
                                            viewModel.midiMappingManager.clearMapping(action)
                                            triggerRecompose++
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                
                                Button(
                                    onClick = {
                                        viewModel.midiMappingManager.startLearning(action)
                                        triggerRecompose++
                                    },
                                    enabled = currentLearning == null
                                ) {
                                    Text("Mapear")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
