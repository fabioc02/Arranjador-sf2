package com.example.domain.models

enum class StyleSection(val sectionName: String) {
    IntroA("Intro A"),
    IntroB("Intro B"),
    IntroC("Intro C"),
    IntroD("Intro D"),
    MainA("Main A"),
    MainB("Main B"),
    MainC("Main C"),
    MainD("Main D"),
    FillInAA("Fill In AA"),
    FillInAB("Fill In AB"),
    FillInBA("Fill In BA"),
    FillInBB("Fill In BB"),
    FillInCC("Fill In CC"),
    FillInCD("Fill In CD"),
    FillInDD("Fill In DD"),
    Break("Break"),
    EndingA("Ending A"),
    EndingB("Ending B"),
    EndingC("Ending C"),
    EndingD("Ending D"),
    Unknown("Unknown");

    companion object {
        fun fromMarker(marker: String): StyleSection {
            return values().find { it.sectionName.equals(marker, ignoreCase = true) } ?: Unknown
        }
    }
}
