package com.example.domain.soundfont.model

enum class SoundOutputEngine(
    val id: String,
    val displayName: String,
    val shortName: String,
    val description: String
) {
    YAMAHA_HARDWARE(
        id = "yamaha_hw",
        displayName = "Yamaha PSR-S750 (Hardware USB)",
        shortName = "Yamaha HW",
        description = "Toca usando exclusivamente os timbres, XG e gerador de som nativo do teclado via USB-MIDI."
    ),
    SOUNDFONT_INTERNAL(
        id = "soundfont_int",
        displayName = "SoundFont Interno (SF2 / SFZ)",
        shortName = "SoundFont",
        description = "Toca usando exclusivamente os SoundFonts (Korg Triton e VSTs) carregados no app, direto na interface de áudio ou fone."
    ),
    HYBRID_BOTH(
        id = "hybrid",
        displayName = "Híbrido (Yamaha + SoundFont)",
        shortName = "Híbrido",
        description = "Envia eventos tanto para o gerador físico do teclado quanto para o sintetizador SoundFont simultaneamente."
    )
}
