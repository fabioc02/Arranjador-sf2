package com.example.ui.arranger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.arranger.ArrangerViewModel
import com.example.domain.models.StyleSection
import com.example.domain.models.MidiEvent

@Composable
fun DiagnosticDialog(
    viewModel: ArrangerViewModel,
    onDismiss: () -> Unit
) {
    val style = viewModel.state.loadedStyle.get()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize()
            ) {
                Text("Diagnóstico do Style", style = MaterialTheme.typography.titleLarge)
                Text("Instrumentos originais programados neste ritmo:", style = MaterialTheme.typography.bodySmall)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (style == null) {
                    Text("Nenhum estilo carregado.", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sectionData = style.sections[StyleSection.MainA] ?: style.sections.values.firstOrNull()
                        
                        if (sectionData != null) {
                            val channels = listOf(
                                Pair(8, "Rhythm Sub (Bateria 1)"),
                                Pair(9, "Rhythm Main (Bateria 2)"),
                                Pair(10, "Bass (Baixo)"),
                                Pair(11, "Chord 1 (Acorde 1)"),
                                Pair(12, "Chord 2 (Acorde 2)"),
                                Pair(13, "Pad (Fundo)"),
                                Pair(14, "Phrase 1 (Frase 1)"),
                                Pair(15, "Phrase 2 (Frase 2)")
                            )
                            
                            items(channels) { (chIndex, chName) ->
                                var msb = -1
                                var lsb = -1
                                var pc = -1
                                
                                // Percorrer todas as seções, começando pelas primeiras para capturar a inicialização
                                style.sections.values.sortedBy { it.startTick }.forEach { sectionData ->
                                    val track = sectionData.tracks.values.find { t -> 
                                        t.events.any { e -> 
                                            when (e) {
                                                is MidiEvent.NoteOn -> e.channel == chIndex
                                                is MidiEvent.NoteOff -> e.channel == chIndex
                                                is MidiEvent.ControlChange -> e.channel == chIndex
                                                is MidiEvent.ProgramChange -> e.channel == chIndex
                                                is MidiEvent.PitchBend -> e.channel == chIndex
                                                else -> false
                                            }
                                        } 
                                    } ?: sectionData.tracks[chIndex]
                                    
                                    track?.events?.forEach { event ->
                                        val evChannel = when (event) {
                                            is MidiEvent.NoteOn -> event.channel
                                            is MidiEvent.NoteOff -> event.channel
                                            is MidiEvent.ControlChange -> event.channel
                                            is MidiEvent.ProgramChange -> event.channel
                                            is MidiEvent.PitchBend -> event.channel
                                            else -> -1
                                        }
                                        
                                        if (evChannel == chIndex) {
                                            if (event is MidiEvent.ControlChange) {
                                                if (event.controller == 0 && msb == -1) msb = event.value
                                                if (event.controller == 32 && lsb == -1) lsb = event.value
                                            } else if (event is MidiEvent.ProgramChange && pc == -1) {
                                                pc = event.program
                                            }
                                        }
                                    }
                                }
                                
                                val instrumentInfo = if (pc != -1) {
                                    val msbVal = if(msb!=-1) msb else 0
                                    val lsbVal = if(lsb!=-1) lsb else 0
                                    val voiceName = com.example.InstrumentDatabase.instruments.find { it.msb == msbVal && it.lsb == lsbVal && it.pc == pc }?.name 
                                        ?: com.example.InstrumentDatabase.drumKits.find { it.msb == msbVal && it.lsb == lsbVal && it.pc == pc }?.name 
                                        ?: "Desconhecido"
                                    "MSB: $msbVal | LSB: $lsbVal | PC: $pc\nIdentificado: $voiceName"
                                } else {
                                    "Não especificado"
                                }
                                
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                                        Text(chName, style = MaterialTheme.typography.titleSmall)
                                        Text(instrumentInfo, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        } else {
                            item { Text("O estilo não possui seções válidas.") }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Fechar")
                }
            }
        }
    }
}
