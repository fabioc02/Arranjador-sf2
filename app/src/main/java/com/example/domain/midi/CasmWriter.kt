package com.example.domain.midi

import com.example.domain.models.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object CasmWriter {

    fun writeStyle(
        sections: Map<com.example.StyleSection, List<com.example.RhythmTrack>>,
        ppq: Int,
        tempoBpm: Double = 120.0
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // MThd
        out.write("MThd".toByteArray())
        out.write(byteArrayOf(0, 0, 0, 6)) // length
        out.write(byteArrayOf(0, 0)) // type 0
        out.write(byteArrayOf(0, 1)) // 1 track
        out.write(byteArrayOf((ppq shr 8).toByte(), (ppq and 0xFF).toByte())) // division

        val trackData = ByteArrayOutputStream()

        // Tempo meta event at tick 0
        writeVarInt(trackData, 0)
        trackData.write(0xFF)
        trackData.write(0x51)
        trackData.write(0x03)
        val microsecondsPerBeat = (60_000_000 / tempoBpm).toInt()
        trackData.write((microsecondsPerBeat shr 16) and 0xFF)
        trackData.write((microsecondsPerBeat shr 8) and 0xFF)
        trackData.write(microsecondsPerBeat and 0xFF)

        // Time Signature 4/4
        writeVarInt(trackData, 0)
        trackData.write(0xFF)
        trackData.write(0x58)
        trackData.write(0x04)
        trackData.write(0x04) // numerator
        trackData.write(0x02) // denominator
        trackData.write(0x18) 
        trackData.write(0x08) 

        // Initial setup for all unique tracks across all sections
        val allTracks = sections.values.flatten().distinctBy { it.channel }
        
        allTracks.forEach { track ->
            val outCh = track.channel - 1
            
            // XG Part Mode (Drum or Normal)
            val setupNum = if (track.isDrumTrack) {
                if (track.channel == 9) 0x02.toByte() else 0x01.toByte()
            } else {
                0x00.toByte()
            }
            writeSysEx(trackData, 0, byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, 0x08, outCh.toByte(), 0x07, setupNum, 0xF7.toByte()))
            
            // Bank Select MSB, LSB, PC
            writeCC(trackData, 0, outCh, 0, track.bankMsb)
            writeCC(trackData, 0, outCh, 32, track.bankLsb)
            writePC(trackData, 0, outCh, track.program)

            // Volume, Pan, Reverb, Chorus
            writeCC(trackData, 0, outCh, 7, track.volume)
            writeCC(trackData, 0, outCh, 10, track.pan)
            writeCC(trackData, 0, outCh, 91, track.reverb)
            writeCC(trackData, 0, outCh, 93, track.chorus)

            // Drum Params (NRPN & SysEx)
            if (track.isDrumTrack) {
                val setupNumber = if (track.channel == 9) 1 else 0
                val setupByte = (0x30 + setupNumber).toByte()
                
                track.drumParams.forEach { (key, params) ->
                    val sendNRPN = { msb: Int, value: Int ->
                        writeCC(trackData, 0, outCh, 99, msb)
                        writeCC(trackData, 0, outCh, 98, key)
                        writeCC(trackData, 0, outCh, 6, value)
                    }
                    sendNRPN(24, params.pitchCoarse)
                    sendNRPN(25, params.pitchFine)
                    sendNRPN(26, params.level)
                    sendNRPN(28, params.pan)
                    sendNRPN(29, params.reverb)
                    sendNRPN(30, params.chorus)
                    
                    val sendSysExParam = { paramId: Int, value: Int ->
                        writeSysEx(trackData, 0, byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x4C, setupByte, key.toByte(), paramId.toByte(), value.toByte(), 0xF7.toByte()))
                    }
                    sendSysExParam(0, params.pitchCoarse)
                    sendSysExParam(1, params.pitchFine)
                    sendSysExParam(2, params.level)
                    sendSysExParam(4, params.pan)
                    sendSysExParam(5, params.reverb)
                    sendSysExParam(6, params.chorus)
                }
            }
        }

        // Yamaha Styles use Markers to denote sections
        class Ev(val tick: Long, val type: Int, val ch: Int, val d1: Int, val d2: Int, val metaType: Int = -1, val metaData: ByteArray? = null)
        val allEvents = mutableListOf<Ev>()
        var currentTick = 0L
        val sortedSections = sections.entries.sortedBy { it.key.ordinal }
        
        sortedSections.forEach { (section, tracks) ->
            // Proper Yamaha section names: Main A, Main B, Fill In AA, Intro A, Ending A
            val yamahaName = when (section) {
                com.example.StyleSection.MAIN_A -> "Main A"
                com.example.StyleSection.MAIN_B -> "Main B"
                com.example.StyleSection.MAIN_C -> "Main C"
                com.example.StyleSection.MAIN_D -> "Main D"
                com.example.StyleSection.FILL_A -> "Fill In AA"
                com.example.StyleSection.FILL_B -> "Fill In BB"
                com.example.StyleSection.FILL_C -> "Fill In CC"
                com.example.StyleSection.FILL_D -> "Fill In DD"
                com.example.StyleSection.INTRO_A -> "Intro A"
                com.example.StyleSection.INTRO_B -> "Intro B"
                com.example.StyleSection.INTRO_C -> "Intro C"
                com.example.StyleSection.ENDING_A -> "Ending A"
                com.example.StyleSection.ENDING_B -> "Ending B"
                com.example.StyleSection.ENDING_C -> "Ending C"
            }
            val markerBytes = yamahaName.toByteArray()
            allEvents.add(Ev(currentTick, -1, -1, -1, -1, 0x06, markerBytes))
            
            // Calculate dynamic section length based on notes
            val maxNoteTick = tracks.flatMap { it.notes }.maxOfOrNull { it.tick + it.durationTicks } ?: 0L
            val measureTicks = 4 * ppq
            val measures = Math.ceil(maxNoteTick.toDouble() / measureTicks).toInt().coerceAtLeast(1)
            val sectionTicks = measures * measureTicks
            
            tracks.forEach { track ->
                val outCh = track.channel - 1
                track.notes.forEach { note ->
                    val absOn = currentTick + note.tick
                    val absOff = absOn + note.durationTicks
                    allEvents.add(Ev(absOn, 0x90, outCh, note.note, note.velocity))
                    allEvents.add(Ev(absOff, 0x80, outCh, note.note, 0))
                }
            }
            
            currentTick += sectionTicks
        }

        allEvents.sortWith(compareBy({ it.tick }, { if (it.metaType != -1) 0 else 1 }))

        var lastEvTick = 0L
        allEvents.forEach { ev ->
            val delta = (ev.tick - lastEvTick).toInt()
            writeVarInt(trackData, delta)
            if (ev.metaType != -1) {
                trackData.write(0xFF)
                trackData.write(ev.metaType)
                writeVarInt(trackData, ev.metaData!!.size)
                trackData.write(ev.metaData)
            } else {
                trackData.write(ev.type or ev.ch)
                trackData.write(ev.d1)
                trackData.write(ev.d2)
            }
            lastEvTick = ev.tick
        }

        // End of track meta event
        writeVarInt(trackData, 0)
        trackData.write(0xFF)
        trackData.write(0x2F)
        trackData.write(0x00)

        val trackBytes = trackData.toByteArray()
        out.write("MTrk".toByteArray())
        out.write(ByteBuffer.allocate(4).putInt(trackBytes.size).array())
        out.write(trackBytes)
        
        // NOW WRITE THE CASM CHUNK!
        val casmBytes = generateCasmChunk(sections)
        out.write(casmBytes)

        return out.toByteArray()
    }
    
    private fun generateCasmChunk(sections: Map<com.example.StyleSection, List<com.example.RhythmTrack>>): ByteArray {
        val casmOut = ByteArrayOutputStream()
        casmOut.write("CASM".toByteArray())
        
        val csegOut = ByteArrayOutputStream()
        
        // Loop over each section to generate a CSEG
        // For simplicity, we can group all Main sections into one CSEG, but let's just create one CSEG that covers everything
        csegOut.write("CSEG".toByteArray())
        val csegDataOut = ByteArrayOutputStream()
        
        // Sdec (Section declarations)
        csegDataOut.write("Sdec".toByteArray())
        val sdecNames = sections.keys.map { 
            when (it) {
                com.example.StyleSection.MAIN_A -> "Main A"
                com.example.StyleSection.MAIN_B -> "Main B"
                com.example.StyleSection.MAIN_C -> "Main C"
                com.example.StyleSection.MAIN_D -> "Main D"
                com.example.StyleSection.FILL_A -> "Fill In AA"
                com.example.StyleSection.FILL_B -> "Fill In BB"
                com.example.StyleSection.FILL_C -> "Fill In CC"
                com.example.StyleSection.FILL_D -> "Fill In DD"
                com.example.StyleSection.INTRO_A -> "Intro A"
                com.example.StyleSection.INTRO_B -> "Intro B"
                com.example.StyleSection.INTRO_C -> "Intro C"
                com.example.StyleSection.ENDING_A -> "Ending A"
                com.example.StyleSection.ENDING_B -> "Ending B"
                com.example.StyleSection.ENDING_C -> "Ending C"
            }
        }.joinToString(",")
        val sdecBytes = sdecNames.toByteArray()
        csegDataOut.write(ByteBuffer.allocate(4).putInt(sdecBytes.size).array())
        csegDataOut.write(sdecBytes)
        
        // Ctab (Channel table - Yamaha SFF standard 27 bytes)
        // Get the distinct tracks from the first available section
        val representativeTracks = sections.values.firstOrNull() ?: emptyList()
        representativeTracks.forEach { track ->
            csegDataOut.write("Ctab".toByteArray())
            val ctabData = ByteArrayOutputStream()
            
            val outCh = (track.channel - 1).coerceIn(0, 15)
            ctabData.write(outCh) // Offset 0: Source Channel
            
            // Offset 1..8: Part Name (8 bytes, ASCII space-padded)
            val partNameFormatted = when (outCh) {
                8 -> "Rhythm1 "
                9 -> "Rhythm2 "
                10 -> "Bass    "
                11 -> "Chord1  "
                12 -> "Chord2  "
                13 -> "Pad     "
                14 -> "Phrase1 "
                15 -> "Phrase2 "
                else -> track.name.padEnd(8, ' ').take(8)
            }
            val partNameBytes = partNameFormatted.padEnd(8, ' ').toByteArray().take(8).toByteArray()
            ctabData.write(partNameBytes)
            
            ctabData.write(outCh) // Offset 9: Dest Channel
            ctabData.write(0)     // Offset 10: Editable (0 = editable)
            
            // Offset 11..12: Note Mute (2 bytes)
            ctabData.write(0)
            ctabData.write(0)
            
            // Offset 13..17: Chord Mute (5 bytes)
            repeat(5) { ctabData.write(0) }
            
            // Offset 18: Source Chord Root (0 = C)
            val rootKey = track.sourceRoot?.rootKey ?: 0
            ctabData.write(rootKey.coerceIn(0, 11))
            
            // Offset 19: Source Chord Type (2 = Maj7 default for Yamaha style parts, 0 = Maj)
            val chordType = if (track.isDrumTrack) 0 else 2
            ctabData.write(chordType)
            
            // Determine Yamaha standard NTR and NTT
            // NTR: 0 = Root Trans, 1 = Root Fixed, 2 = Guitar
            // NTT: 0 = Bypass, 1 = Melody, 2 = Chord, 3 = Bass, 4 = Melodic Minor, 5 = Harmonic Minor
            var ntr = 0x01 // Root Fixed
            var ntt = 0x02 // Chord
            var highKey = 6 // F#
            var noteLowLimit = 36 // C2
            var noteHighLimit = 96 // C7
            var rtr = 0 // Stop
            
            if (track.isDrumTrack || outCh == 8 || outCh == 9) {
                ntr = 0x00 // Root Trans
                ntt = 0x00 // Bypass
                highKey = 6
                noteLowLimit = 0
                noteHighLimit = 127
                rtr = 0 // Stop
            } else if (outCh == 10 || track.name.contains("Bass", ignoreCase = true)) {
                ntr = 0x00 // Root Trans (Bass follows chord root changes)
                ntt = 0x03 // Bass Table (Yamaha Bass NTT = 3)
                highKey = 6 // F# (Yamaha standard High Key pivot for Bass)
                noteLowLimit = 28 // E1 (Yamaha standard Bass lower limit)
                noteHighLimit = 67 // G3 (Yamaha standard Bass upper limit)
                rtr = 3 // Retrigger
            } else if (track.name.contains("Guitar", ignoreCase = true)) {
                ntr = 0x02 // Guitar
                ntt = 0x02 // Chord
                highKey = 6
                noteLowLimit = 36
                noteHighLimit = 96
                rtr = 0
            } else if (outCh in 14..15 || track.name.contains("Phrase", ignoreCase = true) || track.name.contains("Melody", ignoreCase = true)) {
                ntr = 0x00 // Root Trans
                ntt = 0x01 // Melody
                highKey = 6
                noteLowLimit = 36
                noteHighLimit = 108
                rtr = 3 // Retrigger
            } else {
                // Chord 1, Chord 2, Pad
                ntr = 0x01 // Root Fixed
                ntt = 0x02 // Chord
                highKey = 6
                noteLowLimit = 36
                noteHighLimit = 96
                rtr = 0 // Stop
            }
            
            ctabData.write(ntr)           // Offset 20: NTR
            ctabData.write(ntt)           // Offset 21: NTT
            ctabData.write(highKey)       // Offset 22: High Key
            ctabData.write(noteLowLimit)  // Offset 23: Note Limit Low
            ctabData.write(noteHighLimit) // Offset 24: Note Limit High
            ctabData.write(rtr)           // Offset 25: RTR (Retrigger Rule)
            ctabData.write(0)             // Offset 26: Reserved / End marker (0x00)
            
            val ctabBytes = ctabData.toByteArray()
            csegDataOut.write(ByteBuffer.allocate(4).putInt(ctabBytes.size).array())
            csegDataOut.write(ctabBytes)
        }
        
        val csegDataBytes = csegDataOut.toByteArray()
        csegOut.write(ByteBuffer.allocate(4).putInt(csegDataBytes.size).array())
        csegOut.write(csegDataBytes)
        
        val casmDataBytes = csegOut.toByteArray()
        casmOut.write(ByteBuffer.allocate(4).putInt(casmDataBytes.size).array())
        casmOut.write(casmDataBytes)
        
        return casmOut.toByteArray()
    }

    private fun writeVarInt(out: ByteArrayOutputStream, value: Int) {
        var buffer = value and 0x7F
        var v = value shr 7
        while (v > 0) {
            buffer = (buffer shl 8) or 0x80 or (v and 0x7F)
            v = v shr 7
        }
        while (true) {
            out.write(buffer and 0xFF)
            if ((buffer and 0x80) != 0) {
                buffer = buffer ushr 8
            } else {
                break
            }
        }
    }
    
    private fun writeCC(out: ByteArrayOutputStream, delta: Int, ch: Int, cc: Int, value: Int) {
        writeVarInt(out, delta)
        out.write(0xB0 or ch)
        out.write(cc)
        out.write(value)
    }
    
    private fun writePC(out: ByteArrayOutputStream, delta: Int, ch: Int, pc: Int) {
        writeVarInt(out, delta)
        out.write(0xC0 or ch)
        out.write(pc)
    }
    
    private fun writeSysEx(out: ByteArrayOutputStream, delta: Int, data: ByteArray) {
        writeVarInt(out, delta)
        out.write(0xF0)
        writeVarInt(out, data.size - 1) 
        out.write(data, 1, data.size - 1)
    }
}
