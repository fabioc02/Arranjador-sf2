package com.example.domain.models

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf

/**
 * Modelos de Domínio para o Arranger Core.
 * Estas classes definem a estrutura musical: notas, eventos CC, trilhas e parâmetros de bateria.
 */

data class ArrangerNote(
    val tick: Int,
    val note: Int,
    val velocity: Int,
    var durationTicks: Int = 480 // 1 semínima (se PPQ = 480)
)

data class ArrangerCcEvent(
    val tick: Int,
    val cc: Int,
    val value: Int
)

data class DrumParam(
    var pitchCoarse: Int = 64,
    var pitchFine: Int = 64,
    var level: Int = 64,
    var pan: Int = 64,
    var reverb: Int = 64,
    var chorus: Int = 64
)

// Representa o Estado/Configuração de uma Trilha de Ritmo
// Como esta classe ainda pode ser observada pela UI, usamos campos normais,
// mas as coleções podem ser manipuladas de forma segura.
class ArrangerTrack(
    var name: String,
    var channel: Int,
    var program: Int,
    var bankMsb: Int,
    var bankLsb: Int,
    var isDrumTrack: Boolean = false,
    var volume: Int = 100,
    var pan: Int = 64,
    var isMuted: Boolean = false
) {
    // Para simplificar a migração e garantir reatividade na UI, podemos usar listas do Compose
    // SE e SOMENTE SE elas não forem escritas na Thread do MIDI.
    // O motor MIDI apenas as lê de forma sincronizada.
    val notes = mutableStateListOf<ArrangerNote>()
    val ccEvents = mutableStateListOf<ArrangerCcEvent>()
    
    // Parâmetros de bateria por nota MIDI (NRPN / XG SysEx)
    val drumParams = mutableStateMapOf<Int, DrumParam>()
}
