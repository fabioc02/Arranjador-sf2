package com.example.domain.arranger

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.models.YamahaVoice
import com.example.domain.soundfont.model.SoundFontType
import com.example.domain.soundfont.model.SoundFontVoice

enum class ChannelVoiceMode {
    YAMAHA_HARDWARE,
    SOUNDFONT
}

class VoiceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_mappings", Context.MODE_PRIVATE)
    
    // channel (0-15) -> Mode (Yamaha HW vs SoundFont)
    val channelVoiceMode = mutableMapOf<Int, ChannelVoiceMode>()

    // channel (0-15) -> YamahaVoice
    val channelRemap = mutableMapOf<Int, YamahaVoice>()

    // channel (0-15) -> SoundFontVoice
    val channelSoundFontRemap = mutableMapOf<Int, SoundFontVoice>()

    val channelVolumes = mutableMapOf<Int, Int>()  // CC 7 (0-127)
    val channelPans = mutableMapOf<Int, Int>()     // CC 10 (0-127)
    val channelReverbs = mutableMapOf<Int, Int>()  // CC 91 (0-127)
    val channelChoruses = mutableMapOf<Int, Int>() // CC 93 (0-127)

    // originalNote -> targetNote for Drum setup
    val drumNoteRemap = mutableMapOf<Int, Int>()

    init {
        loadMappings()
    }

    private fun loadMappings() {
        channelVoiceMode.clear()
        channelRemap.clear()
        channelSoundFontRemap.clear()
        channelVolumes.clear()
        channelPans.clear()
        channelReverbs.clear()
        channelChoruses.clear()

        for (channel in 0..15) {
            val modeStr = prefs.getString("ch${channel}_mode", ChannelVoiceMode.YAMAHA_HARDWARE.name)
            val mode = try {
                ChannelVoiceMode.valueOf(modeStr ?: ChannelVoiceMode.YAMAHA_HARDWARE.name)
            } catch (e: Exception) {
                ChannelVoiceMode.YAMAHA_HARDWARE
            }
            channelVoiceMode[channel] = mode

            // Yamaha Voice
            val name = prefs.getString("ch${channel}_name", null)
            if (name != null) {
                val msb = prefs.getInt("ch${channel}_msb", 127)
                val lsb = prefs.getInt("ch${channel}_lsb", 0)
                val pc = prefs.getInt("ch${channel}_pc", 0)
                channelRemap[channel] = YamahaVoice(name, msb, lsb, pc)
            }

            // SoundFont Voice
            val sfName = prefs.getString("ch${channel}_sf_name", null)
            val sfPath = prefs.getString("ch${channel}_sf_path", null)
            if (sfName != null && sfPath != null) {
                val sfId = prefs.getString("ch${channel}_sf_id", "$sfPath:0:0") ?: "$sfPath:0:0"
                val sfCat = prefs.getString("ch${channel}_sf_cat", "Outros") ?: "Outros"
                val sfTypeStr = prefs.getString("ch${channel}_sf_type", SoundFontType.SF2.name)
                val sfType = if (sfTypeStr == SoundFontType.SFZ.name) SoundFontType.SFZ else SoundFontType.SF2
                val sfBank = prefs.getInt("ch${channel}_sf_bank", 0)
                val sfProg = prefs.getInt("ch${channel}_sf_prog", 0)
                val sfSize = prefs.getString("ch${channel}_sf_size", "") ?: ""

                val sfVoice = SoundFontVoice(
                    id = sfId,
                    name = sfName,
                    category = sfCat,
                    filePath = sfPath,
                    fileType = sfType,
                    bank = sfBank,
                    program = sfProg,
                    fileSizeFormatted = sfSize
                )
                channelSoundFontRemap[channel] = sfVoice
                
                // If mode is SoundFont, notify sound engine
                if (mode == ChannelVoiceMode.SOUNDFONT) {
                    com.example.MidiState.soundFontSynth.setChannelVoice(channel, sfVoice)
                }
            }

            channelVolumes[channel] = prefs.getInt("ch${channel}_vol", 100)
            channelPans[channel] = prefs.getInt("ch${channel}_pan", 64)
            channelReverbs[channel] = prefs.getInt("ch${channel}_rev", 40)
            channelChoruses[channel] = prefs.getInt("ch${channel}_cho", 0)
        }

        drumNoteRemap.clear()
        for (note in 0..127) {
            val targetNote = prefs.getInt("drum_remap_${note}", -1)
            if (targetNote != -1) {
                drumNoteRemap[note] = targetNote
            }
        }
    }

    fun setMapping(channel: Int, voice: YamahaVoice) {
        channelVoiceMode[channel] = ChannelVoiceMode.YAMAHA_HARDWARE
        channelRemap[channel] = voice
        // Unbind soundfont override from synth
        com.example.MidiState.soundFontSynth.setChannelVoice(channel, null)

        prefs.edit()
            .putString("ch${channel}_mode", ChannelVoiceMode.YAMAHA_HARDWARE.name)
            .putString("ch${channel}_name", voice.name)
            .putInt("ch${channel}_msb", voice.msb)
            .putInt("ch${channel}_lsb", voice.lsb)
            .putInt("ch${channel}_pc", voice.pc)
            .apply()
    }

    fun setSoundFontMapping(channel: Int, voice: SoundFontVoice) {
        channelVoiceMode[channel] = ChannelVoiceMode.SOUNDFONT
        channelSoundFontRemap[channel] = voice
        // Bind directly to SoundFont synth
        com.example.MidiState.soundFontSynth.setChannelVoice(channel, voice)

        prefs.edit()
            .putString("ch${channel}_mode", ChannelVoiceMode.SOUNDFONT.name)
            .putString("ch${channel}_sf_id", voice.id)
            .putString("ch${channel}_sf_name", voice.name)
            .putString("ch${channel}_sf_path", voice.filePath)
            .putString("ch${channel}_sf_cat", voice.category)
            .putString("ch${channel}_sf_type", voice.fileType.name)
            .putInt("ch${channel}_sf_bank", voice.bank)
            .putInt("ch${channel}_sf_prog", voice.program)
            .putString("ch${channel}_sf_size", voice.fileSizeFormatted)
            .apply()
    }

    fun setVoiceMode(channel: Int, mode: ChannelVoiceMode) {
        channelVoiceMode[channel] = mode
        if (mode == ChannelVoiceMode.SOUNDFONT) {
            val sfVoice = channelSoundFontRemap[channel]
            com.example.MidiState.soundFontSynth.setChannelVoice(channel, sfVoice)
        } else {
            com.example.MidiState.soundFontSynth.setChannelVoice(channel, null)
        }
        prefs.edit().putString("ch${channel}_mode", mode.name).apply()
    }

    fun clearMapping(channel: Int) {
        channelVoiceMode[channel] = ChannelVoiceMode.YAMAHA_HARDWARE
        channelRemap.remove(channel)
        channelSoundFontRemap.remove(channel)
        com.example.MidiState.soundFontSynth.setChannelVoice(channel, null)

        prefs.edit()
            .putString("ch${channel}_mode", ChannelVoiceMode.YAMAHA_HARDWARE.name)
            .remove("ch${channel}_name")
            .remove("ch${channel}_msb")
            .remove("ch${channel}_lsb")
            .remove("ch${channel}_pc")
            .remove("ch${channel}_sf_id")
            .remove("ch${channel}_sf_name")
            .remove("ch${channel}_sf_path")
            .remove("ch${channel}_sf_cat")
            .remove("ch${channel}_sf_type")
            .remove("ch${channel}_sf_bank")
            .remove("ch${channel}_sf_prog")
            .remove("ch${channel}_sf_size")
            .apply()
    }
    
    fun setVolume(channel: Int, vol: Int) {
        channelVolumes[channel] = vol
        prefs.edit().putInt("ch${channel}_vol", vol).apply()
    }
    
    fun setPan(channel: Int, pan: Int) {
        channelPans[channel] = pan
        prefs.edit().putInt("ch${channel}_pan", pan).apply()
    }

    fun setReverb(channel: Int, rev: Int) {
        channelReverbs[channel] = rev
        prefs.edit().putInt("ch${channel}_rev", rev).apply()
    }

    fun setChorus(channel: Int, cho: Int) {
        channelChoruses[channel] = cho
        prefs.edit().putInt("ch${channel}_cho", cho).apply()
    }
    
    fun isChannelSoundFont(channel: Int): Boolean {
        return channelVoiceMode[channel] == ChannelVoiceMode.SOUNDFONT && channelSoundFontRemap.containsKey(channel)
    }

    fun setDrumNoteRemap(originalNote: Int, targetNote: Int) {
        drumNoteRemap[originalNote] = targetNote
        prefs.edit().putInt("drum_remap_${originalNote}", targetNote).apply()
    }
    
    fun clearDrumNoteRemap(originalNote: Int) {
        drumNoteRemap.remove(originalNote)
        prefs.edit().remove("drum_remap_${originalNote}").apply()
    }
}

