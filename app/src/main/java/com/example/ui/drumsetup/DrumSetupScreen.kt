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

val drumKeyNamesLive = mapOf(
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
fun DrumSetupScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Drum Setup (Edição ao Vivo - XG)", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Edite os parâmetros da bateria em tempo real no Canal 10.", fontSize = 12.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        val channel = 9 // Ch 10
        var expandedKit by remember { mutableStateOf(false) }
        var currentKitName by remember { mutableStateOf("Live Drum Kit") }
        var resetTrigger by remember { mutableStateOf(0) }
        val liveParams = com.example.MidiState.liveDrumParams

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box {
                Button(onClick = { expandedKit = true }) { Text(currentKitName) }
                DropdownMenu(expanded = expandedKit, onDismissRequest = { expandedKit = false }) {
                    com.example.InstrumentDatabase.drumKits.forEach { kit ->
                        DropdownMenuItem(text = { Text(kit.name) }, onClick = {
                            currentKitName = kit.name
                            com.example.MidiState.sendProgramChange(channel, kit.msb, kit.lsb, kit.pc)
                            expandedKit = false
                        })
                    }
                }
            }
            Button(
                onClick = {
                    liveParams.clear()
                    resetTrigger++ 
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reset Params", color = androidx.compose.ui.graphics.Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        var selectedKey by remember { mutableStateOf(36) }
        
        LaunchedEffect(Unit) {
            com.example.MidiState.sendSysEx(byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, 0x00, 0x00, 0x7E, 0x00, 0xF7.toByte()))
        }
        
        fun sendDrumParam(paramId: Int, value: Int) {
            val outChannel = channel
            val nrpnMsb = when(paramId) {
                0 -> 24
                1 -> 26
                2 -> 28
                5 -> 29
                6 -> 30
                else -> -1
            }
            if (nrpnMsb != -1) {
                com.example.MidiState.sendControlChange(outChannel, 99, nrpnMsb)
                com.example.MidiState.sendControlChange(outChannel, 98, selectedKey)
                com.example.MidiState.sendControlChange(outChannel, 6, value)
                com.example.MidiState.sendControlChange(outChannel, 38, 0)
                com.example.MidiState.sendControlChange(outChannel, 101, 127)
                com.example.MidiState.sendControlChange(outChannel, 100, 127)
            }
            
            val setupByte = 0x30.toByte()
            val sysex = byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, setupByte, selectedKey.toByte(), paramId.toByte(), value.toByte(), 0xF7.toByte())
            com.example.MidiState.sendSysEx(sysex)
        }
        
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight()) {
                items(drumKeyNamesLive.entries.toList()) { entry ->
                    val isSelected = selectedKey == entry.key
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        onClick = { selectedKey = entry.key },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("${entry.key}: ${entry.value}", modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                    }
                }
            }
            
            Column(modifier = Modifier.weight(1.5f).verticalScroll(rememberScrollState())) {
                Text(drumKeyNamesLive[selectedKey] ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                val params = liveParams[selectedKey] ?: com.example.DrumKeyParams()
                var pitchCoarse by remember(selectedKey, resetTrigger) { mutableStateOf(params.pitchCoarse.toFloat()) }
                var pitchFine by remember(selectedKey, resetTrigger) { mutableStateOf(params.pitchFine.toFloat()) }
                var level by remember(selectedKey, resetTrigger) { mutableStateOf(params.level.toFloat()) }
                var pan by remember(selectedKey, resetTrigger) { mutableStateOf(params.pan.toFloat()) }
                var reverb by remember(selectedKey, resetTrigger) { mutableStateOf(params.reverb.toFloat()) }
                var chorus by remember(selectedKey, resetTrigger) { mutableStateOf(params.chorus.toFloat()) }

                fun saveParams() {
                    liveParams[selectedKey] = com.example.DrumKeyParams(pitchCoarse.toInt(), pitchFine.toInt(), level.toInt(), pan.toInt(), reverb.toInt(), chorus.toInt())
                }

                Text("Pitch Coarse: ${pitchCoarse.toInt()}", fontSize = 12.sp)
                Slider(value = pitchCoarse, valueRange = 0f..127f, onValueChange = { pitchCoarse = it; sendDrumParam(0, pitchCoarse.toInt()); saveParams() })
                
                Text("Pitch Fine: ${pitchFine.toInt()}", fontSize = 12.sp)
                Slider(value = pitchFine, valueRange = 0f..127f, onValueChange = { pitchFine = it; sendDrumParam(1, pitchFine.toInt()); saveParams() })
                
                Text("Level: ${level.toInt()}", fontSize = 12.sp)
                Slider(value = level, valueRange = 0f..127f, onValueChange = { level = it; sendDrumParam(2, level.toInt()); saveParams() })
                
                Text("Pan: ${pan.toInt()}", fontSize = 12.sp)
                Slider(value = pan, valueRange = 0f..127f, onValueChange = { pan = it; sendDrumParam(4, pan.toInt()); saveParams() })
                
                Text("Reverb: ${reverb.toInt()}", fontSize = 12.sp)
                Slider(value = reverb, valueRange = 0f..127f, onValueChange = { reverb = it; sendDrumParam(5, reverb.toInt()); saveParams() })
                
                Text("Chorus: ${chorus.toInt()}", fontSize = 12.sp)
                Slider(value = chorus, valueRange = 0f..127f, onValueChange = { chorus = it; sendDrumParam(6, chorus.toInt()); saveParams() })
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { 
                    com.example.MidiState.sendNoteOn(channel, selectedKey, level.toInt()) 
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Test Sound")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
