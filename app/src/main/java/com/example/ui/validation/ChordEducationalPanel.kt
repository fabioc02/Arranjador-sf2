package com.example.ui.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.models.Chord

@Composable
fun ChordEducationalPanel(activeNotes: List<Int>, currentChord: Chord?, compact: Boolean = false) {
    if (compact) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column {
                    val noteNames = activeNotes.map { midiNoteToName(it).split(" ")[0] }.joinToString(" ")
                    Text("Teclas: ${if (activeNotes.isEmpty()) "---" else noteNames}", style = MaterialTheme.typography.bodySmall, color = if (activeNotes.isEmpty()) Color.Gray else MaterialTheme.colorScheme.onSurface)
                }
                if (currentChord != null) {
                    Text("${currentChord.displayName} (${translateChordName(currentChord.displayName)})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("---", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                }
            }
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Painel Educativo de Notas e Acordes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            val noteNames = activeNotes.map { midiNoteToName(it) }.joinToString(", ")
            
            Text("Teclas Pressionadas (Mão Esquerda):", style = MaterialTheme.typography.labelMedium)
            Text(if (activeNotes.isEmpty()) "Nenhuma" else noteNames, style = MaterialTheme.typography.bodyLarge, color = if (activeNotes.isEmpty()) Color.Gray else MaterialTheme.colorScheme.onSurface)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Acorde Detectado:", style = MaterialTheme.typography.labelMedium)
            if (currentChord != null) {
                Text("${currentChord.displayName} (${translateChordName(currentChord.displayName)})", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            } else {
                Text("---", style = MaterialTheme.typography.headlineSmall, color = Color.Gray)
            }
        }
    }
}

fun midiNoteToName(note: Int): String {
    val notes = arrayOf("C (Dó)", "C# (Dó#)", "D (Ré)", "D# (Ré#)", "E (Mi)", "F (Fá)", "F# (Fá#)", "G (Sol)", "G# (Sol#)", "A (Lá)", "A# (Lá#)", "B (Si)")
    val name = notes[note % 12]
    val octave = (note / 12) - 1
    return "$name$octave"
}

fun translateChordName(chordName: String): String {
    val roots = mapOf(
        "C" to "Dó", "C#" to "Dó#", "D" to "Ré", "D#" to "Ré#", 
        "E" to "Mi", "F" to "Fá", "F#" to "Fá#", "G" to "Sol", 
        "G#" to "Sol#", "A" to "Lá", "A#" to "Lá#", "B" to "Si"
    )
    var result = chordName
    roots.forEach { (en, pt) ->
        if (result.startsWith(en)) {
            result = result.replaceFirst(en, pt)
        }
    }
    
    if (result.contains("Maj")) result = result.replace("Maj", "Maior")
    else if (result.contains("m")) result = result.replace("m", "Menor")
    else if (result.length <= 2 || (result.length == 3 && result[1] == '#')) result += " Maior"
    
    return result
}
