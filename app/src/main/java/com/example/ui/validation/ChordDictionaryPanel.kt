package com.example.ui.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ChordDictionaryPanel() {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dicionário de Acordes (Tabela Visual)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Ocultar" else "Mostrar")
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                val chords = listOf(
                    Triple("Maior (Ex: C)", "Tônica, Terça Maior, Quinta Justa", "C, E, G"),
                    Triple("Menor (Ex: Cm)", "Tônica, Terça Menor, Quinta Justa", "C, D#, G"),
                    Triple("Sétima (Ex: C7)", "Tônica, Terça Maior, Quinta, Sétima Menor", "C, E, G, A#"),
                    Triple("Maior com Sétima (Ex: CMaj7)", "Tônica, Terça Maior, Quinta, Sétima Maior", "C, E, G, B"),
                    Triple("Menor com Sétima (Ex: Cm7)", "Tônica, Terça Menor, Quinta, Sétima Menor", "C, D#, G, A#"),
                    Triple("Diminuto (Ex: Cdim)", "Tônica, Terça Menor, Quinta Diminuta", "C, D#, F#")
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chords.forEach { (name, interval, notes) ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Intervalos: $interval", style = MaterialTheme.typography.bodySmall)
                                Text("Teclas (Dó): $notes", style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "O mecanismo analisa as notas pressionadas simultaneamente na mão esquerda (abaixo do Split Point) e deduz a tônica e a qualidade do acorde matematicamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
