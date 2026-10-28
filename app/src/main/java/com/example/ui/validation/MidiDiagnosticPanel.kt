package com.example.ui.validation

import android.media.midi.MidiDeviceInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.MidiState

@Composable
fun MidiDiagnosticPanel() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("MIDI Diagnostic (Hardware Test)", style = MaterialTheme.typography.titleMedium)
        
        Text("Total Input Ports in MidiState: ${MidiState.midiInputPorts.size}")
        
        MidiState.midiInputPorts.forEachIndexed { index, port ->
            Text("Port $index: portNumber=${port.portNumber}")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { 
                MidiState.midiInputPorts.forEach { port ->
                    val buffer = byteArrayOf(0x90.toByte(), 0x3C.toByte(), 0x64.toByte())
                    try { port.send(buffer, 0, 3, 0) } catch(e: Exception){}
                }
            }) { Text("Note On (Ch 1, C4)") }
            Button(onClick = { 
                MidiState.midiInputPorts.forEach { port ->
                    val buffer = byteArrayOf(0x80.toByte(), 0x3C.toByte(), 0x00.toByte())
                    try { port.send(buffer, 0, 3, 0) } catch(e: Exception){}
                }
            }) { Text("Note Off (Ch 1, C4)") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { 
                // Program Change 0 (Grand Piano) on Ch 1
                MidiState.midiInputPorts.forEach { port ->
                    try { port.send(byteArrayOf(0xC0.toByte(), 0x00.toByte()), 0, 2, 0) } catch(e: Exception){}
                }
            }) { Text("PC: 0 (Ch 1)") }
            
            Button(onClick = { 
                // Volume 100 on Ch 1
                MidiState.midiInputPorts.forEach { port ->
                    try { port.send(byteArrayOf(0xB0.toByte(), 0x07.toByte(), 0x64.toByte()), 0, 3, 0) } catch(e: Exception){}
                }
            }) { Text("Vol: 100 (Ch 1)") }
        }
    }
}
