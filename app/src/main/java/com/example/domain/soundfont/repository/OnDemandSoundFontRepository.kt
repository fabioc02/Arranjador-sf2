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
    private val maxLoadedSoundFonts: Int = 32,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    // File path -> Lightweight Metadata (< 20 KB)
    private val catalog = ConcurrentHashMap<String, SoundFontMetadata>()

    // Cache key -> Loaded Preset with active SampleBuffers
    private val loadedPresetCache = ConcurrentHashMap<String, SoundFontPreset>()

    // Access order tracking for LRU eviction
    private val accessOrder = java.util.concurrent.CopyOnWriteArrayList<String>()

    // Keys pinned to RAM (active rhythm tracks + live Right 1, Right 2, Left parts)
    private val pinnedKeys = ConcurrentHashMap.newKeySet<String>()

    // Fallback default preset (synthesized or generic GM)
    var defaultPreset: SoundFontPreset? = null

    val availableVoices = androidx.compose.runtime.mutableStateListOf<com.example.domain.soundfont.model.SoundFontVoice>()

    init {
        // Register built-in starter soundfonts so instruments and audition are immediately available
        val starters = StarterSoundFontLibrary.getAllStarterVoices()
        starters.forEach { voice ->
            val meta = StarterSoundFontLibrary.getStarterMetadata(voice.filePath)
            if (meta != null) {
                catalog[voice.filePath] = meta
                if (defaultPreset == null && meta.presets.isNotEmpty()) {
                    defaultPreset = meta.presets.first()
                }
            }
        }
        availableVoices.addAll(starters)
    }

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

    fun getCatalogMetadata(filePath: String): SoundFontMetadata? {
        val cached = catalog[filePath]
        if (cached != null) return cached
        if (filePath.startsWith(StarterSoundFontLibrary.STARTER_PREFIX)) {
            val starter = StarterSoundFontLibrary.getStarterMetadata(filePath)
            if (starter != null) {
                catalog[filePath] = starter
                return starter
            }
        }
        val file = File(filePath)
        if (file.exists()) {
            return registerSoundFont(file)
        }
        return null
    }

    /**
     * Determines the musical category of a soundfont by its parent folder or name.
     */
    fun detectCategory(file: File, presetName: String): String {
        val parent = file.parentFile?.name ?: ""
        return SoundFontCategoryManager.detectDefaultCategory(file.name, presetName, parent)
    }

    fun updateVoiceCategory(voiceId: String, newCategory: String) {
        synchronized(availableVoices) {
            val index = availableVoices.indexOfFirst { it.id == voiceId }
            if (index != -1) {
                val old = availableVoices[index]
                availableVoices[index] = old.copy(category = newCategory)
            }
        }
    }

    fun refreshCategories() {
        synchronized(availableVoices) {
            val updated = availableVoices.map { voice ->
                val file = java.io.File(voice.filePath)
                val parent = file.parentFile?.name ?: ""
                val cat = SoundFontCategoryManager.getEffectiveCategory(
                    voiceId = voice.id,
                    fileName = file.name,
                    presetName = voice.name,
                    parentFolderName = parent
                )
                if (voice.category != cat) voice.copy(category = cat) else voice
            }
            availableVoices.clear()
            availableVoices.addAll(updated)
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
                    val parentFolder = file.parentFile?.name ?: ""
                    
                    if (meta.presets.isNotEmpty()) {
                        for (preset in meta.presets) {
                            val voiceName = if (meta.presets.size == 1) file.nameWithoutExtension else preset.name.ifBlank { file.nameWithoutExtension }
                            val voiceId = "${file.absolutePath}:${preset.bank}:${preset.program}"
                            val category = SoundFontCategoryManager.getEffectiveCategory(
                                voiceId = voiceId,
                                fileName = file.name,
                                presetName = voiceName,
                                parentFolderName = parentFolder
                            )
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
                        val voiceId = "${file.absolutePath}:0:0"
                        val category = SoundFontCategoryManager.getEffectiveCategory(
                            voiceId = voiceId,
                            fileName = file.name,
                            presetName = voiceName,
                            parentFolderName = parentFolder
                        )
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

        // Always keep starter voices available alongside imported soundfonts
        val starters = StarterSoundFontLibrary.getAllStarterVoices()
        for (st in starters) {
            if (newVoices.none { it.id == st.id }) {
                newVoices.add(st)
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

        if (metadata.filePath.startsWith(StarterSoundFontLibrary.STARTER_PREFIX)) {
            val starterPreset = StarterSoundFontLibrary.getStarterPreset(metadata.filePath, bank, program)
            if (starterPreset != null) {
                loadedPresetCache[cacheKey] = starterPreset
                touchAccess(cacheKey)
                return starterPreset
            }
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
        // Never evict pinned presets (current rhythm tracks or active Right 1, Right 2, Left parts)
        val candidateKey = accessOrder.find { !pinnedKeys.contains(it) } ?: return
        accessOrder.remove(candidateKey)
        val evictedPreset = loadedPresetCache.remove(candidateKey)
        // Clean sample buffers to aid GC
        evictedPreset?.zones?.forEach { zone ->
            zone.sampleBuffer = null
        }
    }

    fun pinVoices(voices: Collection<com.example.domain.soundfont.model.SoundFontVoice>) {
        val keys = voices.map { "${it.filePath}:${it.bank}:${it.program}" }.toSet()
        pinnedKeys.clear()
        pinnedKeys.addAll(keys)
    }

    /**
     * Preloads exactly the SoundFonts required by all rhythm tracks + the 3 live performance parts (R1, R2, L).
     * Bounded strictly to only what is needed, evicting any unused old presets to conserve mobile RAM.
     */
    fun preloadActiveRhythmAndParts(
        rhythmVoices: List<com.example.domain.soundfont.model.SoundFontVoice?>,
        performanceVoices: List<com.example.domain.soundfont.model.SoundFontVoice?>,
        onComplete: (() -> Unit)? = null
    ) {
        val allActiveVoices = (rhythmVoices + performanceVoices).filterNotNull().distinctBy { "${it.filePath}:${it.bank}:${it.program}" }
        pinVoices(allActiveVoices)

        coroutineScope.launch {
            for (voice in allActiveVoices) {
                val meta = catalog[voice.filePath] ?: registerSoundFont(File(voice.filePath))
                if (meta != null) {
                    try {
                        getOrLoadPreset(meta, voice.bank, voice.program)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            // Free any stale presets not used by current rhythm or performance
            val activeKeys = allActiveVoices.map { "${it.filePath}:${it.bank}:${it.program}" }.toSet()
            evictUnused(activeKeys)

            onComplete?.invoke()
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
