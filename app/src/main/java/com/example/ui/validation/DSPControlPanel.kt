package com.example.ui.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MidiState

data class DspType(val name: String, val msb: Byte, val lsb: Byte)

val ReverbTypes = listOf(
    DspType("No Effect", 0, 0),
    DspType("Hall 1", 1, 0),
    DspType("Hall 2", 1, 16),
    DspType("Hall 3", 1, 17),
    DspType("Hall 4", 1, 18),
    DspType("Room 1", 2, 16),
    DspType("Room 2", 2, 17),
    DspType("Room 3", 2, 18),
    DspType("Stage 1", 3, 16),
    DspType("Stage 2", 3, 17),
    DspType("Plate 1", 4, 16),
    DspType("Plate 2", 4, 17)
)

val ChorusTypes = listOf(
    DspType("No Effect", 0, 0),
    DspType("Chorus 1", 66, 17),
    DspType("Chorus 2", 66, 8),
    DspType("Chorus 3", 66, 16),
    DspType("Chorus 4", 66, 1),
    DspType("Celeste 1", 66, 0),
    DspType("Celeste 2", 66, 2),
    DspType("Flanger 1", 67, 8),
    DspType("Flanger 2", 67, 16),
    DspType("Symphonic 1", 68, 16),
    DspType("Symphonic 2", 68, 0)
)

val Dsp1Types = listOf(
    DspType("No Effect", 0, 0),
    DspType("Delay L,C,R", 5, 16),
    DspType("Delay L,R", 6, 0),
    DspType("Echo", 7, 0),
    DspType("Cross Delay", 8, 0),
    DspType("Distortion 1", 75, 16),
    DspType("Overdrive", 74, 0),
    DspType("Amp Simulator", 75, 0),
    DspType("Rotary Speaker 1", 69, 16),
    DspType("Rotary Speaker 2", 71, 17),
    DspType("Tremolo", 70, 16),
    DspType("Auto Pan", 71, 16),
    DspType("Phaser", 72, 0),
    DspType("Auto Wah", 78, 16)
)

@Composable
fun DSPControlPanel() {
    var expandedReverb by remember { mutableStateOf(false) }
    var selectedReverb by remember { mutableStateOf(ReverbTypes[1]) } // Default Hall 1

    var expandedChorus by remember { mutableStateOf(false) }
    var selectedChorus by remember { mutableStateOf(ChorusTypes[1]) } // Default Chorus 1

    var expandedDsp1 by remember { mutableStateOf(false) }
    var selectedDsp1 by remember { mutableStateOf(Dsp1Types[11]) } // Default Rotary Speaker 1

    // Reverb Type SysEx: F0 43 1n 4C 02 01 00 MSB LSB F7
    fun sendReverbType(msb: Byte, lsb: Byte) {
        MidiState.sendSysEx(byteArrayOf(0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x4C.toByte(), 0x02.toByte(), 0x01.toByte(), 0x00.toByte(), msb, lsb, 0xF7.toByte()))
    }

    // Chorus Type SysEx: F0 43 1n 4C 02 01 20 MSB LSB F7
    fun sendChorusType(msb: Byte, lsb: Byte) {
        MidiState.sendSysEx(byteArrayOf(0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x4C.toByte(), 0x02.toByte(), 0x01.toByte(), 0x20.toByte(), msb, lsb, 0xF7.toByte()))
    }

    // DSP1 Type SysEx: F0 43 1n 4C 02 01 40 MSB LSB F7
    fun sendDsp1Type(msb: Byte, lsb: Byte) {
        MidiState.sendSysEx(byteArrayOf(0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x4C.toByte(), 0x02.toByte(), 0x01.toByte(), 0x40.toByte(), msb, lsb, 0xF7.toByte()))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("DSPs e Efeitos Yamaha PSR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text("O chip interno do PSR possui blocos profissionais de efeitos digitais. Escolha abaixo quais simulações físicas carregar na memória do teclado.", fontSize = 12.sp, color = Color.LightGray)

        // Reverb Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("REVERB (Motor Acústico)", fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    Button(onClick = { expandedReverb = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))) {
                        Text(selectedReverb.name)
                    }
                    DropdownMenu(expanded = expandedReverb, onDismissRequest = { expandedReverb = false }) {
                        ReverbTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedReverb = type
                                    expandedReverb = false
                                    sendReverbType(type.msb, type.lsb)
                                }
                            )
                        }
                    }
                }
                Text("Para ajustar a quantidade, use o fader 'Reverb' na aba de Timbres.", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Chorus Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CHORUS (Modulação Espacial)", fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    Button(onClick = { expandedChorus = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))) {
                        Text(selectedChorus.name)
                    }
                    DropdownMenu(expanded = expandedChorus, onDismissRequest = { expandedChorus = false }) {
                        ChorusTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedChorus = type
                                    expandedChorus = false
                                    sendChorusType(type.msb, type.lsb)
                                }
                            )
                        }
                    }
                }
                Text("Para ajustar a quantidade, use o fader 'Chorus' na aba de Timbres.", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // DSP1 Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("DSP VARIATION (Efeitos de Inserção)", fontWeight = FontWeight.Bold, color = Color.White)
                Text("Delay, Amp Simulators, Rotary Speaker, Phaser, etc.", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    Button(onClick = { expandedDsp1 = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))) {
                        Text(selectedDsp1.name)
                    }
                    DropdownMenu(expanded = expandedDsp1, onDismissRequest = { expandedDsp1 = false }) {
                        Dsp1Types.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedDsp1 = type
                                    expandedDsp1 = false
                                    sendDsp1Type(type.msb, type.lsb)
                                }
                            )
                        }
                    }
                }
                Text("Para aplicar esse efeito em um timbre (Right 1, 2 ou Left), use o fader 'DSP / Var' na aba de Timbres.", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
    }
}
