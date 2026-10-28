package com.example

// Triggering fresh deployment 2
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip

import com.example.ui.arranger.SettingsScreen
import com.example.ui.arranger.VoicesScreen



import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.media.midi.MidiDevice
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat


enum class VelocityCurve {
    NORMAL, SOFT, HARD, FIXED
}

data class VoicePreset(
    val name: String,
    val r1BankMsb: Int, val r1BankLsb: Int, val r1Program: Int,
    val r1Volume: Int = 100, val r1Pan: Int = 64, val r1Reverb: Int = 40, val r1Chorus: Int = 0, val r1Cutoff: Int = 64, val r1Resonance: Int = 64,
    val r1Attack: Int = 64, val r1Decay: Int = 64, val r1Release: Int = 64, val r1VibratoRate: Int = 64, val r1VibratoDepth: Int = 64, val r1VibratoDelay: Int = 64, val r1Variation: Int = 0,
    val r2BankMsb: Int, val r2BankLsb: Int, val r2Program: Int,
    val r2Volume: Int = 100, val r2Pan: Int = 64, val r2Reverb: Int = 40, val r2Chorus: Int = 0, val r2Cutoff: Int = 64, val r2Resonance: Int = 64,
    val r2Attack: Int = 64, val r2Decay: Int = 64, val r2Release: Int = 64, val r2VibratoRate: Int = 64, val r2VibratoDepth: Int = 64, val r2VibratoDelay: Int = 64, val r2Variation: Int = 0,
    val l1BankMsb: Int = 104, val l1BankLsb: Int = 0, val l1Program: Int = 0,
    val l1Volume: Int = 100, val l1Pan: Int = 64, val l1Reverb: Int = 40, val l1Chorus: Int = 0, val l1Cutoff: Int = 64, val l1Resonance: Int = 64,
    val l1Attack: Int = 64, val l1Decay: Int = 64, val l1Release: Int = 64, val l1VibratoRate: Int = 64, val l1VibratoDepth: Int = 64, val l1VibratoDelay: Int = 64, val l1Variation: Int = 0
)

val gmCategories = listOf("Piano", "Chromatic Perc", "Organ", "Guitar", "Bass", "Strings", "Ensemble", "Brass", "Reed", "Pipe", "Synth Lead", "Synth Pad", "Synth FX", "Ethnic", "Percussive", "Sound FX")

val defaultVoicePresets = listOf(
    VoicePreset("Piano Grand", 
        104, 0, 0, r1Reverb = 60, r1Chorus = 0, r1Attack = 64, r1Release = 64, 
        r2BankMsb = 104, r2BankLsb = 0, r2Program = 0, r2Volume = 0, // Layer 2 muted
        l1BankMsb = 104, l1BankLsb = 0, l1Program = 32, l1Volume = 60), // Acoustic Bass on Left
    VoicePreset("Cinematic", 
        104, 0, 48, r1Volume = 110, r1Reverb = 100, r1Chorus = 60, r1Attack = 80, r1Release = 80, r1Variation = 64,
        r2BankMsb = 104, r2BankLsb = 0, r2Program = 49, r2Volume = 90, r2Reverb = 110, r2Chorus = 60, r2Attack = 90, r2Release = 90, r2Variation = 64,
        l1BankMsb = 104, l1BankLsb = 0, l1Program = 48, l1Volume = 80, l1Reverb = 110, l1Attack = 90, l1Release = 90),
    VoicePreset("Rock Guitar", 
        104, 0, 30, r1Volume = 110, r1Reverb = 40, r1Chorus = 20, r1Cutoff = 80, r1Variation = 80,
        r2BankMsb = 104, r2BankLsb = 0, r2Program = 29, r2Volume = 80, r2Reverb = 40, r2Cutoff = 70,
        l1BankMsb = 104, l1BankLsb = 0, l1Program = 33, l1Volume = 90), // Fingered Bass
    VoicePreset("Gospel Organ", 
        104, 0, 16, r1Volume = 100, r1Reverb = 50, r1Chorus = 80, r1Release = 50, r1Variation = 90,
        r2BankMsb = 104, r2BankLsb = 0, r2Program = 17, r2Volume = 100, r2Reverb = 50, r2Chorus = 90, r2Variation = 90,
        l1BankMsb = 104, l1BankLsb = 0, l1Program = 16, l1Volume = 80, l1Reverb = 50, l1Chorus = 80)
)

