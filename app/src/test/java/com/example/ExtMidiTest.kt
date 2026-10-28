package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.example.ui.validation.ExternalMidiControlPanel

@RunWith(AndroidJUnit4::class)
class ExtMidiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testExtMidiPanel() {
        composeTestRule.setContent {
            ExternalMidiControlPanel(onOpenRhythmCreator = {})
        }
        composeTestRule.waitForIdle()
    }
}
