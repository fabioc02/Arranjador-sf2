package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class ArrangerStudioScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testRhythmCreatorRenders() {
        composeTestRule.setContent {
            MidiRhythmCreatorDialog(visible = true, onDismiss = {})
        }
        
        composeTestRule.onNodeWithText("REC").assertIsDisplayed()
    }
}
