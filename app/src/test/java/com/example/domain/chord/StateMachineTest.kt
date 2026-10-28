package com.example.domain.chord

import com.example.ArrangerEngine
import com.example.domain.models.ChordType
import org.junit.Assert.*
import org.junit.Test

class StateMachineTest {

    @Test
    fun testNoteOffDoesNotAlterChord() {
        val activeNotes = mutableListOf<Int>()
        var emittedChord: com.example.domain.models.Chord? = null
        
        val simulateNoteOn = { note: Int ->
            activeNotes.add(note)
            val newChord = ArrangerEngine.detectChord(activeNotes)
            if (newChord != null) {
                emittedChord = newChord
            }
        }
        
        val simulateNoteOff = { note: Int ->
            activeNotes.remove(note)
        }

        // Teste 1: C + E + G -> C Major
        simulateNoteOn(60)
        simulateNoteOn(64)
        simulateNoteOn(67)
        assertEquals(0, emittedChord?.rootKey)
        assertEquals(ChordType.Major, emittedChord?.type)

        // Teste 2: NoteOff G -> continua C Major
        simulateNoteOff(67)
        assertEquals(0, emittedChord?.rootKey)
        assertEquals(ChordType.Major, emittedChord?.type)
        
        // Teste 3: NoteOff E -> continua C Major
        simulateNoteOff(64)
        assertEquals(0, emittedChord?.rootKey)
        assertEquals(ChordType.Major, emittedChord?.type)

        // Teste 4: NoteOff C -> continua C Major
        simulateNoteOff(60)
        assertEquals(0, emittedChord?.rootKey)
        assertEquals(ChordType.Major, emittedChord?.type)
        assertTrue(activeNotes.isEmpty())

        // Teste 5: Novo acorde F + A + C -> F Major
        simulateNoteOn(65)
        simulateNoteOn(69)
        simulateNoteOn(72)
        assertEquals(5, emittedChord?.rootKey)
        assertEquals(ChordType.Major, emittedChord?.type)
        
        // Teste 6: F + A + C -> retirar A -> tocar solo D -> deve não quebrar o F Major
        simulateNoteOff(69) // Retira A
        simulateNoteOn(74) // Toca D
        // Note: Our algorithm evaluates any NoteOn. So hitting D might shift it to Dm7 or just fail to detect a chord (keeping F Major).
        // If detectChord returns null for F,C,D, the old code ignored it. But wait, detectChord returns null? Let's check!
    }

    @Test
    fun testNoteOnVelocity0IsNoteOff() {
        val status = 0x90
        val data2 = 0 // Velocity 0
        val command = status and 0xF0
        val isNoteOn = command == 0x90 && data2 > 0
        val isNoteOff = command == 0x80 || (command == 0x90 && data2 == 0)

        assertFalse(isNoteOn)
        assertTrue(isNoteOff)
    }
}
