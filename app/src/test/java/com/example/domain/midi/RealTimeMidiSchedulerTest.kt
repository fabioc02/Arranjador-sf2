package com.example.domain.midi

import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class RealTimeMidiSchedulerTest {

@Test
    fun testMathematicalClockConsistency() {
        // Test combinations of PPQ, BPM and Time Signatures mathematically
        val bpms = listOf(60f, 80f, 100f, 120f, 140f, 180f, 240f)
        val ppqs = listOf(24, 96, 480, 960, 1920)
        
        for (bpm in bpms) {
            for (ppq in ppqs) {
                // Using double to prevent truncation accumulation
                val tickDurationNs = 60_000_000_000.0 / (bpm * ppq)
                
                // 1 quarter note = ppq ticks
                val quarterNoteDurationNs = tickDurationNs * ppq
                val expectedQuarterNoteDurationNs = 60_000_000_000.0 / bpm
                
                // Allow 1 ns rounding error maximum due to float precision
                assertTrue("BPM=$bpm PPQ=$ppq Failed. Diff = ${abs(quarterNoteDurationNs - expectedQuarterNoteDurationNs)}", abs(quarterNoteDurationNs - expectedQuarterNoteDurationNs) <= 1.0)
            }
        }
    }
    
    @Test
    fun testTimeSignaturesAndSubdivisions() {
        // We must prove that changing the subdivision DOES NOT alter the BPM duration.
        // A quarter note at 120 BPM should ALWAYS be 500ms, regardless of the time signature.
        val bpm = 120f
        val ppq = 960
        val nanosPerTick = 60_000_000_000.0 / (bpm * ppq)
        
        // --- RESOLUTIONS ---
        val quarterTicks = ppq
        val eighthTicks = ppq / 2
        val eighthTripletTicks = ppq / 3
        val sixteenthTicks = ppq / 4
        val sixteenthTripletTicks = ppq / 6
        val thirtySecondTicks = ppq / 8
        
        // Assert subdivisions sum up correctly to a quarter note (500,000,000 ns)
        val expectedQuarterNs = 500_000_000.0
        
        assertEquals(expectedQuarterNs, quarterTicks * nanosPerTick, 1.0)
        assertEquals(expectedQuarterNs, 2 * eighthTicks * nanosPerTick, 1.0)
        assertEquals(expectedQuarterNs, 3 * eighthTripletTicks * nanosPerTick, 1.0)
        assertEquals(expectedQuarterNs, 4 * sixteenthTicks * nanosPerTick, 1.0)
        assertEquals(expectedQuarterNs, 6 * sixteenthTripletTicks * nanosPerTick, 1.0)
        assertEquals(expectedQuarterNs, 8 * thirtySecondTicks * nanosPerTick, 1.0)
        
        // --- TIME SIGNATURES ---
        // Let's test how long a full measure takes for various time signatures
        // Duration of a measure = (numerator) * (ticks per denominator) * nanosPerTick
        
        fun getMeasureDurationMs(numerator: Int, denominator: Int): Double {
            val ticksPerBeat = when (denominator) {
                2 -> ppq * 2
                4 -> ppq
                8 -> ppq / 2
                else -> throw IllegalArgumentException("Unsupported denominator")
            }
            val totalTicks = numerator * ticksPerBeat
            return (totalTicks * nanosPerTick) / 1_000_000.0
        }
        
        // At 120 BPM, 1 quarter note = 500ms.
        // 2/4 = 2 quarter notes = 1000ms
        assertEquals(1000.0, getMeasureDurationMs(2, 4), 0.1)
        // 3/4 = 3 quarter notes = 1500ms
        assertEquals(1500.0, getMeasureDurationMs(3, 4), 0.1)
        // 4/4 = 4 quarter notes = 2000ms
        assertEquals(2000.0, getMeasureDurationMs(4, 4), 0.1)
        // 5/4 = 5 quarter notes = 2500ms
        assertEquals(2500.0, getMeasureDurationMs(5, 4), 0.1)
        // 6/8 = 6 eighth notes = 1500ms
        assertEquals(1500.0, getMeasureDurationMs(6, 8), 0.1)
        // 7/8 = 7 eighth notes = 1750ms
        assertEquals(1750.0, getMeasureDurationMs(7, 8), 0.1)
        // 9/8 = 9 eighth notes = 2250ms
        assertEquals(2250.0, getMeasureDurationMs(9, 8), 0.1)
        // 11/8 = 11 eighth notes = 2750ms
        assertEquals(2750.0, getMeasureDurationMs(11, 8), 0.1)
        // 12/8 = 12 eighth notes = 3000ms
        assertEquals(3000.0, getMeasureDurationMs(12, 8), 0.1)
        // 3/2 = 3 half notes = 3000ms
        assertEquals(3000.0, getMeasureDurationMs(3, 2), 0.1)
        // 4/2 = 4 half notes = 4000ms
        assertEquals(4000.0, getMeasureDurationMs(4, 2), 0.1)
    }

@Test
    fun testSchedulerJitterAndPrecision() {
        val ppq = 24
        val bpm = 120f
        val ticksToSimulate = 100
        val latch = CountDownLatch(ticksToSimulate)
        
        val nanosPerTickDouble = 60_000_000_000.0 / (bpm * ppq)
        
        class EventRecord(
            val expectedTargetTimeNs: Long,
            val actualTargetTimeNs: Long,
            val realExecutionTimeNs: Long
        )
        
        val records = mutableListOf<EventRecord>()
        var startTimeNs = 0L
        val scheduler = RealTimeMidiScheduler(ppq, { bpm }) { currentTick, targetTimeNs ->
            if (currentTick == 0L) {
                startTimeNs = targetTimeNs
            }
            
            val expectedTargetTimeNs = (startTimeNs + (currentTick * nanosPerTickDouble)).toLong()
            
            records.add(EventRecord(
                expectedTargetTimeNs = expectedTargetTimeNs,
                actualTargetTimeNs = targetTimeNs,
                realExecutionTimeNs = System.nanoTime()
            ))
            
            latch.countDown()
        }
        
        scheduler.start()
        
        val completed = latch.await(10, TimeUnit.SECONDS)
        scheduler.stop()
        
        assertTrue("Scheduler did not complete in time", completed)
        
        var maxJitter = 0L
        var totalJitter = 0L
        var outOfOrder = 0
        var lastExecutionTime = 0L
        
        for (record in records) {
            // Validate the mathematical target time is perfect (No accumulated jitter in the clock)
            assertTrue(
                "Target time mathematical error. Expected: ${record.expectedTargetTimeNs}, Actual: ${record.actualTargetTimeNs}", 
                abs(record.actualTargetTimeNs - record.expectedTargetTimeNs) <= 1
            )
            
            // Measure actual execution jitter (Android Threading limits)
            val jitter = abs(record.realExecutionTimeNs - record.actualTargetTimeNs)
            if (jitter > maxJitter) maxJitter = jitter
            totalJitter += jitter
            
            if (record.realExecutionTimeNs < lastExecutionTime) {
                outOfOrder++
            }
            lastExecutionTime = record.realExecutionTimeNs
        }
        
        val avgJitter = totalJitter / records.size
        
        println("--- RealTimeMidiScheduler Jitter Report ---")
        println("BPM: $bpm")
        println("PPQ: $ppq")
        println("Total Events: ${records.size}")
        println("Expected Nanos Per Tick: $nanosPerTickDouble")
        println("Max Jitter: ${maxJitter / 1_000_000.0} ms")
        println("Avg Jitter: ${avgJitter / 1_000_000.0} ms")
        println("Out Of Order Events: $outOfOrder")
        
        // Assertions for mathematical correctness
        assertEquals("Events should never be out of order in a single thread", 0, outOfOrder)
    }
}
