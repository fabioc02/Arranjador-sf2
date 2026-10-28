package com.example.domain.audio

data class InstrumentInfo(
    val soundFontId: Int,
    val bank: Int,
    val program: Int,
    val name: String
)

class InstrumentManager(
    private val soundFontManager: SoundFontManager,
    private val synthBackend: SynthBackend
) {
    private val cache = mutableMapOf<String, InstrumentInfo>()
    private val favorites = mutableListOf<InstrumentInfo>()

    fun preloadInstrument(path: String, bank: Int, program: Int, name: String) {
        val sfId = soundFontManager.loadSoundFont(path)
        val info = InstrumentInfo(sfId, bank, program, name)
        cache["$bank-$program"] = info
    }

    fun selectInstrument(channel: Int, bank: Int, program: Int) {
        val info = cache["$bank-$program"]
        if (info != null) {
            synthBackend.bankSelect(channel, info.bank)
            synthBackend.programChange(channel, info.program)
        }
    }

    fun addFavorite(info: InstrumentInfo) {
        if (!favorites.contains(info)) {
            favorites.add(info)
        }
    }

    fun getFavorites(): List<InstrumentInfo> = favorites.toList()
}
