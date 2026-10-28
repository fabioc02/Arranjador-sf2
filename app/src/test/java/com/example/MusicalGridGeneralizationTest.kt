package com.example

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicalGridGeneralizationTest {

    // quantizeResolution determines divisions of a quarter note.
    // 4 = 1/16th notes (4 per quarter note)
    // 8 = 1/32nd notes (8 per quarter note)
    // 16 = 1/64th notes (16 per quarter note)
    // 3 = 8th note triplets (3 per quarter note)
    // 6 = 16th note triplets (6 per quarter note)
    // 12 = 32nd note triplets (12 per quarter note)
    private fun getQuantizeTicks(ppq: Int, quantizeResolution: Int): Int {
        return ppq / quantizeResolution
    }

    @Test
    fun testStandardGrids() {
        val ppq = 24
        
        // 1/16 grid (4 subdivisions per beat)
        assertEquals(6, getQuantizeTicks(ppq, 4))
        
        // 1/32 grid (8 subdivisions per beat)
        assertEquals(3, getQuantizeTicks(ppq, 8))
    }

    @Test
    fun testAdvancedGrids() {
        // Assume high resolution for advanced tuplets
        val ppq = 96
        
        // 1/4 grid (1 subdivision per beat)
        assertEquals(96, getQuantizeTicks(ppq, 1))
        
        // 1/8 grid (2 subdivisions per beat)
        assertEquals(48, getQuantizeTicks(ppq, 2))
        
        // 1/64 grid (16 subdivisions per beat)
        assertEquals(6, getQuantizeTicks(ppq, 16))
        
        // 1/128 grid (32 subdivisions per beat)
        assertEquals(3, getQuantizeTicks(ppq, 32))
    }
    
    @Test
    fun testTuplets() {
        val ppq = 96
        
        // 8th note triplets (3 per beat)
        assertEquals(32, getQuantizeTicks(ppq, 3))
        
        // 16th note triplets (6 per beat)
        assertEquals(16, getQuantizeTicks(ppq, 6))
        
        // 32nd note triplets (12 per beat)
        assertEquals(8, getQuantizeTicks(ppq, 12))
        
        // Quintuplets (5 per beat) - this requires floating point or higher resolution,
        // but checking the integer division math is safe here if engine needs it.
        // For PPQ 120, it would be 24.
        assertEquals(24, getQuantizeTicks(120, 5))
    }
}
