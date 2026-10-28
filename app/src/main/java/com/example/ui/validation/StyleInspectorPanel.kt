package com.example.ui.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.domain.models.StyFile

@Composable
fun StyleInspectorPanel(styFile: StyFile?, viewModel: com.example.presentation.viewmodel.ValidationPlayerViewModel) {
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && styFile != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(styFile.rawData)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    if (styFile == null) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("No style loaded.", color = Color.Gray)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Style Inspector", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("STYLE", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Name: ${styFile.name}")
                Text("PPQ: ${styFile.ppq}")
                Text("Sections: ${styFile.sections.size}")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SECTIONS", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                styFile.sections.keys.forEach { section ->
                    Text(section.name)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CASM RULES", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                styFile.casm.sections.firstOrNull()?.channels?.forEach { channel ->
                    Text("Channel ${channel.destChannel}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("NTR: ${channel.ntr.displayName}", style = MaterialTheme.typography.bodySmall)
                    Text("NTT: ${channel.ntt.displayName}", style = MaterialTheme.typography.bodySmall)
                    Text("High Limit: ${channel.noteHighLimit}", style = MaterialTheme.typography.bodySmall)
                    Text("Low Limit: ${channel.noteLowLimit}", style = MaterialTheme.typography.bodySmall)
                    Text("RTR: ${channel.retriggerRule.displayName} (${channel.retriggerRule.id})", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CHANNELS (Instruments)", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                for (ch in 8..15) {
                    val chName = when(ch) {
                        8 -> "Rhythm 1 (Ch 9)"
                        9 -> "Rhythm 2 (Ch 10)"
                        10 -> "Bass (Ch 11)"
                        11 -> "Chord 1 (Ch 12)"
                        12 -> "Chord 2 (Ch 13)"
                        13 -> "Pad (Ch 14)"
                        14 -> "Phrase 1 (Ch 15)"
                        15 -> "Phrase 2 (Ch 16)"
                        else -> "Channel ${ch + 1}"
                    }
                    
                    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    var currentVoiceName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("Default Voice") }
                    
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth().clickable { expanded = true }, 
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(chName, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(currentVoiceName, color = MaterialTheme.colorScheme.primary)
                        
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            val list = if (ch == 8 || ch == 9) com.example.InstrumentDatabase.drumKits else com.example.InstrumentDatabase.instruments
                            list.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice.name) },
                                    onClick = {
                                        currentVoiceName = voice.name
                                        viewModel.setChannelInstrument(ch, voice.msb, voice.lsb, voice.pc)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = { 
            saveLauncher.launch(styFile.name.ifEmpty { "modified.sty" })
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Save Modified Style")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
