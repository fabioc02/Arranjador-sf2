package com.example.domain.metronome

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.domain.midi_core.MidiDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MetronomePiecePreset(
    val id: String,
    val displayName: String,
    val downbeatNote: Int, // Beat 1 (accent)
    val upbeatNote: Int    // Other beats
)

/**
 * Global Metronome Configuration supporting Yamaha PSR-S750 hardware drum kits
 * and custom SoundFont drum pieces without envelope choking.
 */
object MetronomeConfig {

    private const val PREFS_NAME = "metronome_settings"

    val PRESETS = listOf(
        MetronomePiecePreset("woodblock", "Woodblock (Padrão Yamaha Clássico)", 76, 77),
        MetronomePiecePreset("bell_click", "Metronome Bell & Click (XG)", 34, 33),
        MetronomePiecePreset("sidestick", "Aro & Baquetas (Side Stick / Sticks)", 37, 31),
        MetronomePiecePreset("cowbell_claves", "Caneca & Claves (Cowbell / Claves)", 56, 75),
        MetronomePiecePreset("hihat", "Chimbal Fechado / Pedal", 42, 44),
        MetronomePiecePreset("custom", "Personalizado (Escolher Peça do Kit)", 76, 77)
    )

    // Complete General MIDI & Yamaha XG Drum Pieces mapping
    val AVAILABLE_DRUM_PIECES = listOf(
        Pair(27, "High Q"),
        Pair(28, "Slap"),
        Pair(29, "Scratch Push"),
        Pair(30, "Scratch Pull"),
        Pair(31, "Sticks (Baquetas)"),
        Pair(32, "Square Click"),
        Pair(33, "Metronome Click"),
        Pair(34, "Metronome Bell"),
        Pair(35, "Acoustic Bass Drum (Bumbo)"),
        Pair(36, "Bass Drum 1"),
        Pair(37, "Side Stick (Aro de Caixa)"),
        Pair(38, "Acoustic Snare (Caixa)"),
        Pair(39, "Hand Clap (Palmas)"),
        Pair(40, "Electric Snare"),
        Pair(41, "Low Floor Tom"),
        Pair(42, "Closed Hi-Hat (Chimbal Fechado)"),
        Pair(43, "High Floor Tom"),
        Pair(44, "Pedal Hi-Hat (Chimbal Pedal)"),
        Pair(45, "Low Tom"),
        Pair(46, "Open Hi-Hat (Chimbal Aberto)"),
        Pair(47, "Low-Mid Tom"),
        Pair(48, "Hi-Mid Tom"),
        Pair(49, "Crash Cymbal 1 (Prato Ataque)"),
        Pair(50, "High Tom"),
        Pair(51, "Ride Cymbal 1 (Prato Condução)"),
        Pair(52, "Chinese Cymbal"),
        Pair(53, "Ride Bell (Cúpula)"),
        Pair(54, "Tambourine (Pandeirola)"),
        Pair(55, "Splash Cymbal"),
        Pair(56, "Cowbell (Caneca)"),
        Pair(57, "Crash Cymbal 2"),
        Pair(58, "Vibraslap"),
        Pair(59, "Ride Cymbal 2"),
        Pair(60, "Hi Bongo"),
        Pair(61, "Low Bongo"),
        Pair(62, "Mute Hi Conga"),
        Pair(63, "Open Hi Conga"),
        Pair(64, "Low Conga"),
        Pair(65, "High Timbale"),
        Pair(66, "Low Timbale"),
        Pair(67, "High Agogo"),
        Pair(68, "Low Agogo"),
        Pair(69, "Cabasa"),
        Pair(70, "Maracas"),
        Pair(71, "Short Whistle"),
        Pair(72, "Long Whistle"),
        Pair(73, "Short Guiro"),
        Pair(74, "Long Guiro"),
        Pair(75, "Claves"),
        Pair(76, "Hi Wood Block"),
        Pair(77, "Low Wood Block"),
        Pair(78, "Mute Cuica"),
        Pair(79, "Open Cuica"),
        Pair(80, "Mute Triangle"),
        Pair(81, "Open Triangle"),
        Pair(82, "Shaker")
    )

    var enabled by mutableStateOf(false)
    var channel by mutableIntStateOf(9) // MIDI Channel 10 (0-indexed 9)
    var selectedPresetId by mutableStateOf("woodblock")
    var downbeatNote by mutableIntStateOf(76) // Accent beat 1
    var upbeatNote by mutableIntStateOf(77)   // Beats 2, 3, 4
    var volume by mutableIntStateOf(110)      // CC 7
    var accentVelocity by mutableIntStateOf(120)
    var normalVelocity by mutableIntStateOf(85)

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean("enabled", false)
        channel = prefs.getInt("channel", 9)
        selectedPresetId = prefs.getString("preset_id", "woodblock") ?: "woodblock"
        downbeatNote = prefs.getInt("downbeat_note", 76)
        upbeatNote = prefs.getInt("upbeat_note", 77)
        volume = prefs.getInt("volume", 110)
        accentVelocity = prefs.getInt("accent_vel", 120)
        normalVelocity = prefs.getInt("normal_vel", 85)
        isInitialized = true
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("enabled", enabled)
            .putInt("channel", channel)
            .putString("preset_id", selectedPresetId)
            .putInt("downbeat_note", downbeatNote)
            .putInt("upbeat_note", upbeatNote)
            .putInt("volume", volume)
            .putInt("accent_vel", accentVelocity)
            .putInt("normal_vel", normalVelocity)
            .apply()
    }

    fun applyPreset(preset: MetronomePiecePreset) {
        selectedPresetId = preset.id
        downbeatNote = preset.downbeatNote
        upbeatNote = preset.upbeatNote
    }

    /**
     * Plays an audible test click using the active audio engine (Yamaha HW or SoundFont)
     * with proper note-off spacing to eliminate envelope cut-off/choke.
     */
    fun testClick(isDownbeat: Boolean = true) {
        val note = if (isDownbeat) downbeatNote else upbeatNote
        val vel = if (isDownbeat) accentVelocity else normalVelocity
        val ch = channel

        // Ensure volume and expression are active on metronome drum channel
        MidiDispatcher.sendControlChange(ch, 7, volume, 0L)
        MidiDispatcher.sendControlChange(ch, 11, 127, 0L)

        // Trigger Note On
        MidiDispatcher.sendNoteOn(ch, note, vel, 0L)

        // Send Note Off after 120ms to allow natural sample acoustic decay
        CoroutineScope(Dispatchers.IO).launch {
            delay(120)
            MidiDispatcher.sendNoteOff(ch, note, 0, 0L)
        }
    }
}
