package com.example.domain.soundfont.repository

import android.content.Context
import com.example.domain.soundfont.model.SoundFontVoice
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages user-defined category classifications and custom tags for SoundFonts.
 * Persists classifications in a JSON file so the user can organize and re-index
 * any SoundFont (e.g. Korg Triton, Rhodes, Pianos, Accordions) into their desired categories.
 */
object SoundFontCategoryManager {

    private const val FILE_NAME = "soundfont_user_categories.json"

    val DEFAULT_CATEGORIES = listOf(
        "Piano",
        "E.Piano",
        "Korg Triton",
        "Acordeon",
        "Órgão",
        "Violão / Guitarra",
        "Baixo",
        "Cordas",
        "Metais",
        "Sax / Sopros",
        "Pad / Coro",
        "Sintetizador",
        "Baterias",
        "Outros"
    )

    // Key (voiceId or fileName) -> Category name
    private val userCategoryMap = ConcurrentHashMap<String, String>()

    // Custom user-created categories
    private val customCategoriesSet = ConcurrentHashMap.newKeySet<String>()

    private var isInitialized = false

    /**
     * Initializes and loads the persistent mappings from disk.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        loadFromDisk(context)
        isInitialized = true
    }

    private fun loadFromDisk(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return

            val content = file.readText()
            if (content.isBlank()) return

            val json = JSONObject(content)
            
            val mapObj = json.optJSONObject("mappings")
            if (mapObj != null) {
                mapObj.keys().forEach { key ->
                    val cat = mapObj.optString(key)
                    if (cat.isNotBlank()) {
                        userCategoryMap[key] = cat
                    }
                }
            }

            val customArr = json.optJSONArray("customCategories")
            if (customArr != null) {
                for (i in 0 until customArr.length()) {
                    val cat = customArr.optString(i)
                    if (cat.isNotBlank()) {
                        customCategoriesSet.add(cat)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveToDisk(context: Context) {
        try {
            val json = JSONObject()
            
            val mapObj = JSONObject()
            userCategoryMap.forEach { (key, cat) ->
                mapObj.put(key, cat)
            }
            json.put("mappings", mapObj)

            val customArr = JSONArray()
            customCategoriesSet.forEach { customArr.put(it) }
            json.put("customCategories", customArr)

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Returns all available categories: Default + Custom user categories.
     */
    fun getAllCategories(): List<String> {
        val list = mutableListOf<String>()
        list.addAll(DEFAULT_CATEGORIES)
        customCategoriesSet.forEach { custom ->
            if (!list.contains(custom)) {
                list.add(custom)
            }
        }
        return list
    }

    /**
     * Adds a brand new custom category.
     */
    fun addCustomCategory(context: Context, categoryName: String): Boolean {
        val trimmed = categoryName.trim()
        if (trimmed.isBlank()) return false
        customCategoriesSet.add(trimmed)
        saveToDisk(context)
        return true
    }

    /**
     * Gets the effective category for a SoundFont voice.
     * 1. Returns user manual assignment if present.
     * 2. Otherwise falls back to smart default heuristic.
     */
    fun getEffectiveCategory(
        voiceId: String,
        fileName: String,
        presetName: String,
        parentFolderName: String
    ): String {
        // 1. Direct voice ID mapping
        userCategoryMap[voiceId]?.let { return it }

        // 2. File name mapping (without extension)
        val baseFileName = fileName.substringBeforeLast(".")
        userCategoryMap[baseFileName]?.let { return it }
        userCategoryMap[fileName]?.let { return it }

        // 3. Fallback to smart heuristic
        return detectDefaultCategory(fileName, presetName, parentFolderName)
    }

    /**
     * Assigns and persists a category to a specific SoundFont voice.
     */
    fun assignCategory(context: Context, voice: SoundFontVoice, newCategory: String) {
        val trimmed = newCategory.trim()
        if (trimmed.isBlank()) return

        // Save by voiceId and base filename
        userCategoryMap[voice.id] = trimmed
        val fileName = File(voice.filePath).name
        val baseName = fileName.substringBeforeLast(".")
        userCategoryMap[fileName] = trimmed
        userCategoryMap[baseName] = trimmed

        if (!DEFAULT_CATEGORIES.contains(trimmed)) {
            customCategoriesSet.add(trimmed)
        }

        saveToDisk(context)
    }

    /**
     * Batch assigns a category to multiple voices.
     */
    fun batchAssignCategory(context: Context, voices: List<SoundFontVoice>, newCategory: String) {
        val trimmed = newCategory.trim()
        if (trimmed.isBlank()) return

        voices.forEach { voice ->
            userCategoryMap[voice.id] = trimmed
            val fileName = File(voice.filePath).name
            val baseName = fileName.substringBeforeLast(".")
            userCategoryMap[fileName] = trimmed
            userCategoryMap[baseName] = trimmed
        }

        if (!DEFAULT_CATEGORIES.contains(trimmed)) {
            customCategoriesSet.add(trimmed)
        }

        saveToDisk(context)
    }

