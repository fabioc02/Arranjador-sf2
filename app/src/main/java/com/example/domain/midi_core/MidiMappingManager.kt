package com.example.domain.midi_core

import android.content.Context
import android.content.SharedPreferences

enum class MidiAction(val displayName: String) {
    START("Start"),
    STOP("Stop"),
    MAIN_A("Main A"),
    MAIN_B("Main B"),
    MAIN_C("Main C"),
    MAIN_D("Main D"),
    FILL_A("Fill In A"),
    FILL_B("Fill In B"),
    FILL_C("Fill In C"),
    FILL_D("Fill In D"),
    BREAK("Break"),
    INTRO_A("Intro A"),
    ENDING_A("Ending A")
}

class MidiMappingManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("midi_mappings", Context.MODE_PRIVATE)
    private val mappings = mutableMapOf<String, MidiAction>()
    private val actionToTrigger = mutableMapOf<MidiAction, String>()

    var learningAction: MidiAction? = null
    var onMappingChanged: (() -> Unit)? = null

    init {
        loadMappings()
    }

    private fun loadMappings() {
        mappings.clear()
        actionToTrigger.clear()
        prefs.all.forEach { (key, value) ->
            if (value is String) {
                try {
                    val action = MidiAction.valueOf(value)
                    mappings[key] = action
                    actionToTrigger[action] = key
                } catch (e: Exception) {
                    // Ignore invalid actions
                }
            }
        }
    }

    fun startLearning(action: MidiAction) {
        learningAction = action
    }

    fun cancelLearning() {
        learningAction = null
    }

    fun getTriggerForAction(action: MidiAction): String? {
        return actionToTrigger[action]
    }

    fun clearMapping(action: MidiAction) {
        val trigger = actionToTrigger.remove(action)
        if (trigger != null) {
            mappings.remove(trigger)
            prefs.edit().remove(trigger).apply()
            onMappingChanged?.invoke()
        }
    }

    fun onRawMidiMessage(msg: ByteArray): MidiAction? {
        if (msg.isEmpty()) return null

        val status = msg[0].toInt() and 0xFF
        
        // Ignore Clock and Active Sensing
        if (status == 248 || status == 254) return null

        // Ignore Note Off
        val command = status and 0xF0
        val isNoteOff = command == 0x80 || (command == 0x90 && msg.size > 2 && msg[2].toInt() == 0)
        if (isNoteOff) return null

        // Generate a signature for the trigger
        val triggerSignature = buildSignature(msg) ?: return null

        val currentLearning = learningAction
        if (currentLearning != null) {
            clearMapping(currentLearning)
            mappings[triggerSignature] = currentLearning
            actionToTrigger[currentLearning] = triggerSignature
            prefs.edit().putString(triggerSignature, currentLearning.name).apply()
            
            learningAction = null
            onMappingChanged?.invoke()
            return null // Intercepted for learning
        }

        return mappings[triggerSignature]
    }

    private fun buildSignature(msg: ByteArray): String? {
        val status = msg[0].toInt() and 0xFF
        val command = status and 0xF0

        return when {
            command == 0x90 -> {
                // Note On: ignore velocity (msg[2])
                if (msg.size >= 2) "${status.toHexString()}_${msg[1].toInt().toHexString()}" else null
            }
            status >= 0xF8 -> {
                // Realtime (e.g. FA Start, FC Stop)
                status.toHexString()
            }
            status == 0xF0 -> {
                // SysEx: use the full hex string
                msg.joinToString("") { it.toInt().and(0xFF).toHexString() }
            }
            command == 0xB0 -> {
                // CC: use channel, cc num, and cc value
                if (msg.size >= 3) "${status.toHexString()}_${msg[1].toInt().toHexString()}_${msg[2].toInt().toHexString()}" else null
            }
            else -> null // Ignore pitch bend, aftertouch, etc for triggers
        }
    }

    private fun Int.toHexString(): String = toString(16).padStart(2, '0').uppercase()
}
