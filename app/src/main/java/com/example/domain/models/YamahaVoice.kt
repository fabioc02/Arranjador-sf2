package com.example.domain.models

data class YamahaVoice(
    val category: String,
    val name: String,
    val msb: Int,
    val lsb: Int,
    val pc: Int // 1-128 or 0-127?
) {
    // Para facilitar na UI que fizemos antes, vamos criar um construtor secundário
    constructor(name: String, msb: Int, lsb: Int, pc: Int) : this("Drum Kit", name, msb, lsb, pc)
}
