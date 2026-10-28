package com.example.domain.playback

import com.example.domain.chord.ChordEngine
import com.example.domain.models.Chord
import com.example.domain.models.ChordType
import com.example.presentation.viewmodel.ValidationPlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChordStateRegressionTest {

    private lateinit var viewModel: ValidationPlayerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ValidationPlayerViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun noteOn(note: Int) {
        viewModel.handleMidiInput(0x92, note, 100)
    }

    private fun noteOff(note: Int) {
        viewModel.handleMidiInput(0x82, note, 0)
    }

    @Test
    fun testNoteOnSetsChord() {
        noteOn(60) // C
        noteOn(64) // E
        noteOn(67) // G
        
        val chord = viewModel.currentChord.value
        assertEquals(0, chord?.rootKey)
        assertEquals(ChordType.Major, chord?.type)
    }

    @Test
    fun testNoteOffDoesNotChangeChord() {
        noteOn(60) // C
        noteOn(64) // E
        noteOn(67) // G
        
        val chord = viewModel.currentChord.value
        assertEquals(0, chord?.rootKey)
        
        noteOff(67) // G
        
        val chordAfterNoteOff = viewModel.currentChord.value
        assertEquals(0, chordAfterNoteOff?.rootKey)
        assertEquals(ChordType.Major, chordAfterNoteOff?.type)
    }

    @Test
    fun testAllNotesOffRetainsChord() {
        noteOn(60) // C
        noteOn(64) // E
        noteOn(67) // G
        
        noteOff(60) // C
        noteOff(64) // E
        noteOff(67) // G
        
        val chordAfterAllNoteOff = viewModel.currentChord.value
        assertEquals(0, chordAfterAllNoteOff?.rootKey)
        assertEquals(ChordType.Major, chordAfterAllNoteOff?.type)
    }

    @Test
    fun testNewNoteOnSetsNewChord() {
        noteOn(60) // C
        noteOn(64) // E
        noteOn(67) // G
        
        noteOff(60) // C
        noteOff(64) // E
        noteOff(67) // G
        
        noteOn(62) // D
        noteOn(66) // F#
        noteOn(69) // A
        
        val newChord = viewModel.currentChord.value
        assertEquals(2, newChord?.rootKey)
        assertEquals(ChordType.Major, newChord?.type)
    }
}
