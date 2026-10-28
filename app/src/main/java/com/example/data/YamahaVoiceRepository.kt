package com.example.data

import com.example.domain.models.YamahaVoice

object YamahaVoiceRepository {
    val voices = listOf(
        // Piano
        YamahaVoice("Piano", "ConcertGrand", 0, 115, 1),
        YamahaVoice("Piano", "PopGrand", 104, 5, 1),
        YamahaVoice("Piano", "RockPiano", 104, 4, 1),
        YamahaVoice("Piano", "AmbientPiano", 104, 3, 1),
        YamahaVoice("Piano", "CocktailPiano", 104, 0, 4),
        YamahaVoice("Piano", "MIDIGrand", 104, 0, 3),
        YamahaVoice("Piano", "MIDIGrandPad", 104, 1, 3),
        YamahaVoice("Piano", "PianoOrchestra", 104, 2, 1),
        YamahaVoice("Piano", "Harpsichord", 8, 32, 113),
        YamahaVoice("Piano", "GrandPiano", 0, 113, 1),
        YamahaVoice("Piano", "WarmGrand", 0, 114, 1),
        YamahaVoice("Piano", "BrightPiano", 0, 112, 2),
        YamahaVoice("Piano", "OctavePiano1", 0, 113, 4),
        YamahaVoice("Piano", "OctavePiano2", 0, 114, 4),
        YamahaVoice("Piano", "HonkyTonk", 0, 112, 4),
        
        // E.Piano
        YamahaVoice("E.Piano", "SparkleStack", 0, 121, 6),
        YamahaVoice("E.Piano", "SweetDX", 104, 0, 6),
        YamahaVoice("E.Piano", "BalladDX", 0, 124, 6),
        YamahaVoice("E.Piano", "DX Dynamics", 0, 123, 6),
        YamahaVoice("E.Piano", "BalladBells", 104, 2, 6),
        YamahaVoice("E.Piano", "SuitcaseEP", 0, 118, 5),
        YamahaVoice("E.Piano", "VintageEP", 0, 116, 5),
        YamahaVoice("E.Piano", "CP80", 0, 113, 3),
        YamahaVoice("E.Piano", "GalaxyEP", 0, 114, 5),
        YamahaVoice("E.Piano", "StageEP", 0, 117, 5),
        YamahaVoice("E.Piano", "SmoothTine", 0, 119, 6),
        YamahaVoice("E.Piano", "ElectricPiano", 0, 119, 5),
        YamahaVoice("E.Piano", "Clavi", 0, 112, 8),
        YamahaVoice("E.Piano", "WahClavi", 0, 113, 8),

        // Strings
        YamahaVoice("Strings", "ConcertStrings", 8, 32, 50),
        YamahaVoice("Strings", "StudioStrings", 8, 32, 49),
        YamahaVoice("Strings", "Strings", 0, 117, 50),
        YamahaVoice("Strings", "MovieStrings", 0, 123, 49),
        YamahaVoice("Strings", "Violin", 0, 113, 41),
        YamahaVoice("Strings", "Pizzicato", 0, 113, 46),
        YamahaVoice("Strings", "OrchestralHarp", 104, 1, 47),
        YamahaVoice("Strings", "Strings f", 0, 119, 49),
        YamahaVoice("Strings", "Strings mf", 0, 118, 49),
        YamahaVoice("Strings", "Strings p", 0, 117, 49),
        YamahaVoice("Strings", "Allegro", 0, 122, 50),
        YamahaVoice("Strings", "Spiccato", 8, 33, 49),
        YamahaVoice("Strings", "Tutti", 0, 120, 50),
        YamahaVoice("Strings", "OrchestraHit", 0, 112, 56),

        // Guitar&Bass
        YamahaVoice("Guitar&Bass", "StageLead", 8, 38, 6),
        YamahaVoice("Guitar&Bass", "OverdriveWah", 8, 39, 6),
        YamahaVoice("Guitar&Bass", "ConcertGuitar", 8, 32, 1),
        YamahaVoice("Guitar&Bass", "SteelGuitar", 8, 32, 2),
        YamahaVoice("Guitar&Bass", "ElectroAcoustic", 8, 35, 1),
        YamahaVoice("Guitar&Bass", "JazzVintage", 8, 38, 7),
        YamahaVoice("Guitar&Bass", "PureVintage", 8, 42, 6),
        YamahaVoice("Guitar&Bass", "MetalMaster", 0, 120, 31),
        YamahaVoice("Guitar&Bass", "FlamencoGtr", 8, 33, 1),
        YamahaVoice("Guitar&Bass", "12StringGtr", 0, 113, 26),
        YamahaVoice("Guitar&Bass", "NylonGuitar", 8, 34, 1),
        YamahaVoice("Guitar&Bass", "FolkGuitar", 8, 33, 2),
        YamahaVoice("Guitar&Bass", "JazzClean", 8, 32, 7),
        YamahaVoice("Guitar&Bass", "PedalSteel", 8, 36, 4),
        YamahaVoice("Guitar&Bass", "WarmElectric", 8, 32, 4),
        YamahaVoice("Guitar&Bass", "HeavyRock", 8, 32, 5),
        
        // MegaVoices - Guitar & Bass
        YamahaVoice("Guitar&Bass (Mega)", "SmallStrings", 8, 0, 49),
        YamahaVoice("Guitar&Bass (Mega)", "LargeStrings", 8, 0, 50),
        YamahaVoice("Guitar&Bass (Mega)", "SingleCoil", 8, 3, 4),
        YamahaVoice("Guitar&Bass (Mega)", "JazzGuitar", 8, 0, 7),
        YamahaVoice("Guitar&Bass (Mega)", "NylonGuitar", 8, 0, 1),
        YamahaVoice("Guitar&Bass (Mega)", "SteelGuitar", 8, 0, 2),
        YamahaVoice("Guitar&Bass (Mega)", "12StringGtr", 8, 1, 3),
        YamahaVoice("Guitar&Bass (Mega)", "SolidGuitar1", 8, 1, 4),
        YamahaVoice("Guitar&Bass (Mega)", "CleanGuitar", 8, 0, 4),
        YamahaVoice("Guitar&Bass (Mega)", "OverdriveGtr", 8, 0, 5),
        YamahaVoice("Guitar&Bass (Mega)", "DistortionGtr", 8, 0, 6),
        YamahaVoice("Guitar&Bass (Mega)", "AcousticBass", 8, 0, 17),
        YamahaVoice("Guitar&Bass (Mega)", "ElectricBass", 8, 0, 18),
        YamahaVoice("Guitar&Bass (Mega)", "PickBass", 8, 0, 19),
        YamahaVoice("Guitar&Bass (Mega)", "FretlessBass", 8, 0, 20),
        
        YamahaVoice("Guitar&Bass", "AcousticBass", 0, 112, 33),
        YamahaVoice("Guitar&Bass", "FretlessBass", 0, 112, 36),
        YamahaVoice("Guitar&Bass", "SlapBass", 0, 112, 37),
        YamahaVoice("Guitar&Bass", "ElectricBass", 0, 114, 34),
        YamahaVoice("Guitar&Bass", "PickBass", 0, 112, 35),
        YamahaVoice("Guitar&Bass", "MellowFinger", 0, 112, 34),
        YamahaVoice("Guitar&Bass", "RockBass", 0, 114, 35),

        // Saxophone
        YamahaVoice("Saxophone", "Saxophone", 8, 32, 83),
        YamahaVoice("Saxophone", "BigBandSax", 8, 35, 83),
        YamahaVoice("Saxophone", "JazzTenor", 0, 125, 67),
        YamahaVoice("Saxophone", "PopTenor", 0, 127, 67),
        YamahaVoice("Saxophone", "SopranoSax", 0, 113, 65),
        YamahaVoice("Saxophone", "AltoSax", 0, 114, 66),
        YamahaVoice("Saxophone", "TenorSax", 0, 117, 67),
        YamahaVoice("Saxophone", "SaxSection", 0, 116, 67),
        
        // MegaVoices - Brass / Sax
        YamahaVoice("Saxophone (Mega)", "TenorSax", 8, 0, 83),
        YamahaVoice("Brass (Mega)", "Trumpet", 8, 0, 65),
        YamahaVoice("Brass (Mega)", "Brass", 8, 0, 57),

        // Flute&Woodwind
        YamahaVoice("Flute&Woodwind", "OrchFlute", 104, 0, 74),
        YamahaVoice("Flute&Woodwind", "OrchOboe", 104, 0, 69),
        YamahaVoice("Flute&Woodwind", "OrchClarinet", 104, 0, 72),
        YamahaVoice("Flute&Woodwind", "ClassicalFlute", 0, 115, 74),
        YamahaVoice("Flute&Woodwind", "Flute", 0, 114, 74),
        YamahaVoice("Flute&Woodwind", "PanFlute", 0, 113, 76),

        // Organ
        YamahaVoice("Organ", "WhiterBars", 8, 32, 30),
        YamahaVoice("Organ", "JazzRotary", 8, 32, 114),
        YamahaVoice("Organ", "ClassicBars", 8, 34, 30),
        YamahaVoice("Organ", "Organ-a-Gogo", 104, 0, 17),
        YamahaVoice("Organ", "VintageFast", 0, 127, 17),
        YamahaVoice("Organ", "RotorOrgan", 0, 117, 19),
        YamahaVoice("Organ", "JazzOrgan", 0, 117, 17),
        YamahaVoice("Organ", "RockRotary", 8, 33, 114),
        YamahaVoice("Organ", "OrganAccomp1", 0, 108, 18),
        YamahaVoice("Organ", "PipeOrgan", 0, 112, 20),

        // Trumpet / Brass
        YamahaVoice("Trumpet", "Trumpet", 8, 32, 65),
        YamahaVoice("Trumpet", "SilverTrumpet", 8, 33, 65),
        YamahaVoice("Trumpet", "GoldenTrumpet", 8, 34, 65),
        YamahaVoice("Trumpet", "Trombone", 0, 117, 58),
        YamahaVoice("Trumpet", "Cornet", 0, 119, 57),
        YamahaVoice("Trumpet", "FlugelHorn", 0, 118, 57),
        YamahaVoice("Trumpet", "MutedTrumpet", 0, 114, 60),

        YamahaVoice("Brass", "BigBandBrass", 8, 37, 57),
        YamahaVoice("Brass", "SmoothBrass", 8, 36, 57),
        YamahaVoice("Brass", "DynamicBrass", 0, 127, 62),
        YamahaVoice("Brass", "FrenchHorns", 0, 112, 61),
        YamahaVoice("Brass", "SymphonyBrass", 0, 119, 61),
        YamahaVoice("Brass", "Brass f", 0, 108, 62),
        YamahaVoice("Brass", "BrassFalls f", 8, 34, 57),
        YamahaVoice("Brass", "BrassShake", 8, 32, 57),

        // Choir&Pad
        YamahaVoice("Choir&Pad", "GospelVoices", 0, 116, 53),
        YamahaVoice("Choir&Pad", "Humming", 0, 118, 53),
        YamahaVoice("Choir&Pad", "SweetHeaven", 0, 118, 89),
        YamahaVoice("Choir&Pad", "GothicVox", 0, 113, 54),
        YamahaVoice("Choir&Pad", "NewAtmosphere", 104, 4, 90),
        YamahaVoice("Choir&Pad", "DarkFatSaw", 104, 2, 90),
        YamahaVoice("Choir&Pad", "AnalogPad", 0, 120, 90),
        YamahaVoice("Choir&Pad", "Choir", 0, 112, 53),

        // Synth&FX
        YamahaVoice("Synth&FX", "ClubLead", 104, 3, 63),
        YamahaVoice("Synth&FX", "SoftSaw", 104, 16, 82),
        YamahaVoice("Synth&FX", "Matrix", 0, 123, 82),
        YamahaVoice("Synth&FX", "SoftR&B", 0, 119, 81),
        YamahaVoice("Synth&FX", "LektroCodes", 104, 2, 85),
        YamahaVoice("Synth&FX", "TrancePerc", 104, 5, 82),

        // Perc&Drum
        YamahaVoice("Perc&Drum", "Vibraphone", 0, 112, 12),
        YamahaVoice("Perc&Drum", "Marimba", 0, 112, 13),
        YamahaVoice("Perc&Drum", "Xylophone", 0, 112, 14),
        YamahaVoice("Perc&Drum", "Glockenspiel", 0, 112, 10),
        YamahaVoice("Perc&Drum", "TubularBells", 0, 112, 15),
        YamahaVoice("Perc&Drum", "Timpani", 0, 112, 48),
        
        YamahaVoice("Perc&Drum", "StandardKit1", 127, 0, 1),
        YamahaVoice("Perc&Drum", "StandardKit2", 127, 0, 2),
        YamahaVoice("Perc&Drum", "HitKit", 127, 0, 5),
        YamahaVoice("Perc&Drum", "RoomKit", 127, 0, 9),
        YamahaVoice("Perc&Drum", "RockKit", 127, 0, 17),
        YamahaVoice("Perc&Drum", "ElectroKit", 127, 0, 25),
        YamahaVoice("Perc&Drum", "AnalogKit", 127, 0, 26),
        YamahaVoice("Perc&Drum", "DanceKit", 127, 0, 28),
        YamahaVoice("Perc&Drum", "JazzKit", 127, 0, 33),
        YamahaVoice("Perc&Drum", "BrushKit", 127, 0, 41),
        YamahaVoice("Perc&Drum", "SymphonyKit", 127, 0, 49)
    )

    val categories = voices.map { it.category }.distinct()

    fun getVoicesByCategory(category: String): List<YamahaVoice> {
        return voices.filter { it.category == category }
    }
}
