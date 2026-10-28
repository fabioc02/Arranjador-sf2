package com.example.ui.validation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.models.Chord
import com.example.domain.models.ChordType

@Composable
fun ChordSelector(currentChord: Chord, onChordSelected: (Chord) -> Unit) {
    val roots = listOf(
        0 to "C", 1 to "C#", 2 to "D", 3 to "Eb", 4 to "E", 5 to "F", 
        6 to "F#", 7 to "G", 8 to "Ab", 9 to "A", 10 to "Bb", 11 to "B"
    )
    
    val types = listOf(
        ChordType.Major, ChordType.Minor, ChordType.Dominant7, 
        ChordType.Major7, ChordType.Minor7, ChordType.Diminished, 
        ChordType.Augmented, ChordType.Sus4, ChordType.Minor7b5, 
        ChordType.Major9
    )
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Root Note", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(roots) { (rootValue, rootName) ->
                FilterChip(
                    selected = currentChord.rootKey == rootValue,
                    onClick = { onChordSelected(currentChord.copy(rootKey = rootValue)) },
                    label = { Text(rootName) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text("Chord Type", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(types) { type ->
                FilterChip(
                    selected = currentChord.type == type,
                    onClick = { onChordSelected(currentChord.copy(type = type)) },
                    label = { Text(type.displayName) }
                )
            }
        }
    }
}
