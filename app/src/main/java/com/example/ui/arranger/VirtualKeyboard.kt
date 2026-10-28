package com.example.ui.arranger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MidiRhythmState
import com.example.RhythmTrack
import com.example.domain.chord.NoteUtils
import com.example.domain.models.Chord
import com.example.domain.models.ChordType

@Composable
fun VirtualKeyboard(
    track: RhythmTrack?,
    onConfigChange: (Int, ChordType) -> Unit = { _, _ -> },
    onNoteClick: (Int) -> Unit = {}
) {
    if (track == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Nenhuma pista selecionada", color = Color.White)
        }
        return
    }

    val isBass = track.channel == 11 || track.name.contains("Bass", ignoreCase = true)
    val isDrum = track.isDrumTrack || track.channel in 9..10

    // Source root and chord type
    val currentRoot = if (isBass) 0 else (track.sourceRoot?.rootKey ?: 0)
    val currentType = track.sourceRoot?.type ?: ChordType.Major7

    // Intervals based on mode
    val allowedIntervals = if (isDrum) {
        (0..11).toList()
    } else if (MidiRhythmState.evalMode == MidiRhythmState.EvalMode.CHORD) {
        val base = when (currentType) {
            ChordType.Major -> listOf(0, 4, 7)
            ChordType.Major7 -> listOf(0, 4, 7, 11)
            ChordType.Minor -> listOf(0, 3, 7)
            ChordType.Minor7 -> listOf(0, 3, 7, 10)
            ChordType.Dominant7 -> listOf(0, 4, 7, 10)
            ChordType.Diminished -> listOf(0, 3, 6)
            ChordType.Augmented -> listOf(0, 4, 8)
            ChordType.Sus4 -> listOf(0, 5, 7)
            else -> listOf(0, 4, 7)
        }
        val bassExtra = if (isBass) listOf(2, 9) else emptyList()
        base + bassExtra
    } else {
        if (currentType == ChordType.Minor || currentType == ChordType.Minor7) {
            listOf(0, 2, 3, 5, 7, 8, 10)
        } else {
            listOf(0, 2, 4, 5, 7, 9, 11)
        }
    }

    val allowedPitches = allowedIntervals.map { (currentRoot + it) % 12 }.toSet()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF212121), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        // Track Header & Reference Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Pista: ${track.name} (CH${track.channel})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isBass) {
                    Text(
                        text = "🔒 Tom Fixo Yamaha: C (Obrigatório para Bass)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFFB74D)
                    )
                } else if (isDrum) {
                    Text(
                        text = "🥁 Pista de Bateria / Percussão (Sem Transposição)",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                } else {
                    Text(
                        text = "Tonalidade de Origem desta Pista",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
            }

            // Mode Toggle (Acorde vs Escala)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { MidiRhythmState.evalMode = MidiRhythmState.EvalMode.CHORD },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (MidiRhythmState.evalMode == MidiRhythmState.EvalMode.CHORD) Color(0xFF1976D2) else Color(0xFF424242)
                    ),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Acorde", fontSize = 10.sp)
                }
                Button(
                    onClick = { MidiRhythmState.evalMode = MidiRhythmState.EvalMode.SCALE },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (MidiRhythmState.evalMode == MidiRhythmState.EvalMode.SCALE) Color(0xFF1976D2) else Color(0xFF424242)
                    ),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Escala", fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Selector row (disabled/fixed for Bass, active for harmonic tracks)
        if (!isDrum) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBass) {
                    Text("Fundamental: C (Padrão)", color = Color.White, fontSize = 12.sp)
                } else {
                    var showRootMenu by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = { showRootMenu = true },
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
                        ) {
                            Text("Tom: ${NoteUtils.NOTE_NAMES[currentRoot]}", fontSize = 11.sp, color = Color.White)
                        }
                        DropdownMenu(expanded = showRootMenu, onDismissRequest = { showRootMenu = false }) {
                            (0..11).forEach { rKey ->
                                DropdownMenuItem(
                                    text = { Text(NoteUtils.NOTE_NAMES[rKey]) },
                                    onClick = {
                                        onConfigChange(rKey, currentType)
                                        showRootMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Chord Type selector
                var showTypeMenu by remember { mutableStateOf(false) }
                val chordTypes = listOf(
                    ChordType.Major to "Maior",
                    ChordType.Minor to "Menor",
                    ChordType.Major7 to "Maj7",
                    ChordType.Minor7 to "m7",
                    ChordType.Dominant7 to "7 (Dom)",
                    ChordType.Diminished to "Dim",
                    ChordType.Augmented to "Aug",
                    ChordType.Sus4 to "Sus4"
                )
                Box {
                    Button(
                        onClick = { showTypeMenu = true },
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
                    ) {
                        val currentTypeName = chordTypes.find { it.first == currentType }?.second ?: currentType.name
                        Text("Tipo: $currentTypeName", fontSize = 11.sp, color = Color.White)
                    }
                    DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                        chordTypes.forEach { (cType, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onConfigChange(currentRoot, cType)
                                    showTypeMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Block wrong notes toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = MidiRhythmState.blockWrongNotes,
                        onCheckedChange = { MidiRhythmState.blockWrongNotes = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color.Red, uncheckedColor = Color.Gray)
                    )
                    Text("Bloquear Erradas", fontSize = 10.sp, color = Color.LightGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // CERTO / ERRADO Notification Banner
        val lastNote = MidiRhythmState.lastEvaluatedNote
        val lastStatus = MidiRhythmState.lastEvaluatedStatus
        val lastNoteName = MidiRhythmState.lastEvaluatedNoteName
        val lastRef = MidiRhythmState.lastEvaluatedReference

        val bannerColor = when (lastStatus) {
            true -> Color(0xFF1B5E20) // Deep Green
            false -> Color(0xFFB71C1C) // Deep Red
            null -> Color(0xFF333333)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bannerColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (lastStatus == null) {
                Text(
                    text = "Toque no teclado físico ou virtual para validar a nota",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            } else if (lastStatus == true) {
                Text(
                    text = "✅ CERTO: $lastNoteName",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Corresponde a $lastRef",
                    fontSize = 11.sp,
                    color = Color(0xFFA5D6A7)
                )
            } else {
                Text(
                    text = "❌ ERRADO: $lastNoteName",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (MidiRhythmState.blockWrongNotes) "Fora de $lastRef (BLOQUEADA)" else "Fora de $lastRef (Aviso)",
                    fontSize = 11.sp,
                    color = Color(0xFFFFCDD2)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Virtual Piano Keys
        val whiteKeys = listOf(0, 2, 4, 5, 7, 9, 11)
        val blackKeys = listOf(1, 3, -1, 6, 8, 10)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(Color.Black)
                .padding(2.dp)
        ) {
            // White Keys
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                whiteKeys.forEach { note ->
                    val isAllowed = allowedPitches.contains(note)
                    val isLastPlayed = lastNote != null && (lastNote % 12 == note)

                    val bgColor = when {
                        isLastPlayed && lastStatus == true -> Color(0xFFC8E6C9)
                        isLastPlayed && lastStatus == false -> Color(0xFFFFCDD2)
                        isAllowed -> Color(0xFFE8F5E9)
                        else -> Color.White
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .background(bgColor)
                            .border(1.dp, if (isLastPlayed) Color.Blue else Color.Black)
                            .clickable {
                                onNoteClick(note)
                                val (status, ref) = MidiRhythmState.evaluateNoteAgainstTrack(note + 48, track)
                                MidiRhythmState.lastEvaluatedNote = note + 48
                                MidiRhythmState.lastEvaluatedStatus = status
                                MidiRhythmState.lastEvaluatedNoteName = NoteUtils.NOTE_NAMES[note] + "3"
                                MidiRhythmState.lastEvaluatedReference = ref
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isAllowed) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF2E7D32), androidx.compose.foundation.shape.CircleShape)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            Text(
                                text = NoteUtils.NOTE_NAMES[note],
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Black Keys Overlay
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                blackKeys.forEach { note ->
                    if (note == -1) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            val isAllowed = allowedPitches.contains(note)
                            val isLastPlayed = lastNote != null && (lastNote % 12 == note)

                            val bgColor = when {
                                isLastPlayed && lastStatus == true -> Color(0xFF1B5E20)
                                isLastPlayed && lastStatus == false -> Color(0xFFB71C1C)
                                isAllowed -> Color(0xFF2E7D32)
                                else -> Color.Black
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .fillMaxHeight(0.65f)
                                    .align(Alignment.TopCenter)
                                    .background(bgColor)
                                    .border(1.dp, if (isLastPlayed) Color.Cyan else Color.DarkGray)
                                    .clickable {
                                        onNoteClick(note)
                                        val (status, ref) = MidiRhythmState.evaluateNoteAgainstTrack(note + 48, track)
                                        MidiRhythmState.lastEvaluatedNote = note + 48
                                        MidiRhythmState.lastEvaluatedStatus = status
                                        MidiRhythmState.lastEvaluatedNoteName = NoteUtils.NOTE_NAMES[note] + "3"
                                        MidiRhythmState.lastEvaluatedReference = ref
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (isAllowed) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Text(
                                        text = NoteUtils.NOTE_NAMES[note],
                                        color = Color.White,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
