package com.example.ui.validation

import android.net.Uri
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.parser.StyParser
import com.example.domain.models.StyFile
import com.example.domain.midi.MidiFileWriter
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun ValidationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var styFile by remember { mutableStateOf<StyFile?>(null) }
    var logText by remember { mutableStateOf("Aguardando arquivo .sty...\n") }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var tempMidiFile by remember { mutableStateOf<File?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                logText += "Carregando arquivo: ${it.lastPathSegment}\n"
                try {
                    val bytes = context.contentResolver.openInputStream(it)?.readBytes() ?: return@launch
                    val parser = StyParser()
                    styFile = parser.parseBytes(bytes, it.lastPathSegment ?: "unknown.sty")
                    logText += "Parser concluído: ${styFile?.metrics?.tracksCount} tracks, ${styFile?.metrics?.totalEvents} eventos.\n"
                    logText += "Seções encontradas: ${styFile?.sections?.keys?.joinToString()}\n"
                } catch (e: Exception) {
                    logText += "Erro ao fazer parse: ${e.message}\n"
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Sprint 10.5 - Yamaha Validation", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { launcher.launch("*/*") }) {
                Text("Abrir Style (.sty)")
            }
            
            Button(
                onClick = {
                    styFile?.let { sf ->
                        scope.launch {
                            logText += "Extraindo bateria (Canal 10)...\n"
                            try {
                                val allDrumEvents = mutableListOf<com.example.domain.models.MidiEvent>()
                                // A bateria na Yamaha normalmente fica no canal 9 (10-1 = 9 zero-indexed)
                                // Rhythm 1 (9), Rhythm 2 (10) em 1-index. Em 0-index: 8 e 9.
                                sf.sections.values.forEach { section ->
                                    section.tracks.values.forEach { track ->
                                        track.events.forEach { ev ->
                                            when (ev) {
                                                is com.example.domain.models.MidiEvent.NoteOn -> if (ev.channel == 8 || ev.channel == 9) allDrumEvents.add(ev)
                                                is com.example.domain.models.MidiEvent.NoteOff -> if (ev.channel == 8 || ev.channel == 9) allDrumEvents.add(ev)
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                                
                                val writer = MidiFileWriter()
                                val midiBytes = writer.writeToBytes(allDrumEvents, sf.ppq, sf.defaultBpm)
                                
                                val tempFile = File.createTempFile("solo_drums", ".mid", context.cacheDir)
                                FileOutputStream(tempFile).use { it.write(midiBytes) }
                                tempMidiFile = tempFile
                                logText += "Arquivo MIDI temporário gerado: ${tempFile.length()} bytes.\n"
                            } catch (e: Exception) {
                                logText += "Erro ao gerar MIDI: ${e.message}\n"
                            }
                        }
                    }
                },
                enabled = styFile != null
            ) {
                Text("Gerar MIDI Bateria")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    tempMidiFile?.let { file ->
                        try {
                            if (mediaPlayer == null) {
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(file.absolutePath)
                                    setOnCompletionListener { 
                                        isPlaying = false 
                                        logText += "Reprodução concluída.\n"
                                    }
                                    prepare()
                                }
                            }
                            mediaPlayer?.start()
                            isPlaying = true
                            logText += "Reproduzindo usando MediaPlayer interno (SF2 nativo do Android)...\n"
                        } catch (e: Exception) {
                            logText += "Erro no player: ${e.message}\n"
                        }
                    }
                },
                enabled = tempMidiFile != null && !isPlaying
            ) {
                Text("Play")
            }
            
            Button(
                onClick = {
                    mediaPlayer?.pause()
                    mediaPlayer?.seekTo(0)
                    isPlaying = false
                    logText += "Stop.\n"
                },
                enabled = isPlaying
            ) {
                Text("Stop")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Validation Log", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = logText,
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
