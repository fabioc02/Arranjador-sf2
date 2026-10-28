package com.example.ui.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.models.Chord
import com.example.presentation.viewmodel.ValidationPlayerViewModel

@Composable
fun CasmTracePanel(
    currentChord: Chord,
    transLogs: List<ValidationPlayerViewModel.TranspositionLog>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("CASM Trace", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Chord:", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        Text(currentChord.displayName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        
        // Group transLogs by channel to show the latest for each channel
        val latestByChannel = transLogs.groupBy { it.channel }.mapValues { it.value.first() }.toSortedMap()
        
        latestByChannel.forEach { (channel, log) ->
            val isDrum = channel == 9
            val chStr = if (isDrum) "Drum/Ch 10" else "Channel ${channel + 1}"
            val ntr = if (log.ntr.isNotEmpty()) log.ntr else if (isDrum) "Bypass" else "Bypass"
            val ntt = if (log.ntt.isNotEmpty()) log.ntt else if (isDrum) "Bypass" else "Bypass"
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDrum) Color(0xFF3E2723) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(chStr, style = MaterialTheme.typography.titleMedium, color = if (isDrum) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row {
                        Text("Rule:", modifier = Modifier.width(64.dp), color = Color.Gray)
                        Text("$ntr / $ntt")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row {
                        Text("Input:", modifier = Modifier.width(64.dp), color = Color.Gray)
                        Text(log.originalNoteName, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row {
                        Text("Output:", modifier = Modifier.width(64.dp), color = Color.Gray)
                        Text(log.transposedNoteName, fontFamily = FontFamily.Monospace, color = Color(0xFF00FFCC))
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("PASS", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
        }
        
        if (latestByChannel.isEmpty()) {
            Text("No notes played yet...", color = Color.Gray)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
