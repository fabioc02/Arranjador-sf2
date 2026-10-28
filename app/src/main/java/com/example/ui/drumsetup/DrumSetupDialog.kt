package com.example.ui.drumsetup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MidiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay

val drumKeyNames = mapOf(
    35 to "Acoustic Bass Drum", 36 to "Bass Drum 1", 37 to "Side Stick", 38 to "Acoustic Snare",
    39 to "Hand Clap", 40 to "Electric Snare", 41 to "Low Floor Tom", 42 to "Closed Hi-Hat",
    43 to "High Floor Tom", 44 to "Pedal Hi-Hat", 45 to "Low Tom", 46 to "Open Hi-Hat",
    47 to "Low-Mid Tom", 48 to "Hi-Mid Tom", 49 to "Crash Cymbal 1", 50 to "High Tom",
    51 to "Ride Cymbal 1", 52 to "Chinese Cymbal", 53 to "Ride Bell", 54 to "Tambourine",
    55 to "Splash Cymbal", 56 to "Cowbell", 57 to "Crash Cymbal 2", 58 to "Vibraslap",
    59 to "Ride Cymbal 2", 60 to "Hi Bongo", 61 to "Low Bongo", 62 to "Mute Hi Conga",
    63 to "Open Hi Conga", 64 to "Low Conga", 65 to "High Timbale", 66 to "Low Timbale",
    67 to "High Agogo", 68 to "Low Agogo", 69 to "Cabasa", 70 to "Maracas",
    71 to "Short Whistle", 72 to "Long Whistle", 73 to "Short Guiro", 74 to "Long Guiro",
    75 to "Claves", 76 to "Hi Wood Block", 77 to "Low Wood Block", 78 to "Mute Cuica",
    79 to "Open Cuica", 80 to "Mute Triangle", 81 to "Open Triangle"
)

