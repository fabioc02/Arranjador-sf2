package com.example.domain.yamaha

/**
 * Mapeamento e Tradução Inteligente de Bateria Yamaha PSR-S750 / GM / Korg Triton.
 * Baseado no manual programming.pdf (Jososoft p. 10) e no PSR-S750 Data List (páginas 23 a 34).
 * Garante que estilos com kits eletrônicos (Analog, Break, Dance, HipHop, Room)
 * toquem com precisão em kits padrão (Standard Kit 1 / 2 / Triton Standard Kit 2).
 */
object YamahaDrumMap {

    /**
     * Tabela de tradução padrão de notas especiais para notas GM / Standard Kit.
     * Mapeia notas de kits alternativos (AnalogT8, AnalogT9, DanceKit, BreakKit, HipHopKit)
     * para suas posições correspondentes no kit padrão.
     */
    val defaultTranslationMap: Map<Int, Int> = mapOf(
        // Clicks e Efeitos graves (13..26)
        13 to 36, // Surdo Mute / Kick Dance 1 -> Kick
        14 to 36, // Surdo Open / Kick Dance 2 -> Kick
        17 to 39, // Scratch H -> Hand Clap
        18 to 39, // Scratch L -> Hand Clap
        19 to 37, // Finger Snap -> Side Stick
        21 to 31, // Metronome Click / Dance Perc -> Sticks
        22 to 42, // Metronome Bell / Reverse Dance -> Closed Hi-Hat
        24 to 36, // Kick Break Deep -> Kick
        25 to 38, // Snare Hip -> Snare
        26 to 38, // Snare Lo-Fi -> Snare
        27 to 38, // Snare Clappy -> Hand Clap / Snare
        28 to 38, // Snare LdwH -> Snare
        29 to 38, // Snare Rock Roll -> Snare
        30 to 38, // Snare Gate -> Snare

        // Variações de Bumbos e Caixas de Kits Eletrônicos (31..34)
        31 to 38, // Snare Soft / Noisy / Techno -> Acoustic Snare (38)
        32 to 31, // Sticks / Snare Dance -> Sticks (31)
        33 to 36, // Kick Soft / Kick 3 / Kick Techno -> Kick (36)
        34 to 37, // Open Rim Shot / Rim Gate -> Side Stick (37)

        // Peças de Break e Eletro agudas (82..91)
        82 to 36, // Kick Break 3 -> Kick
        83 to 36, // Kick Break 4 -> Kick
        84 to 36, // Kick Break 5 -> Kick
        85 to 38, // Snare Hip 1 / Piccolo -> Snare
        86 to 38, // Snare Hip 2 / Snare T8 -> Snare
        87 to 42, // Hi-Hat Closed Break -> Closed Hi-Hat
        88 to 38, // Snare Break 4 -> Snare
        89 to 36, // Kick Blip -> Kick
        90 to 49, // Snare FX / Crash -> Crash Cymbal 1
        91 to 36  // Kick Sustain -> Kick
    )

    /**
     * Resolve a nota de bateria a ser tocada:
     * 1. Se o usuário definiu uma regra manual no VoiceManager, ela tem prioridade total.
     * 2. Se a nota for uma nota padrão (35..81), mantém a nota original.
     * 3. Se a nota for uma peça especial ou kit estendido fora do padrão, aplica a tradução inteligente.
     */
    fun resolveDrumNote(originalNote: Int, userOverrides: Map<Int, Int>? = null): Int {
        userOverrides?.get(originalNote)?.let { return it }
        
        // Se já está no range padrão GM / Triton Standard Kit (35..81), preserva
        if (originalNote in 35..81) {
            return originalNote
        }
        
        // Aplica a tabela de tradução para kits especiais (Dance, Analog, Break, HipHop)
        return defaultTranslationMap[originalNote] ?: originalNote.coerceIn(35, 81)
    }

    /**
     * Informações de instrumentos do Data List para exibição ao usuário
     */
    fun getInstrumentName(note: Int): String = when (note) {
        35 -> "Acoustic Bass Drum (Bumbo Acústico)"
        36 -> "Bass Drum 1 (Bumbo Principal)"
        37 -> "Side Stick (Aro da Caixa)"
        38 -> "Acoustic Snare (Caixa Acústica)"
        39 -> "Hand Clap (Palmas)"
        40 -> "Electric Snare (Caixa Eletrônica)"
        41 -> "Low Floor Tom (Surdo Grave)"
        42 -> "Closed Hi-Hat (Chimbal Fechado)"
        43 -> "High Floor Tom (Surdo Médio)"
        44 -> "Pedal Hi-Hat (Chimbal Pedal)"
        45 -> "Low Tom (Tom Grave)"
        46 -> "Open Hi-Hat (Chimbal Aberto)"
        47 -> "Low-Mid Tom (Tom Médio)"
        48 -> "Hi-Mid Tom (Tom Agudo)"
        49 -> "Crash Cymbal 1 (Prato de Ataque 1)"
        50 -> "High Tom (Tom Super Agudo)"
        51 -> "Ride Cymbal 1 (Prato de Condução)"
        52 -> "Chinese Cymbal (Prato Chinês)"
        53 -> "Ride Bell (Cúpula do Prato)"
        54 -> "Tambourine (Pandeirola)"
        55 -> "Splash Cymbal (Prato Splash)"
        56 -> "Cowbell (Caneca)"
        57 -> "Crash Cymbal 2 (Prato de Ataque 2)"
        58 -> "Vibraslap"
        59 -> "Ride Cymbal 2 (Condução 2)"
        60 -> "Hi Bongo"
        61 -> "Low Bongo"
        62 -> "Mute Hi Conga"
        63 -> "Open Hi Conga"
        64 -> "Low Conga"
        65 -> "High Timbale"
        66 -> "Low Timbale"
        67 -> "High Agogo"
        68 -> "Low Agogo"
        69 -> "Cabasa"
        70 -> "Maracas"
        71 -> "Short Whistle"
        72 -> "Long Whistle"
        73 -> "Short Guiro"
        74 -> "Long Guiro"
        75 -> "Claves"
        76 -> "Hi Wood Block"
        77 -> "Low Wood Block"
        78 -> "Mute Cuica"
        79 -> "Open Cuica"
        80 -> "Mute Triangle"
        81 -> "Open Triangle"
        else -> "Percussão Especial (Nota $note)"
    }
}
