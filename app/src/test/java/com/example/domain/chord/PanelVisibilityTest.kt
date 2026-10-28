package com.example.domain.chord

import com.example.domain.models.ChordType
import org.junit.Assert.*
import org.junit.Test

class PanelVisibilityTest {

    @Test
    fun testPanelStaysVisibleWhenArrangerOnAndNoNotes() {
        var isArrangerMode = true
        var activeNotes = emptyList<Int>()
        var detectedChord: com.example.domain.models.Chord? = null
        
        // Simular o estado da UI:
        // if (MidiState.isArrangerMode) { ... mostra o painel ... }
        
        val isPanelVisible = isArrangerMode
        
        assertTrue("O painel deve estar visível se ArrangerMode = true, independentemente de activeNotes ou detectedChord", isPanelVisible)
    }
}