@Composable
fun DrumSetupDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Drum Setup / Tuner (XG)") },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Text("Select a drum kit and key to adjust its independent tuning, volume, pan, etc.", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                
                val currentTrack = com.example.MidiRhythmState.tracks.getOrNull(com.example.MidiRhythmState.selectedTrackIndex)
                var expandedKit by remember { mutableStateOf(false) }
                val defaultKit = com.example.InstrumentDatabase.drumKits.find { it.pc == currentTrack?.program && it.msb == currentTrack?.bankMsb }?.name ?: "StandardKit1"
                var currentKitName by remember(currentTrack?.program) { mutableStateOf(defaultKit) }
                var resetTrigger by remember { mutableStateOf(0) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Button(onClick = { expandedKit = true }) { Text("Kit: $currentKitName") }
                        DropdownMenu(expanded = expandedKit, onDismissRequest = { expandedKit = false }) {
                            com.example.InstrumentDatabase.drumKits.forEach { kit ->
                                DropdownMenuItem(text = { Text(kit.name) }, onClick = {
                                    currentKitName = kit.name
                                    currentTrack?.bankMsb = kit.msb
                                    currentTrack?.bankLsb = kit.lsb
                                    currentTrack?.program = kit.pc
                                    MidiState.sendProgramChange(currentTrack?.channel?.minus(1) ?: 9, kit.msb, kit.lsb, kit.pc)
                                    expandedKit = false
                                })
                            }
                        }
                    }
                    
                    Button(
                        onClick = {
                            currentTrack?.drumParams?.clear()
                            val ch = currentTrack?.channel?.minus(1) ?: 9
                            currentTrack?.let {
                                MidiState.sendProgramChange(ch, it.bankMsb, it.bankLsb, it.program)
                            }
                            resetTrigger++ 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset All Keys", color = MaterialTheme.colorScheme.onError)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                
                var selectedKey by remember { mutableStateOf(36) }
                
                

                val initialParams = currentTrack?.drumParams?.get(selectedKey) ?: com.example.DrumKeyParams()
                var pitchCoarse by remember(selectedKey, resetTrigger) { mutableStateOf(initialParams.pitchCoarse.toFloat()) }
                var pitchFine by remember(selectedKey, resetTrigger) { mutableStateOf(initialParams.pitchFine.toFloat()) }
                var level by remember(selectedKey, resetTrigger) { mutableStateOf(initialParams.level.toFloat()) }
                var pan by remember(selectedKey, resetTrigger) { mutableStateOf(initialParams.pan.toFloat()) }
                var reverb by remember(selectedKey, resetTrigger) { mutableStateOf(initialParams.reverb.toFloat()) }
                var chorus by remember(selectedKey, resetTrigger) { mutableStateOf(initialParams.chorus.toFloat()) }
                
                fun saveParams() {
                    currentTrack?.drumParams?.put(selectedKey, com.example.DrumKeyParams(
                        pitchCoarse.toInt(), pitchFine.toInt(), level.toInt(), pan.toInt(), reverb.toInt(), chorus.toInt()
                    ))
                }
                
                val scope = rememberCoroutineScope()
                var lastSendTime by remember { mutableStateOf(0L) }
                
                LaunchedEffect(Unit) {
                    // Ensure keyboard is in XG Mode when editing drums
                    MidiState.sendSysEx(byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, 0x00, 0x00, 0x7E, 0x00, 0xF7.toByte()))
                }
                
                fun sendDrumParam(paramId: Int, value: Int) {
                    saveParams()
                    val outChannel = (currentTrack?.channel ?: 10) - 1
                    
                    val now = System.currentTimeMillis()
                    // Remove aggressive dropping debounce. Use simple send.
                    // Instead of dropping, we just send it. If it chokes, we'll implement a proper coroutine debounce.
                    // But SysEx buffer was causing issues, so let's just send NRPN for real-time.
                    
                    // Send NRPN (Works for Panel Kits & Revo Drums)
                    val nrpnMsb = when (paramId) {
                        0 -> 24 // Pitch Coarse
                        1 -> 25 // Pitch Fine
                        2 -> 26 // Level
                        4 -> 28 // Pan
                        5 -> 29 // Reverb
                        6 -> 30 // Chorus
                        else -> -1
                    }
                    if (nrpnMsb != -1) {
                        MidiState.sendControlChange(outChannel, 99, nrpnMsb)
                        MidiState.sendControlChange(outChannel, 98, selectedKey)
                        MidiState.sendControlChange(outChannel, 6, value)
                        MidiState.sendControlChange(outChannel, 38, 0) // LSB
                        MidiState.sendControlChange(outChannel, 101, 127) // RPN Null
                        MidiState.sendControlChange(outChannel, 100, 127) // RPN Null
                    }
                    
                    // Send XG SysEx (Most robust for Genos/Tyros Drum Setup real-time)
                    val setupNumber = if (outChannel == 8) 1 else 0 // if Ch 9 (index 8), use Setup 2 (0x31)
                    val setupByte = (0x30 + setupNumber).toByte()
                    val sysex = byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, setupByte, selectedKey.toByte(), paramId.toByte(), value.toByte(), 0xF7.toByte())
                    MidiState.sendSysEx(sysex)
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Drum List
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val recordedKeys = currentTrack?.notes?.map { it.note }?.toSet() ?: emptySet()
                        items((35..81).toList()) { key ->
                            val isSelected = key == selectedKey
                            val name = drumKeyNames[key] ?: "Key $key"
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                       else if (key in recordedKeys) MaterialTheme.colorScheme.tertiaryContainer 
                                       else MaterialTheme.colorScheme.surface,
                                onClick = { selectedKey = key },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Text("$key: $name", modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                            }
                        }
                    }
                    
                    // Controls
                    Column(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        Text("Pitch Coarse: ${(pitchCoarse - 64).toInt()}", fontSize = 12.sp)
                        Slider(value = pitchCoarse, valueRange = 0f..127f, onValueChange = { pitchCoarse = it; sendDrumParam(0, pitchCoarse.toInt()) })
                        
                        Text("Pitch Fine: ${(pitchFine - 64).toInt()}", fontSize = 12.sp)
                        Slider(value = pitchFine, valueRange = 0f..127f, onValueChange = { pitchFine = it; sendDrumParam(1, pitchFine.toInt()) })
                        
                        Text("Level: ${level.toInt()}", fontSize = 12.sp)
                        Slider(value = level, valueRange = 0f..127f, onValueChange = { level = it; sendDrumParam(2, level.toInt()) })
                        
                        Text("Pan: ${pan.toInt()}", fontSize = 12.sp)
                        Slider(value = pan, valueRange = 0f..127f, onValueChange = { pan = it; sendDrumParam(4, pan.toInt()) })
                        
                        Text("Reverb Send: ${reverb.toInt()}", fontSize = 12.sp)
                        Slider(value = reverb, valueRange = 0f..127f, onValueChange = { reverb = it; sendDrumParam(5, reverb.toInt()) })
                        
                        Text("Chorus Send: ${chorus.toInt()}", fontSize = 12.sp)
                        Slider(value = chorus, valueRange = 0f..127f, onValueChange = { chorus = it; sendDrumParam(6, chorus.toInt()) })
                        
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = { 
                            val outChannel = (currentTrack?.channel ?: 10) - 1
                            MidiState.sendNoteOn(outChannel, selectedKey, level.toInt()) 
                        }) {
                            Text("Test Sound")
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}