object MidiState {
    var isArrangerMode by mutableStateOf(false)
    var currentTab = "arranger"
    var splitPoint by mutableStateOf(54)
    var keyboardTranspose by mutableStateOf(0)
    var velocityCurve by mutableStateOf(VelocityCurve.NORMAL)
    var fixedVelocity by mutableStateOf(100)
    var midiSchedulerMode by mutableStateOf(com.example.domain.midi.MidiSchedulerMode.AUTO)
    var soundOutputEngine by mutableStateOf(com.example.domain.soundfont.model.SoundOutputEngine.YAMAHA_HARDWARE)
    val soundFontRepository = com.example.domain.soundfont.repository.OnDemandSoundFontRepository()
    val soundFontSynth = com.example.domain.soundfont.synth.SoundFontSynthEngine(soundFontRepository)
    val audioTrackBackend = com.example.domain.audio.AudioTrackBackend()
    var voiceManager: com.example.domain.arranger.VoiceManager? = null

    fun ensureSoundFontAudioActive(context: android.content.Context) {
        audioTrackBackend.attachContext(context)
        audioTrackBackend.setRenderer(object : com.example.domain.audio.AudioRenderer {
            override fun render(buffer: FloatArray, frameCount: Int) {
                soundFontSynth.render(buffer, 0, frameCount)
            }
        })
        audioTrackBackend.start(44100, 256)
    }

    fun setSoundOutput(engine: com.example.domain.soundfont.model.SoundOutputEngine, context: android.content.Context? = null) {
        soundOutputEngine = engine
        if (engine != com.example.domain.soundfont.model.SoundOutputEngine.YAMAHA_HARDWARE) {
            context?.let { audioTrackBackend.attachContext(it) }
            audioTrackBackend.setRenderer(object : com.example.domain.audio.AudioRenderer {
                override fun render(buffer: FloatArray, frameCount: Int) {
                    soundFontSynth.render(buffer, 0, frameCount)
                }
            })
            audioTrackBackend.start(44100, 256)
        } else {
            soundFontSynth.panic()
        }
    }

    val midiInputPorts = java.util.concurrent.CopyOnWriteArrayList<android.media.midi.MidiInputPort>()
    private val buffer3 = object : ThreadLocal<ByteArray>() { override fun initialValue() = ByteArray(3) }
    private val buffer2 = object : ThreadLocal<ByteArray>() { override fun initialValue() = ByteArray(2) }

    val safetyManager = com.example.domain.midi.MidiSafetyManager(
        sendNoteOff = { ch, note, vel, ts ->
            sendRawNoteOff(ch, note, vel, ts)
        },
        sendControlChange = { ch, ctrl, value, ts ->
            sendRawControlChange(ch, ctrl, value, ts)
        }
    )

    fun panicMidi(timestampNs: Long = System.nanoTime()) {
        safetyManager.panicMidi(timestampNs)
    }
    
        val midiListeners = java.util.concurrent.CopyOnWriteArrayList<(status: Int, data1: Int, data2: Int) -> Unit>()
    val timestampedMidiListeners = java.util.concurrent.CopyOnWriteArrayList<(status: Int, data1: Int, data2: Int, timestampNs: Long) -> Unit>()
    val rawMidiListeners = java.util.concurrent.CopyOnWriteArrayList<(ByteArray) -> Unit>()
    val programChangeListeners = java.util.concurrent.CopyOnWriteArrayList<(channel: Int, bankMsb: Int, bankLsb: Int, program: Int) -> Unit>()
    
    val liveDrumParams = androidx.compose.runtime.mutableStateMapOf<Int, com.example.DrumKeyParams>()

