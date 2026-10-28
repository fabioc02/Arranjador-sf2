package com.example.ui.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MidiState
import com.example.VelocityCurve
import com.example.domain.models.VoiceEngine
import com.example.domain.models.ChannelVoiceState
import com.example.domain.soundfont.model.SoundOutputEngine
import com.example.ui.arranger.SoundFontHelpDialog

@Composable
fun ExternalMidiControlPanel(onOpenRhythmCreator: (() -> Unit)? = null) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var showSoundFontHelpDialog by remember { mutableStateOf(false) }

    if (showSoundFontHelpDialog) {
        SoundFontHelpDialog(
            onDismiss = { showSoundFontHelpDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp).verticalScroll(scrollState)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("External MIDI Timbre Control", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("Controle Intercambiável: PSR-S750 e SoundFonts (SF2/SFZ)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (onOpenRhythmCreator != null) {
                Button(onClick = onOpenRhythmCreator, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF009688))) {
                    Text("Rhythm Creator")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))

        // Sound Engine Routing Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val soundEngine = MidiState.soundOutputEngine
                Column(modifier = Modifier.weight(1f)) {
                    Text("Motor Sonoro Ativo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        when (soundEngine) {
                            SoundOutputEngine.YAMAHA_HARDWARE -> "🎹 Yamaha PSR-S750 (Hardware MIDI)"
                            SoundOutputEngine.SOUNDFONT_INTERNAL -> "🎵 SoundFont Sintetizador Interno"
                            SoundOutputEngine.HYBRID_BOTH -> "🔀 Híbrido (Yamaha + SoundFont)"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            val next = when (soundEngine) {
                                SoundOutputEngine.YAMAHA_HARDWARE -> SoundOutputEngine.SOUNDFONT_INTERNAL
                                SoundOutputEngine.SOUNDFONT_INTERNAL -> SoundOutputEngine.HYBRID_BOTH
                                SoundOutputEngine.HYBRID_BOTH -> SoundOutputEngine.YAMAHA_HARDWARE
                            }
                            MidiState.setSoundOutput(next, context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (soundEngine) {
                                SoundOutputEngine.YAMAHA_HARDWARE -> Color(0xFF37474F)
                                SoundOutputEngine.SOUNDFONT_INTERNAL -> Color(0xFF6A1B9A)
                                SoundOutputEngine.HYBRID_BOTH -> Color(0xFF00695C)
                            }
                        ),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(soundEngine.shortName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showSoundFontHelpDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8)),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("📁 SoundFonts", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        
        // Touch Sensitivity Control
        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
            Text("Touch Sensitivity (Curva de Velocidade)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val curves = listOf(VelocityCurve.NORMAL to "Normal", VelocityCurve.SOFT to "Soft", VelocityCurve.HARD to "Hard", VelocityCurve.FIXED to "Fixed")
                curves.forEach { (curve, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = MidiState.velocityCurve == curve, onClick = { MidiState.velocityCurve = curve })
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (MidiState.velocityCurve == VelocityCurve.FIXED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fixed Velocity:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = MidiState.fixedVelocity.toFloat(), onValueChange = { MidiState.fixedVelocity = it.toInt() }, valueRange = 1f..127f, modifier = Modifier.weight(1f))
                    Text(MidiState.fixedVelocity.toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        PartControlSection("Right 1", VoiceEngine.CH_RIGHT1, { VoiceEngine.CH_RIGHT1 = it }, { showSoundFontHelpDialog = true })
        Spacer(modifier = Modifier.height(20.dp))
        PartControlSection("Right 2", VoiceEngine.CH_RIGHT2, { VoiceEngine.CH_RIGHT2 = it }, { showSoundFontHelpDialog = true })
        Spacer(modifier = Modifier.height(20.dp))
        PartControlSection("Left", VoiceEngine.CH_LEFT, { VoiceEngine.CH_LEFT = it }, { showSoundFontHelpDialog = true })
        
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))
        
        MidiDiagnosticPanel()
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        DSPControlPanel()
    }
}

@Composable
fun PartControlSection(
    title: String,
    channelIndex: Int,
    onChannelChange: (Int) -> Unit,
    onOpenSoundFontDialog: () -> Unit
) {
    val state = VoiceEngine.channels[channelIndex]
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = if (state.isSoundFontMode) Color(0xFF6A1B9A) else Color(0xFF00838F),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (state.isSoundFontMode) "SoundFont" else "PSR-S750",
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Switch(checked = state.isEnabled, onCheckedChange = { state.isEnabled = it })
        }
        
        if (state.isEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MIDI CH: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Slider(value = (channelIndex + 1).toFloat(), onValueChange = { onChannelChange(it.toInt() - 1) }, valueRange = 1f..16f, modifier = Modifier.weight(1f))
                Text("${channelIndex + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            
            InterchangeableVoiceSelector(
                label = "$title Timbre",
                channel = channelIndex,
                isSoundFont = state.isSoundFontMode,
                soundFontVoice = state.soundFontVoice,
                yamahaVoiceName = state.voiceName,
                yamahaMsb = state.msb,
                yamahaLsb = state.lsb,
                yamahaPc = state.pc,
                onModeChange = { isSf ->
                    state.isSoundFontMode = isSf
                    if (isSf) {
                        MidiState.ensureSoundFontAudioActive(context)
                        if (MidiState.soundOutputEngine == SoundOutputEngine.YAMAHA_HARDWARE) {
                            MidiState.setSoundOutput(SoundOutputEngine.SOUNDFONT_INTERNAL, context)
                        }
                        if (state.soundFontVoice == null && MidiState.soundFontRepository.availableVoices.isNotEmpty()) {
                            state.soundFontVoice = MidiState.soundFontRepository.availableVoices.first()
                        }
                    }
                    state.sendVoiceConfigToDevice()
                },
                onYamahaSelected = { voice ->
                    state.isSoundFontMode = false
                    state.voiceName = voice.name
                    state.msb = voice.msb
                    state.lsb = voice.lsb
                    state.pc = voice.pc
                    state.sendVoiceConfigToDevice()
                },
                onSoundFontSelected = { sf ->
                    state.isSoundFontMode = true
                    state.soundFontVoice = sf
                    state.voiceName = sf.name
                    MidiState.ensureSoundFontAudioActive(context)
                    if (MidiState.soundOutputEngine == SoundOutputEngine.YAMAHA_HARDWARE) {
                        MidiState.setSoundOutput(SoundOutputEngine.SOUNDFONT_INTERNAL, context)
                    }
                    state.sendVoiceConfigToDevice()
                },
                onOpenSoundFontDialog = onOpenSoundFontDialog
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            CcSlider("Volume (CC7)", state.volume, { state.volume = it }, { state.sendControlChange(7, state.volume) })
            CcSlider("Reverb (CC91)", state.reverb, { state.reverb = it }, { state.sendControlChange(91, state.reverb) })
            CcSlider("Chorus (CC93)", state.chorus, { state.chorus = it }, { state.sendControlChange(93, state.chorus) })
            CcSlider("DSP / Var (CC94)", state.dsp, { state.dsp = it }, { state.sendControlChange(94, state.dsp) })
        }
    }
}
