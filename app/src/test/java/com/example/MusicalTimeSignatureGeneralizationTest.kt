package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicalTimeSignatureGeneralizationTest {

    private fun calculateTicks(ppq: Int, num: Int, den: Int, measures: Int): Pair<Int, Int> {
        val ticksPerBeat = (ppq * 4) / den
        val totalTicks = num * measures * ticksPerBeat
        return Pair(ticksPerBeat, totalTicks)
    }

    @Test
    fun testStandardSignatures() {
        val ppq = 24
        
        // 2/4
        val (tpb24, total24) = calculateTicks(ppq, 2, 4, 1)
        assertEquals(24, tpb24)
        assertEquals(48, total24)
        
        // 3/4
        val (tpb34, total34) = calculateTicks(ppq, 3, 4, 1)
        assertEquals(24, tpb34)
        assertEquals(72, total34)

        // 4/4
        val (tpb44, total44) = calculateTicks(ppq, 4, 4, 1)
        assertEquals(24, tpb44)
        assertEquals(96, total44)
        
        // 6/8
        val (tpb68, total68) = calculateTicks(ppq, 6, 8, 1)
        assertEquals(12, tpb68)
        assertEquals(72, total68)
    }

    @Test
    fun testComplexAndAsymmetricalSignatures() {
        val ppq = 24
        
        // 5/4
        val (tpb54, total54) = calculateTicks(ppq, 5, 4, 1)
        assertEquals(24, tpb54)
        assertEquals(120, total54)
        
        // 7/8
        val (tpb78, total78) = calculateTicks(ppq, 7, 8, 1)
        assertEquals(12, tpb78)
        assertEquals(84, total78)
        
        // 9/8
        val (tpb98, total98) = calculateTicks(ppq, 9, 8, 1)
        assertEquals(12, tpb98)
        assertEquals(108, total98)
        
        // 11/8
        val (tpb118, total118) = calculateTicks(ppq, 11, 8, 1)
        assertEquals(12, tpb118)
        assertEquals(132, total118)
        
        // 12/8
        val (tpb128, total128) = calculateTicks(ppq, 12, 8, 1)
        assertEquals(12, tpb128)
        assertEquals(144, total128)
    }
    
    @Test
    fun testCutTimeAndHalfNotes() {
        val ppq = 24
        
        // 3/2
        val (tpb32, total32) = calculateTicks(ppq, 3, 2, 1)
        assertEquals(48, tpb32) // A half note beat is 48 ticks
        assertEquals(144, total32)
        
        // 5/2
        val (tpb52, total52) = calculateTicks(ppq, 5, 2, 1)
        assertEquals(48, tpb52)
        assertEquals(240, total52)
    }
    
    @Test
    fun testMeasureScaling() {
        val ppq = 24
        
        val (_, total1) = calculateTicks(ppq, 4, 4, 1)
        assertEquals(96, total1)
        
        val (_, total16) = calculateTicks(ppq, 4, 4, 16)
        assertEquals(1536, total16)
        
        val (_, total64) = calculateTicks(ppq, 7, 8, 64)
        assertEquals(84 * 64, total64)
    }
    
    @Test
    fun testTimelineProportionality() {
        // Timeline needle progress is: currentTick / totalTicks
        // It must always reach exactly 1.0 at the end, regardless of time signature
        
        val signatures = listOf(
            Pair(4, 4), Pair(6, 8), Pair(5, 8), Pair(7, 8), Pair(12, 8)
        )
        
        for ((num, den) in signatures) {
            val (_, total) = calculateTicks(24, num, den, 2)
            
            val startProgress = 0f / total.toFloat()
            val midProgress = (total / 2).toFloat() / total.toFloat()
            val endProgress = total.toFloat() / total.toFloat()
            
            assertEquals(0.0f, startProgress)
            assertEquals(0.5f, midProgress)
            assertEquals(1.0f, endProgress)
        }
    }
}
