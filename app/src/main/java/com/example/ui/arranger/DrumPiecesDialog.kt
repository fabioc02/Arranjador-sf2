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

@Composable
fun DrumPiecesDialog(
    viewModel: ArrangerViewModel,
    onDismiss: () -> Unit
) {
    var triggerRecompose by remember { mutableStateOf(0) }
    val dummy = triggerRecompose

    val drumPieces = listOf(
        Pair(27, "High Q"),
        Pair(28, "Slap"),
        Pair(29, "Scratch Push"),
        Pair(30, "Scratch Pull"),
        Pair(31, "Sticks"),
        Pair(32, "Square Click"),
        Pair(33, "Metronome Click"),
        Pair(34, "Metronome Bell"),
        Pair(35, "Acoustic Bass Drum (Bumbo Acústico)"),
        Pair(36, "Bass Drum 1 (Bumbo 1)"),
        Pair(37, "Side Stick (Aro da Caixa)"),
        Pair(38, "Acoustic Snare (Caixa Acústica)"),
        Pair(39, "Hand Clap (Palmas)"),
        Pair(40, "Electric Snare (Caixa Eletrônica)"),
        Pair(41, "Low Floor Tom (Tom Grave)"),
        Pair(42, "Closed Hi-Hat (Chimbal Fechado)"),
        Pair(43, "High Floor Tom (Tom Médio Grave)"),
        Pair(44, "Pedal Hi-Hat (Chimbal Pedal)"),
        Pair(45, "Low Tom (Tom Médio)"),
        Pair(46, "Open Hi-Hat (Chimbal Aberto)"),
        Pair(47, "Low-Mid Tom (Tom Agudo)"),
        Pair(48, "Hi-Mid Tom (Tom Super Agudo)"),
        Pair(49, "Crash Cymbal 1 (Prato de Ataque 1)"),
        Pair(50, "High Tom"),
        Pair(51, "Ride Cymbal 1 (Prato de Condução)"),
        Pair(52, "Chinese Cymbal (Prato Chinês)"),
        Pair(53, "Ride Bell (Cúpula do Prato)"),
        Pair(54, "Tambourine (Pandeirola)"),
        Pair(55, "Splash Cymbal (Prato Splash)"),
        Pair(56, "Cowbell (Caneca)"),
        Pair(57, "Crash Cymbal 2 (Prato de Ataque 2)"),
        Pair(58, "Vibraslap"),
        Pair(59, "Ride Cymbal 2"),
        Pair(60, "Hi Bongo"),
        Pair(61, "Low Bongo"),
        Pair(62, "Mute Hi Conga"),
        Pair(63, "Open Hi Conga"),
        Pair(64, "Low Conga"),
        Pair(65, "High Timbale"),
        Pair(66, "Low Timbale"),
        Pair(67, "High Agogo"),
        Pair(68, "Low Agogo"),
        Pair(69, "Cabasa"),
        Pair(70, "Maracas"),
        Pair(71, "Short Whistle"),
        Pair(72, "Long Whistle"),
        Pair(73, "Short Guiro"),
        Pair(74, "Long Guiro"),
        Pair(75, "Claves"),
        Pair(76, "Hi Wood Block"),
        Pair(77, "Low Wood Block"),
        Pair(78, "Mute Cuica"),
        Pair(79, "Open Cuica"),
        Pair(80, "Mute Triangle"),
        Pair(81, "Open Triangle")
    )

    val style = viewModel.state.loadedStyle.get()
    val usedDrumNotes = remember(style) {
        val notes = mutableSetOf<Int>()
        style?.sections?.values?.forEach { section ->
            section.tracks.values.forEach { track ->
                track.events.forEach { event ->
                    if (event is com.example.domain.models.MidiEvent.NoteOn && (event.channel == 8 || event.channel == 9)) {
                        notes.add(event.note)
                    }
                }
            }
        }
        notes
    }

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
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Configuração Fina (Peças)", style = MaterialTheme.typography.titleLarge)
                Text("Mapeie notas de bateria para outros sons.", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Peças em verde são as que o Estilo atual está usando.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(drumPieces) { (originalNote, name) ->
                        val currentRemap = viewModel.voiceManager.drumNoteRemap[originalNote]
                        val isUsed = usedDrumNotes.contains(originalNote)
                        
                        val containerColor = if (isUsed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        val contentColor = if (isUsed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = containerColor,
                                contentColor = contentColor
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("$originalNote: $name", style = MaterialTheme.typography.titleSmall)
                                    if (isUsed) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("Usado no Estilo")
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Redirecionar para:")
                                    
                                    var noteValue by remember(currentRemap) { 
                                        mutableStateOf(currentRemap?.toFloat() ?: originalNote.toFloat()) 
                                    }
                                    
                                    Slider(
                                        value = noteValue,
                                        onValueChange = { noteValue = it },
                                        onValueChangeFinished = {
                                            val target = noteValue.toInt()
                                            if (target == originalNote) {
                                                viewModel.voiceManager.clearDrumNoteRemap(originalNote)
                                            } else {
                                                viewModel.voiceManager.setDrumNoteRemap(originalNote, target)
                                            }
                                            triggerRecompose++
                                        },
                                        valueRange = 27f..81f,
                                        steps = 54,
                                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                                    )
                                    
                                    Text(noteValue.toInt().toString())
                                }
                                
                                val mappedName = drumPieces.find { it.first == currentRemap ?: originalNote }?.second ?: "Nota ${currentRemap ?: originalNote}"
                                Text(
                                    "Som que será tocado: $mappedName", 
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (currentRemap != null) MaterialTheme.colorScheme.error else contentColor
                                )
                            }
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
