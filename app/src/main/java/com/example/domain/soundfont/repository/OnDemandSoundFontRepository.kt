package com.example.domain.soundfont.repository

import com.example.domain.soundfont.model.SoundFontMetadata
import com.example.domain.soundfont.model.SoundFontPreset
import com.example.domain.soundfont.model.SoundFontType
import com.example.domain.soundfont.parser.Sf2Parser
import com.example.domain.soundfont.parser.SfzParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages SoundFont files with strictly bounded RAM consumption.
 * Indexes all files instantly (zero audio loaded), and only loads PCM samples
 * for presets currently assigned to playing rhythm tracks or live solo voices.
 */
class OnDemandSoundFontRepository(
    private val maxLoadedSoundFonts: Int = 10,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    // File path -> Lightweight Metadata (< 20 KB)
    private val catalog = ConcurrentHashMap<String, SoundFontMetadata>()

    // Cache key -> Loaded Preset with active SampleBuffers
    private val loadedPresetCache = ConcurrentHashMap<String, SoundFontPreset>()

    // Access order tracking for LRU eviction
    private val accessOrder = java.util.concurrent.CopyOnWriteArrayList<String>()

    // Fallback default preset (synthesized or generic GM)
    var defaultPreset: SoundFontPreset? = null

    /**
     * Registers a SoundFont file (.sf2 or .sfz). Parses only headers.
     */
    fun registerSoundFont(file: File): SoundFontMetadata? {
        if (!file.exists()) return null
        return try {
            val metadata = when {
                file.name.endsWith(".sf2", ignoreCase = true) -> Sf2Parser.parseMetadata(file)
                file.name.endsWith(".sfz", ignoreCase = true) -> SfzParser.parseMetadata(file)
                else -> null
            }
            if (metadata != null) {
                catalog[file.absolutePath] = metadata
                if (defaultPreset == null && metadata.presets.isNotEmpty()) {
                    defaultPreset = metadata.presets.first()
                }
            }
            metadata
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val availableVoices = androidx.compose.runtime.mutableStateListOf<com.example.domain.soundfont.model.SoundFontVoice>()

    fun getCatalogMetadata(filePath: String): SoundFontMetadata? = catalog[filePath]

    /**
     * Determines the musical category of a soundfont by its parent folder or name.
     */
    fun detectCategory(file: File, presetName: String): String {
        val parent = file.parentFile?.name ?: ""
        val combined = "$parent ${file.nameWithoutExtension} $presetName".lowercase()

        return when {
            combined.contains("drum") || combined.contains("kit") || combined.contains("perc") || combined.contains("bateria") -> "Baterias"
            combined.contains("e.piano") || combined.contains("epiano") || combined.contains("rhodes") || combined.contains("wurli") || combined.contains("clav") || combined.contains("tine") -> "E.Piano"
            combined.contains("piano") || combined.contains("grand") || combined.contains("upright") || combined.contains("ac.piano") -> "Piano"
            combined.contains("organ") || combined.contains("b3") || combined.contains("drawbar") || combined.contains("órgao") || combined.contains("órgão") -> "Órgão"
            combined.contains("bass") || combined.contains("baixo") || combined.contains("fretless") || combined.contains("slap") -> "Baixo"
            combined.contains("guitar") || combined.contains("violao") || combined.contains("violão") || combined.contains("gtr") || combined.contains("nylon") || combined.contains("steel") -> "Guitarra"
            combined.contains("string") || combined.contains("violin") || combined.contains("cello") || combined.contains("cordas") || combined.contains("orch") || combined.contains("ensemble") -> "Cordas"
            combined.contains("brass") || combined.contains("trumpet") || combined.contains("trompete") || combined.contains("trombone") || combined.contains("horn") || combined.contains("metais") -> "Metais"
            combined.contains("sax") || combined.contains("flute") || combined.contains("flauta") || combined.contains("oboe") || combined.contains("clarinet") -> "Sax / Palhetas"
            combined.contains("pad") || combined.contains("choir") || combined.contains("coral") || combined.contains("vox") || combined.contains("voice") || combined.contains("warm") -> "Pad / Coro"
            combined.contains("lead") || combined.contains("synth") || combined.contains("saw") || combined.contains("square") || combined.contains("teclado") -> "Sintetizador"
            else -> if (parent.isNotBlank() && !parent.equals("soundfonts", ignoreCase = true) && !parent.equals("files", ignoreCase = true)) parent else "Outros"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%d KB", bytes / 1024)
            else -> "$bytes B"
        }
    }

    /**
     * Scans a directory for all .sf2 and .sfz files, indexes metadata without loading audio samples,
     * and updates availableVoices list.
     */
    fun scanDirectory(directory: File): List<SoundFontMetadata> {
        val list = mutableListOf<SoundFontMetadata>()
        if (!directory.exists() || !directory.isDirectory) return list

        val newVoices = mutableListOf<com.example.domain.soundfont.model.SoundFontVoice>()

        directory.walkTopDown().forEach { file ->
            if (file.isFile && (file.name.endsWith(".sf2", ignoreCase = true) || file.name.endsWith(".sfz", ignoreCase = true))) {
                val meta = registerSoundFont(file)
                if (meta != null) {
                    list.add(meta)
                    val sizeStr = formatFileSize(file.length())
                    
                    if (meta.presets.isNotEmpty()) {
                        for (preset in meta.presets) {
                            val voiceName = if (meta.presets.size == 1) file.nameWithoutExtension else preset.name.ifBlank { file.nameWithoutExtension }
                            val category = detectCategory(file, voiceName)
                            val voiceId = "${file.absolutePath}:${preset.bank}:${preset.program}"
                            newVoices.add(
                                com.example.domain.soundfont.model.SoundFontVoice(
                                    id = voiceId,
                                    name = voiceName,
                                    category = category,
                                    filePath = file.absolutePath,
                                    fileType = meta.type,
                                    bank = preset.bank,
                                    program = preset.program,
                                    isStereo = meta.isStereo,
                                    fileSizeFormatted = sizeStr
                                )
                            )
                        }
                    } else {
                        // Fallback single voice entry
                        val voiceName = file.nameWithoutExtension
                        val category = detectCategory(file, voiceName)
                        val voiceId = "${file.absolutePath}:0:0"
                        newVoices.add(
                            com.example.domain.soundfont.model.SoundFontVoice(
                                id = voiceId,
                                name = voiceName,
                                category = category,
                                filePath = file.absolutePath,
                                fileType = meta.type,
                                bank = 0,
                                program = 0,
                                isStereo = meta.isStereo,
                                fileSizeFormatted = sizeStr
                            )
                        )
                    }
                }
            }
        }

        // Update availableVoices on main thread
        synchronized(availableVoices) {
            availableVoices.clear()
            availableVoices.addAll(newVoices)
        }
        return list
    }

    /**
     * Gets or loads audio samples for a specific preset on-demand.
     */
    fun getOrLoadPreset(metadata: SoundFontMetadata, bank: Int, program: Int): SoundFontPreset? {
        val rawPreset = metadata.getPreset(bank, program) ?: return null
        val cacheKey = "${metadata.filePath}:${rawPreset.bank}:${rawPreset.program}"

        // Return if already loaded in RAM
        val cached = loadedPresetCache[cacheKey]
        if (cached != null) {
            touchAccess(cacheKey)
            return cached
        }

        // Perform lazy load of PCM samples
        synchronized(this) {
            // Check double-checked locking
            val existing = loadedPresetCache[cacheKey]
            if (existing != null) {
                touchAccess(cacheKey)
                return existing
            }

            // Evict if cache limit reached
            if (loadedPresetCache.size >= maxLoadedSoundFonts) {
                evictOldest()
            }

            val file = File(metadata.filePath)
            if (file.exists()) {
                when (metadata.type) {
                    SoundFontType.SF2 -> {
                        Sf2Parser.loadSamplesForPreset(file, metadata, rawPreset)
                    }
                    SoundFontType.SFZ -> {
                        SfzParser.loadSamplesForPreset(file, rawPreset)
                    }
                }
            }

            loadedPresetCache[cacheKey] = rawPreset
            touchAccess(cacheKey)
            return rawPreset
        }
    }

    /**
     * Preloads all presets required by active tracks asynchronously in background.
     * Prevents audio glitches during live performance and style starts.
     */
    fun preloadTrackVoices(
        trackVoiceRequests: List<Pair<SoundFontMetadata, Pair<Int, Int>>>,
        onComplete: (() -> Unit)? = null
    ) {
        coroutineScope.launch {
            for ((metadata, bankProg) in trackVoiceRequests) {
                try {
                    getOrLoadPreset(metadata, bankProg.first, bankProg.second)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            onComplete?.invoke()
        }
    }

    /**
     * Finds the best matching loaded or cataloged SoundFont preset for a given channel / bank / program.
     */
    fun findPresetForChannel(channel: Int, bank: Int, program: Int): SoundFontPreset? {
        // Try exact cache hit first
        for ((_, preset) in loadedPresetCache) {
            if (preset.bank == bank && preset.program == program) return preset
        }

        // Try searching catalog
        for ((_, meta) in catalog) {
            val match = meta.presets.find { it.bank == bank && it.program == program }
            if (match != null) {
                return getOrLoadPreset(meta, bank, program)
            }
        }

        // Return first loaded preset or default
        return loadedPresetCache.values.firstOrNull() ?: defaultPreset
    }

    private fun touchAccess(key: String) {
        accessOrder.remove(key)
        accessOrder.add(key)
    }

    private fun evictOldest() {
        if (accessOrder.isEmpty()) return
        val oldestKey = accessOrder.removeAt(0)
        val evictedPreset = loadedPresetCache.remove(oldestKey)
        // Clean sample buffers to aid GC
        evictedPreset?.zones?.forEach { zone ->
            zone.sampleBuffer = null
        }
    }

    fun getLoadedCount(): Int = loadedPresetCache.size
    fun getCatalogCount(): Int = catalog.size

    fun isVoiceLoaded(voice: com.example.domain.soundfont.model.SoundFontVoice): Boolean {
        val cacheKey = "${voice.filePath}:${voice.bank}:${voice.program}"
        return loadedPresetCache.containsKey(cacheKey)
    }

    /**
     * Calculates the estimated RAM consumption (in MB) of loaded PCM sample buffers.
     */
    fun getLoadedRamMb(): Float {
        var totalFloats = 0L
        loadedPresetCache.values.forEach { preset ->
            preset.zones.forEach { zone ->
                zone.sampleBuffer?.data?.let { totalFloats += it.size }
            }
        }
        val bytes = totalFloats * 4L
        return (bytes / (1024f * 1024f))
    }

    /**
     * Selectively unloads any presets from RAM that are not currently assigned to active parts/tracks.
     */
    fun evictUnused(activeKeys: Set<String>) {
        val iterator = loadedPresetCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!activeKeys.contains(entry.key)) {
                entry.value.zones.forEach { it.sampleBuffer = null }
                iterator.remove()
                accessOrder.remove(entry.key)
            }
        }
    }

    fun clearCache() {
        loadedPresetCache.values.forEach { preset ->
            preset.zones.forEach { it.sampleBuffer = null }
        }
        loadedPresetCache.clear()
        accessOrder.clear()
    }
}
