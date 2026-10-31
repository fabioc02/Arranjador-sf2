package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.material3.Text

@Composable
fun VirtualKeyboard(modifier: Modifier = Modifier) {
    val whiteKeyWidth = 48.dp
    val blackKeyWidth = 28.dp
    val keyHeight = 120.dp
    val blackKeyHeight = 70.dp
    
    val octaves = 5
    var baseOctave by remember { mutableStateOf(4) } // C4 = 48 or 60 depending on standard
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Octave: $baseOctave", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = { if (baseOctave > 0) baseOctave-- }) { Text("- Oct") }
                androidx.compose.material3.Button(onClick = { if (baseOctave < 7) baseOctave++ }) { Text("+ Oct") }
            }
        }
        LazyRow(modifier = Modifier.fillMaxWidth().height(keyHeight).background(Color.DarkGray)) {
            items(octaves) { octave ->
                val startNote = baseOctave * 12
                val octaveStartNote = startNote + octave * 12
                Box(modifier = Modifier.width(whiteKeyWidth * 7).height(keyHeight)) {
                    // White keys
                    Row(modifier = Modifier.fillMaxSize()) {
                        val whiteNotes = listOf(0, 2, 4, 5, 7, 9, 11)
                        for (i in 0..6) {
                            val note = octaveStartNote + whiteNotes[i]
                            PianoKey(
                                note = note,
                                isBlack = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(1.dp)
                            )
                        }
                    }
                    
                    // Black keys
                    val blackNotes = listOf(1, 3, 6, 8, 10)
                    val offsets = listOf(1, 2, 4, 5, 6) // number of white keys before it
                    for (i in 0..4) {
                        val note = octaveStartNote + blackNotes[i]
                        val offsetIndex = offsets[i]
                        Box(modifier = Modifier
                            .offset(x = (whiteKeyWidth * offsetIndex) - (blackKeyWidth / 2))
                            .width(blackKeyWidth)
                            .height(blackKeyHeight)
                        ) {
                            PianoKey(
                                note = note,
                                isBlack = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PianoKey(note: Int, isBlack: Boolean, modifier: Modifier = Modifier) {
    var isPressed by remember { mutableStateOf(false) }
    
    val color = if (isPressed) Color.Gray else if (isBlack) Color.Black else Color.White
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(color)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    val velocity = 127
                    try { com.example.domain.midi_core.MidiDispatcher.sendNoteOn(0, note, velocity) } catch(e: Throwable){}
                    
                    waitForUpOrCancellation()
                    isPressed = false
                    try { com.example.domain.midi_core.MidiDispatcher.sendNoteOff(0, note, 0) } catch(e: Throwable){}
                }
            }
    )
}