    fun dispatchMidi(status: Int, data1: Int, data2: Int, timestampNs: Long = 0L) {
        val effectiveTimestamp = if (timestampNs != 0L) timestampNs else System.nanoTime()
        for (listener in timestampedMidiListeners) {
            try {
                listener(status, data1, data2, effectiveTimestamp)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        for (listener in midiListeners) {
            try {
                listener(status, data1, data2)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    
    fun applyVelocityCurve(velocity: Int): Int {
        if (velocity == 0) return 0
        return when (velocityCurve) {
            VelocityCurve.NORMAL -> velocity
            VelocityCurve.SOFT -> (Math.pow(velocity / 127.0, 0.5) * 127).toInt().coerceIn(1, 127)
            VelocityCurve.HARD -> (Math.pow(velocity / 127.0, 2.0) * 127).toInt().coerceIn(1, 127)
            VelocityCurve.FIXED -> fixedVelocity.coerceIn(1, 127)
        }
    }
    
    fun sendProgramChangeRaw(channel: Int, program: Int, timestampNs: Long = System.nanoTime()) {
        val ports = midiInputPorts
        if (ports.isEmpty()) return
        val buffer = buffer2.get()!!
        val timestamp: Long = timestampNs
        ports.forEach { port ->
            try {
                buffer[0] = (0xC0 or (channel and 0x0F)).toByte()
                buffer[1] = (program and 0x7F).toByte()
                port.send(buffer, 0, 2, 0)
            } catch (e: Exception) {}
        }
    }

    fun sendProgramChange(channel: Int, bankMsb: Int, bankLsb: Int, program: Int, timestampNs: Long = System.nanoTime()) {
        programChangeListeners.forEach {
            try { it(channel, bankMsb, bankLsb, program) } catch (e: Exception) {}
        }
        val ports = midiInputPorts
        if (ports.isEmpty()) return
        
        val buffer = ByteArray(3)
        val timestamp: Long = timestampNs
        
        ports.forEach { port ->
            try {
                // CC0 Bank MSB
                buffer[0] = (0xB0 or (channel and 0x0F)).toByte()
                buffer[1] = 0x00.toByte()
                buffer[2] = (bankMsb and 0x7F).toByte()
                port.send(buffer, 0, 3, 0)
                
                // CC32 Bank LSB
                buffer[0] = (0xB0 or (channel and 0x0F)).toByte()
                buffer[1] = 0x20.toByte()
                buffer[2] = (bankLsb and 0x7F).toByte()
                port.send(buffer, 0, 3, 0)
                
                // Program Change
                buffer[0] = (0xC0 or (channel and 0x0F)).toByte()
                buffer[1] = (program and 0x7F).toByte()
                port.send(buffer, 0, 2, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendControlChange(channel: Int, control: Int, value: Int, timestampNs: Long = System.nanoTime()) {
        safetyManager.registerChannelUsed(channel)
        sendRawControlChange(channel, control, value, timestampNs)
    }

    private fun sendRawControlChange(channel: Int, control: Int, value: Int, timestampNs: Long = System.nanoTime()) {
        val ports = midiInputPorts
        if (ports.isEmpty()) return
        val buffer = buffer3.get()!!
        buffer[0] = (0xB0 or (channel and 0x0F)).toByte()
        buffer[1] = (control and 0x7F).toByte()
        buffer[2] = (value and 0x7F).toByte()
        ports.forEach { port ->
            try {
                port.send(buffer, 0, 3, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendChannelPressure(channel: Int, pressure: Int, timestampNs: Long = System.nanoTime()) {
        val ports = midiInputPorts
        if (ports.isEmpty()) return
        
        val buffer = buffer2.get()!!
        val timestamp: Long = timestampNs
        
        ports.forEach { port ->
            try {
                buffer[0] = (0xD0 or (channel and 0x0F)).toByte()
                buffer[1] = (pressure and 0x7F).toByte()
                port.send(buffer, 0, 2, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendPitchBend(channel: Int, lsb: Int, msb: Int, timestampNs: Long = System.nanoTime()) {
        val ports = midiInputPorts
        if (ports.isEmpty()) return
        
        val buffer = buffer3.get()!!
        val timestamp: Long = timestampNs
        
        ports.forEach { port ->
            try {
                buffer[0] = (0xE0 or (channel and 0x0F)).toByte()
                buffer[1] = (lsb and 0x7F).toByte()
                buffer[2] = (msb and 0x7F).toByte()
                port.send(buffer, 0, 3, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendSysEx(data: ByteArray, timestampNs: Long = System.nanoTime()) {
        val ports = midiInputPorts
        if (ports.isEmpty()) return
        ports.forEach { port ->
            try {
                port.send(data, 0, data.size, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendNoteOn(channel: Int, note: Int, velocity: Int, timestampNs: Long = System.nanoTime()) {
        safetyManager.onNoteOn(channel, note, velocity)
        sendRawNoteOn(channel, note, velocity, timestampNs)
    }

    private fun sendRawNoteOn(channel: Int, note: Int, velocity: Int, timestampNs: Long = System.nanoTime()) {
        val ports = midiInputPorts
        if (ports.isEmpty()) return
        val buffer = buffer3.get()!!
        buffer[0] = (0x90 or (channel and 0x0F)).toByte()
        buffer[1] = (note and 0x7F).toByte()
        buffer[2] = (velocity and 0x7F).toByte()
        ports.forEach { port ->
            try {
                port.send(buffer, 0, 3, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendNoteOff(channel: Int, note: Int, velocity: Int, timestampNs: Long = System.nanoTime()) {
        safetyManager.onNoteOff(channel, note)
        sendRawNoteOff(channel, note, velocity, timestampNs)
    }

    private fun sendRawNoteOff(channel: Int, note: Int, velocity: Int, timestampNs: Long = System.nanoTime()) {
        val ports = midiInputPorts
        if (ports.isEmpty()) return
        val buffer = buffer3.get()!!
        buffer[0] = (0x80 or (channel and 0x0F)).toByte()
        buffer[1] = (note and 0x7F).toByte()
        buffer[2] = (velocity and 0x7F).toByte()
        ports.forEach { port ->
            try {
                port.send(buffer, 0, 3, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

var pendingSharedUrl by mutableStateOf("")

class MainActivity : ComponentActivity() {
    private var midiManager: MidiManager? = null
    private val midiDevices = mutableListOf<android.media.midi.MidiDevice>()
    private val midiPorts = mutableListOf<android.media.midi.MidiOutputPort>()
    private val midiReceivers = mutableListOf<android.media.midi.MidiReceiver>()

    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent) {
        if (intent.action == android.content.Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            if (sharedText != null) {
                // Extract URL if there is extra text (like "Check out this video: https://...")
                val urlRegex = "https?://[a-zA-Z0-9./?=_-]+".toRegex()
                val match = urlRegex.find(sharedText)
                if (match != null) {
                    pendingSharedUrl = match.value
                    android.widget.Toast.makeText(this, "URL received! Select a pad and use 'Web Audio'.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    pendingSharedUrl = sharedText
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup MIDI
        midiManager = getSystemService(Context.MIDI_SERVICE) as? MidiManager
        setupMidi()
        com.example.domain.midi.GlobalMidiRouter.start()
        handleIntent(intent)

        // Initialize SoundFont library indexing in background
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.example.domain.soundfont.repository.SoundFontStorageManager.scanAll(this@MainActivity)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var showRhythmCreator by remember { mutableStateOf(false) }
                
                var currentTab by remember { mutableStateOf("arranger") }
                LaunchedEffect(currentTab) {
                    MidiState.currentTab = currentTab
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        ScrollableTabRow(
                            selectedTabIndex = when(currentTab) {
                                "arranger" -> 0
                                "creator" -> 1
                                "voices" -> 2
                                "midi" -> 3
                                else -> 0
                            },
                            edgePadding = 8.dp
                        ) {
                            Tab(
                                selected = currentTab == "arranger",
                                onClick = { currentTab = "arranger" },
                                text = { Text("Arranjador") },
                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Arranjador") }
                            )
                            Tab(
                                selected = currentTab == "creator",
                                onClick = { currentTab = "creator" },
                                text = { Text("Criador") },
                                icon = { Icon(Icons.Default.Edit, contentDescription = "Ritmos") }
                            )
                            Tab(
                                selected = currentTab == "voices",
                                onClick = { currentTab = "voices" },
                                text = { Text("Drums(Live)") },
                                icon = { Icon(Icons.Default.List, contentDescription = "Timbres") }
                            )
                            Tab(
                                selected = currentTab == "midi",
                                onClick = { currentTab = "midi" },
                                text = { Text("MIDI") },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "MIDI") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentTab) {
                            "arranger" -> {
                                com.example.ui.arranger.ArrangerScreen(
                                    onOpenSettings = { currentTab = "midi" }
                                )
                            }
                            "creator" -> {
                                // Full screen creator view 
                                com.example.MidiRhythmCreatorDialog(
                                    visible = true,
                                    onDismiss = { currentTab = "arranger" }
                                )
                            }
                            "voices" -> {
                                VoicesScreen(androidx.lifecycle.viewmodel.compose.viewModel())
                            }
                            "midi" -> {
                                SettingsScreen(
                                    viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                                    onBack = { currentTab = "arranger" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
    
    private fun setupMidi() {

        val handler = Handler(Looper.getMainLooper())
        midiManager?.devices?.forEach { deviceInfo ->
            openMidiDevice(deviceInfo, handler)
        }
        
        midiManager?.registerDeviceCallback(object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(device: android.media.midi.MidiDeviceInfo) {
                openMidiDevice(device, handler)
            }
            override fun onDeviceRemoved(device: android.media.midi.MidiDeviceInfo) {
                // Remove from midiInputPorts to prevent exceptions
                val iterator = MidiState.midiInputPorts.iterator()
                while (iterator.hasNext()) {
                    val port = iterator.next()
                    try {
                        port.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                MidiState.midiInputPorts.clear()

                val portsIterator = midiPorts.iterator()
                while (portsIterator.hasNext()) {
                    val port = portsIterator.next()
                    try {
                        port.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                midiPorts.clear()

                val deviceIterator = midiDevices.iterator()
                while (deviceIterator.hasNext()) {
                    val dev = deviceIterator.next()
                    if (dev.info.id == device.id) {
                        try {
                            dev.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        deviceIterator.remove()
                    }
                }
                midiReceivers.clear()
            }
        }, handler)
    }
    
    private fun openMidiDevice(deviceInfo: android.media.midi.MidiDeviceInfo, handler: Handler) {
        val numOutputs = deviceInfo.outputPortCount
        val numInputs = deviceInfo.inputPortCount
        if (numOutputs > 0 || numInputs > 0) {
            midiManager?.openDevice(deviceInfo, { device ->
                if (device != null) {
                    midiDevices.add(device)
                    if (numInputs > 0) {
                        for (i in 0 until numInputs) {
                            val inputPort = device.openInputPort(i)
                            if (inputPort != null) {
                                android.util.Log.d("MIDI-OPEN", "Added inputPort from device: ${deviceInfo.properties.getString(android.media.midi.MidiDeviceInfo.PROPERTY_NAME)}")
                                MidiState.midiInputPorts.add(inputPort)
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    android.widget.Toast.makeText(this@MainActivity, "MIDI Conectado (Out): " + deviceInfo.properties.getString(android.media.midi.MidiDeviceInfo.PROPERTY_NAME), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    
                    if (numOutputs > 0) {
                        for (outIndex in 0 until numOutputs) {
                        val outputPort = device.openOutputPort(outIndex)
                        if (outputPort != null) {
                        android.util.Log.d("MIDI-OPEN", "Added outputPort $outIndex from device: ${deviceInfo.properties.getString(android.media.midi.MidiDeviceInfo.PROPERTY_NAME)}")
                        val receiver = object : MidiReceiver() {
                            var runningStatus = 0
                            var inSysEx = false
                            override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                                // 1. Send the exact raw buffer to rawMidiListeners (handling slicing)
                                val rawMsg = msg.sliceArray(offset until offset + count)
                                MidiState.rawMidiListeners.forEach { it(rawMsg) }

                                // 2. Original parser for ArrangerEngine (Notes/Chords)
                                var i = 0
                                while (i < count) {
                                    var status = msg[offset + i].toInt() and 0xFF
                                    
                                    if (inSysEx) {
                                        while (i < count && (msg[offset + i].toInt() and 0xFF) != 0xF7) {
                                            i++
                                        }
                                        if (i < count) {
                                            i++ // consume F7
                                            inSysEx = false
                                        }
                                        continue
                                    }
                                    
                                    if (status >= 0xF8) {
                                        i++
                                        continue // Ignore realtime for note processing
                                    }
                                    
                                    if (status == 0xF0) {
                                        inSysEx = true
                                        while (i < count && (msg[offset + i].toInt() and 0xFF) != 0xF7) {
                                            i++
                                        }
                                        if (i < count) {
                                            i++ // consume F7
                                            inSysEx = false
                                        }
                                        continue
                                    }
                                    
                                    if (status >= 0x80) {
                                        runningStatus = status
                                        i++
                                    } else {
                                        status = runningStatus
                                    }
                                    
                                    val command = status and 0xF0
                                    val dataLength = if (command == 0xC0 || command == 0xD0) 1 else 2
                                    
                                    if (i + dataLength <= count) {
                                        val data1 = msg[offset + i].toInt() and 0x7F
                                        val data2 = if (dataLength == 2) msg[offset + i + 1].toInt() and 0x7F else 0
                                        i += dataLength
                                        
                                        if (command >= 0x80 && command < 0xF0) {
                                            MidiState.dispatchMidi(status, data1, data2, timestamp)
                                        }
                                        
                                        if (dataLength == 2 && (command == 0x90 || command == 0x80)) {
                                            val note = data1
                                            val rawVelocity = data2
                                            val velocity = if (command == 0x90 && rawVelocity > 0) MidiState.applyVelocityCurve(rawVelocity) else rawVelocity
                                            val channel = status and 0x0F
                                            val isRightVoice = (channel == 0 || channel == 1) && note > MidiState.splitPoint
                                        }
                                    } else {
                                        break
                                    }
                                }
                            }
                        }
                        outputPort.connect(receiver)
                        
                        midiDevices.add(device)
                        midiPorts.add(outputPort)
                        midiReceivers.add(receiver)
                        }
                        }
                    }
                }
            }, handler)
        }
    }
}

// Triggering fresh deployment 3
// Triggering fresh deployment 4