    /**
     * Intelligent classification heuristics avoiding generic false "Piano" assignments.
     */
    fun detectDefaultCategory(fileName: String, presetName: String, parentFolder: String): String {
        val combined = "$parentFolder $fileName $presetName".lowercase()

        return when {
            // Drums & Percussion
            combined.contains("drum") || combined.contains("kit") || combined.contains("perc") || 
            combined.contains("bateria") || combined.contains("cymbal") || combined.contains("snare") ||
            combined.contains("standard_kit") -> "Baterias"

            // Electric Pianos, Rhodes, Wurlitzer, FM Tines (must be tested before generic Piano!)
            combined.contains("rhodes") || combined.contains("wurli") || combined.contains("wurlitzer") ||
            combined.contains("dyno") || combined.contains("tine") || combined.contains("suitcase") ||
            combined.contains("stage73") || combined.contains("stage88") || combined.contains("mark_i") ||
            combined.contains("mark i") || combined.contains("fm_classic") || combined.contains("fm classic") ||
            combined.contains("dx7") || combined.contains("e.piano") || combined.contains("epiano") ||
            combined.contains("electric piano") || combined.contains("clav") || combined.contains("clavi") ||
            combined.contains("cp70") || combined.contains("cp80") -> "E.Piano"

            // Accordion / Sanfona
            combined.contains("acordeon") || combined.contains("accordion") || combined.contains("sanfona") ||
            combined.contains("gaita") || combined.contains("musette") -> "Acordeon"

            // Korg Triton / M1 / Trinity collections
            combined.contains("triton") || combined.contains("korg") || combined.contains("exba") ||
            combined.contains("tr_") || combined.contains("m1") -> "Korg Triton"

            // Acoustic Pianos (only if not rhodes/electric)
            combined.contains("grand") || combined.contains("upright") || combined.contains("steinway") ||
            combined.contains("ac.piano") || combined.contains("piano acústico") || combined.contains("concert") ||
            combined.contains("piano") -> "Piano"

            // Organs
            combined.contains("organ") || combined.contains("b3") || combined.contains("drawbar") ||
            combined.contains("órgao") || combined.contains("órgão") || combined.contains("hammond") ||
            combined.contains("leslie") -> "Órgão"

            // Guitars & Acoustic Guitars
            combined.contains("guitar") || combined.contains("violao") || combined.contains("violão") ||
            combined.contains("gtr") || combined.contains("nylon") || combined.contains("steel") ||
            combined.contains("strat") || combined.contains("tele") || combined.contains("acoustic gtr") -> "Violão / Guitarra"

            // Basses
            combined.contains("bass") || combined.contains("baixo") || combined.contains("fretless") ||
            combined.contains("slap") || combined.contains("contrabaixo") || combined.contains("precision") -> "Baixo"

            // Strings & Orchestral
            combined.contains("string") || combined.contains("cordas") || combined.contains("violin") ||
            combined.contains("cello") || combined.contains("viola") || combined.contains("orch") ||
            combined.contains("ensemble") || combined.contains("spiccato") || combined.contains("pizzicato") -> "Cordas"

            // Brass
            combined.contains("brass") || combined.contains("metais") || combined.contains("trumpet") ||
            combined.contains("trompete") || combined.contains("trombone") || combined.contains("horn") ||
            combined.contains("tuba") -> "Metais"

            // Reeds, Flutes, Saxes
            combined.contains("sax") || combined.contains("flute") || combined.contains("flauta") ||
            combined.contains("oboe") || combined.contains("clarinet") || combined.contains("sopros") ||
            combined.contains("palheta") -> "Sax / Sopros"

            // Pads & Choirs
            combined.contains("pad") || combined.contains("choir") || combined.contains("coral") ||
            combined.contains("coro") || combined.contains("vox") || combined.contains("voice") ||
            combined.contains("warm") || combined.contains("sweep") || combined.contains("atmosphere") -> "Pad / Coro"

            // Synth Leads & Poly
            combined.contains("lead") || combined.contains("synth") || combined.contains("saw") ||
            combined.contains("square") || combined.contains("sintetizador") || combined.contains("moog") ||
            combined.contains("plucked") -> "Sintetizador"

            else -> {
                // If folder has a meaningful category name, use folder name
                if (parentFolder.isNotBlank() &&
                    !parentFolder.equals("soundfonts", ignoreCase = true) &&
                    !parentFolder.equals("files", ignoreCase = true) &&
                    !parentFolder.equals("samples", ignoreCase = true)
                ) {
                    parentFolder
                } else {
                    "Outros"
                }
            }
        }
    }
}
