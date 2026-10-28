package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RhythmCreatorTest {

    @Test
    fun testOfficialSubdivisions_1920PPQ() {
        val ppq = MidiRhythmState.PPQ
        assertEquals(1920, ppq)

        // Official Yamaha 1920 PPQ Submultiples
        val quarter = (ppq * 4) / 4
        val eighth = (ppq * 4) / 8
        val sixteenth = (ppq * 4) / 16
        val thirtySecond = (ppq * 4) / 32
        val sixtyFourth = (ppq * 4) / 64
        val oneTwentyEighth = (ppq * 4) / 128
        val twoFiftySixth = (ppq * 4) / 256

        assertEquals(1920, quarter)
        assertEquals(960, eighth)
        assertEquals(480, sixteenth)
        assertEquals(240, thirtySecond)
        assertEquals(120, sixtyFourth)
        assertEquals(60, oneTwentyEighth)
        assertEquals(30, twoFiftySixth)
    }

    @Test
    fun testTimeSignatures_4_4() {
        val ppq = MidiRhythmState.PPQ
        val timeSigNumerator = 4
        val timeSigDenominator = 4
        val measures = 2
        
        val ticksPerBeat = (ppq * 4) / timeSigDenominator
        val totalTicks = timeSigNumerator * measures * ticksPerBeat
        
        assertEquals(1920, ticksPerBeat)
        assertEquals(15360, totalTicks) // 4 * 1920 * 2 = 15360
    }

    @Test
    fun testTimeSignatures_6_8() {
        val ppq = MidiRhythmState.PPQ
        val timeSigNumerator = 6
        val timeSigDenominator = 8
        val measures = 2
        
        val ticksPerBeat = (ppq * 4) / timeSigDenominator
        val totalTicks = timeSigNumerator * measures * ticksPerBeat
        
        assertEquals(960, ticksPerBeat)
        assertEquals(11520, totalTicks) // 6 * 960 * 2 = 11520
    }
    
    @Test
    fun testTimeSignatures_3_4() {
        val ppq = MidiRhythmState.PPQ
        val timeSigNumerator = 3
        val timeSigDenominator = 4
        val measures = 2
        
        val ticksPerBeat = (ppq * 4) / timeSigDenominator
        val totalTicks = timeSigNumerator * measures * ticksPerBeat
        
        assertEquals(1920, ticksPerBeat)
        assertEquals(11520, totalTicks) // 3 * 1920 * 2 = 11520
    }
    
    @Test
    fun testTimeSignatures_12_8() {
        val ppq = MidiRhythmState.PPQ
        val timeSigNumerator = 12
        val timeSigDenominator = 8
        val measures = 1
        
        val ticksPerBeat = (ppq * 4) / timeSigDenominator
        val totalTicks = timeSigNumerator * measures * ticksPerBeat
        
        assertEquals(960, ticksPerBeat)
        assertEquals(11520, totalTicks) // 12 * 960 * 1 = 11520
    }

    @Test
    fun testQuantizationPrecision() {
        val totalTicks = 15360 // 2 bars of 4/4 at 1920 PPQ

        // Note played slightly ahead of beat 2 (tick 1900 instead of 1920)
        val qQuarter = MidiRhythmState.quantizeTick(1900, 4, totalTicks)
        assertEquals(1920, qQuarter)

        // Note played slightly behind beat 2 (tick 1940 instead of 1920)
        val qQuarterBehind = MidiRhythmState.quantizeTick(1940, 4, totalTicks)
        assertEquals(1920, qQuarterBehind)

        // 16th note quantize (grid step = 480)
        // Note played at 460 should snap to 480
        val q16th = MidiRhythmState.quantizeTick(460, 16, totalTicks)
        assertEquals(480, q16th)

        // Note played at 15355 (near wrap around end of loop) snaps to 0
        val qWrap = MidiRhythmState.quantizeTick(15355, 16, totalTicks)
        assertEquals(0, qWrap)
    }

    @Test
    fun testMathematicalIndependence() {
        val timeSignatures = listOf(
            Pair(2, 4), Pair(3, 4), Pair(4, 4), Pair(5, 4), Pair(6, 4), Pair(7, 4), Pair(9, 4), Pair(12, 4),
            Pair(2, 8), Pair(3, 8), Pair(5, 8), Pair(6, 8), Pair(7, 8), Pair(9, 8), Pair(11, 8), Pair(12, 8),
            Pair(2, 2), Pair(3, 2), Pair(4, 2)
        )
        
        val measuresList = listOf(1, 2, 3, 4, 5, 6, 7, 8, 16, 32, 64)
        val ppq = MidiRhythmState.PPQ
        
        for ((num, den) in timeSignatures) {
            for (measures in measuresList) {
                val ticksPerBeat = (ppq * 4) / den
                val totalTicks = num * measures * ticksPerBeat
                
                assertTrue("Ticks per beat should be positive for $num/$den", ticksPerBeat > 0)
                assertTrue("Total ticks should be positive for $num/$den with $measures measures", totalTicks > 0)
            }
        }
    }

    @Test
    fun testNoteOnNoteOffPairingAndRecording() {
        MidiState.isArrangerMode = false
        MidiRhythmState.isRecording = true
        MidiRhythmState.selectedTrackIndex = 0
        val track = MidiRhythmState.tracks[0]
        track.notes = emptyList()

        val fakeTimestamp1 = 1_000_000_000L
        val fakeTimestamp2 = 1_500_000_000L

        MidiRhythmState.currentTick = 100
        // Note On channel 0, note 60 (Middle C), vel 100
        MidiRhythmState.processIncomingMidi(0x90, 60, 100, fakeTimestamp1)

        assertEquals(1, track.notes.size)
        val recordedNote = track.notes[0]
        assertEquals(60, recordedNote.note)
        assertEquals(100, recordedNote.velocity)
        assertEquals(100, recordedNote.tick)
        assertEquals(fakeTimestamp1, recordedNote.timestampNs)

        // Advance tick by 480 (a sixteenth note in 1920 PPQ)
        MidiRhythmState.currentTick = 580
        // Note Off channel 0, note 60, vel 0
        MidiRhythmState.processIncomingMidi(0x80, 60, 0, fakeTimestamp2)

        // Track notes size remains 1 (no new list, no map)
        assertEquals(1, track.notes.size)
        // Duration correctly updated to 480 ticks
        assertEquals(480, track.notes[0].durationTicks)

        // Discard unmatched Note Off safely without crashing or leaking
        MidiRhythmState.processIncomingMidi(0x80, 72, 0, fakeTimestamp2)
        assertEquals(1, track.notes.size)

        MidiRhythmState.isRecording = false
        MidiRhythmState.clearActiveNotes()
    }
}
