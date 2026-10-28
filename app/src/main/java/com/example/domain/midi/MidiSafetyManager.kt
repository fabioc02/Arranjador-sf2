package com.example.domain.midi

class MidiSafetyManager(
    private val sendNoteOff: (channel: Int, note: Int, velocity: Int, timestampNs: Long) -> Unit,
    private val sendControlChange: (channel: Int, control: Int, value: Int, timestampNs: Long) -> Unit
) {
    // 16 channels * 128 notes = 2048 booleans. Flat array, 0 allocations, 0 GC churn!
    private val activeNotes = BooleanArray(16 * 128)
    private val activeChannels = BooleanArray(16)
    private val lock = Any()

    fun onNoteOn(channel: Int, note: Int, velocity: Int) {
        if (channel !in 0..15 || note !in 0..127) return
        val key = (channel shl 7) or note
        synchronized(lock) {
            if (velocity > 0) {
                activeNotes[key] = true
                activeChannels[channel] = true
            } else {
                activeNotes[key] = false
            }
        }
    }

    fun onNoteOff(channel: Int, note: Int) {
        if (channel !in 0..15 || note !in 0..127) return
        val key = (channel shl 7) or note
        synchronized(lock) {
            activeNotes[key] = false
        }
    }

    fun isNoteActive(channel: Int, note: Int): Boolean {
        if (channel !in 0..15 || note !in 0..127) return false
        val key = (channel shl 7) or note
        return synchronized(lock) { activeNotes[key] }
    }

    fun getActiveNotesCount(): Int {
        var count = 0
        synchronized(lock) {
            for (i in activeNotes.indices) {
                if (activeNotes[i]) count++
            }
        }
        return count
    }

    fun getActiveChannels(): List<Int> {
        val list = mutableListOf<Int>()
        synchronized(lock) {
            for (ch in 0..15) {
                if (activeChannels[ch]) list.add(ch)
            }
        }
        return list
    }

    fun registerChannelUsed(channel: Int) {
        if (channel in 0..15) {
            synchronized(lock) {
                activeChannels[channel] = true
            }
        }
    }

    /**
     * Panic / All Notes Off:
     * 1. Sends NoteOff for all currently active notes.
     * 2. Sends CC 123 (All Notes Off) for channels used by arranger.
     * 3. Clears active notes state.
     * 4. Safe when called multiple times (idempotent).
     */
    fun panicMidi(timestampNs: Long = System.nanoTime()) {
        synchronized(lock) {
            // 1. Send NoteOff for all active notes
            for (ch in 0..15) {
                val base = ch shl 7
                for (n in 0..127) {
                    if (activeNotes[base + n]) {
                        activeNotes[base + n] = false
                        sendNoteOff(ch, n, 0, timestampNs)
                    }
                }
            }

            // 2. Send CC 123 (All Notes Off) for active/used channels
            for (ch in 0..15) {
                if (activeChannels[ch]) {
                    sendControlChange(ch, 123, 0, timestampNs)
                }
            }
        }
    }

    fun reset() {
        synchronized(lock) {
            activeNotes.fill(false)
            activeChannels.fill(false)
        }
    }
}
