package com.example.domain.playback

import com.example.domain.models.StyleSection

class LoopController {
    fun determineNextSection(currentSection: StyleSection): StyleSection {
        return when {
            // Main sections loop indefinitely
            currentSection.sectionName.startsWith("Main") -> currentSection
            
            // Fills transition to their corresponding Main
            currentSection == StyleSection.FillInAA -> StyleSection.MainA
            currentSection == StyleSection.FillInBB -> StyleSection.MainB
            currentSection == StyleSection.FillInCC -> StyleSection.MainC
            currentSection == StyleSection.FillInDD -> StyleSection.MainD
            
            currentSection == StyleSection.FillInAB -> StyleSection.MainB
            currentSection == StyleSection.FillInBA -> StyleSection.MainA
            
            // Intros transition to their corresponding Main
            currentSection == StyleSection.IntroA -> StyleSection.MainA
            currentSection == StyleSection.IntroB -> StyleSection.MainB
            currentSection == StyleSection.IntroC -> StyleSection.MainC
            currentSection == StyleSection.IntroD -> StyleSection.MainD
            
            // Break goes back to Main A by default or could be last main
            currentSection == StyleSection.Break -> StyleSection.MainA
            
            // Endings stop the style
            currentSection.sectionName.startsWith("Ending") -> StyleSection.Unknown
            
            else -> StyleSection.Unknown
        }
    }
}
