package com.example.domain.rhythm

import android.content.Context
import com.example.DrumKeyParams
import com.example.RecordedCC
import com.example.RecordedNote
import com.example.TrackState
import com.example.domain.soundfont.model.SoundFontType
import com.example.domain.soundfont.model.SoundFontVoice
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedRhythmHeader(
    val id: String,
    val name: String,
    val dateMs: Long,
    val bpm: Float,
    val measures: Int,
    val usedSoundFontsCount: Int
)

data class SavedRhythmProject(
    val id: String,
    val name: String,
    val dateMs: Long,
    val bpm: Float,
    val measures: Int,
    val timeSigNum: Int,
    val timeSigDen: Int,
    val sectionTracks: Map<String, List<TrackState>>
)

object RhythmProjectManager {
    private const val DIR_NAME = "saved_rhythms"

    private fun getStorageDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listSavedRhythms(context: Context): List<SavedRhythmHeader> {
        val dir = getStorageDir(context)
        val files = dir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        val headers = mutableListOf<SavedRhythmHeader>()

        for (file in files.sortedByDescending { it.lastModified() }) {
            try {
                val jsonStr = file.readText()
                val json = JSONObject(jsonStr)
                val id = json.optString("id", file.nameWithoutExtension)
                val name = json.optString("name", file.nameWithoutExtension)
                val dateMs = json.optLong("dateMs", file.lastModified())
                val bpm = json.optDouble("bpm", 120.0).toFloat()
                val measures = json.optInt("measures", 2)
                val sfCount = json.optInt("usedSoundFontsCount", 0)

                headers.add(
                    SavedRhythmHeader(
                        id = id,
                        name = name,
                        dateMs = dateMs,
                        bpm = bpm,
                        measures = measures,
                        usedSoundFontsCount = sfCount
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return headers
    }

    fun saveRhythm(
        context: Context,
        name: String,
        bpm: Float,
        measures: Int,
        timeSigNum: Int,
        timeSigDen: Int,
        sectionTracks: Map<com.example.StyleSection, List<TrackState>>
    ): SavedRhythmProject {
        val dir = getStorageDir(context)
        val id = "rhythm_" + System.currentTimeMillis()
        val file = File(dir, "$id.json")

        val root = JSONObject()
        root.put("id", id)
        root.put("name", name)
        val dateMs = System.currentTimeMillis()
        root.put("dateMs", dateMs)
        root.put("bpm", bpm.toDouble())
        root.put("measures", measures)
        root.put("timeSigNum", timeSigNum)
        root.put("timeSigDen", timeSigDen)

        var sfCount = 0
        val sectionsObj = JSONObject()
        val stringKeyedMap = mutableMapOf<String, List<TrackState>>()

        sectionTracks.forEach { (section, trackStates) ->
            val tracksArray = JSONArray()
            trackStates.forEach { track ->
                val trackObj = JSONObject()
                trackObj.put("name", track.name)
                trackObj.put("channel", track.channel)
                trackObj.put("bankMsb", track.bankMsb)
                trackObj.put("bankLsb", track.bankLsb)
                trackObj.put("program", track.program)
                trackObj.put("volume", track.volume)
                trackObj.put("pan", track.pan)
                trackObj.put("reverb", track.reverb)
                trackObj.put("chorus", track.chorus)
                trackObj.put("dsp", track.dsp)
                trackObj.put("isMuted", track.isMuted)
                trackObj.put("isDrumTrack", track.isDrumTrack)

                // SoundFont Voice serialization
                val sf = track.soundFontVoice
                if (sf != null) {
                    sfCount++
                    val sfObj = JSONObject()
                    sfObj.put("id", sf.id)
                    sfObj.put("name", sf.name)
                    sfObj.put("category", sf.category)
                    sfObj.put("filePath", sf.filePath)
                    sfObj.put("fileType", sf.fileType.name)
                    sfObj.put("bank", sf.bank)
                    sfObj.put("program", sf.program)
                    sfObj.put("isStereo", sf.isStereo)
                    sfObj.put("fileSizeFormatted", sf.fileSizeFormatted)
                    trackObj.put("soundFontVoice", sfObj)
                }

                // Notes
                val notesArray = JSONArray()
                track.notes.forEach { n ->
                    val nObj = JSONObject()
                    nObj.put("tick", n.tick)
                    nObj.put("note", n.note)
                    nObj.put("velocity", n.velocity)
                    nObj.put("durationTicks", n.durationTicks)
                    notesArray.put(nObj)
                }
                trackObj.put("notes", notesArray)

                // CC Events
                val ccArray = JSONArray()
                track.ccEvents.forEach { c ->
                    val cObj = JSONObject()
                    cObj.put("tick", c.tick)
                    cObj.put("cc", c.cc)
                    cObj.put("value", c.value)
                    ccArray.put(cObj)
                }
                trackObj.put("ccEvents", ccArray)

                // Drum Params
                if (track.drumParams.isNotEmpty()) {
                    val dpObj = JSONObject()
                    track.drumParams.forEach { (key, params) ->
                        val pObj = JSONObject()
                        pObj.put("pitchCoarse", params.pitchCoarse)
                        pObj.put("pitchFine", params.pitchFine)
                        pObj.put("level", params.level)
                        pObj.put("pan", params.pan)
                        pObj.put("reverb", params.reverb)
                        pObj.put("chorus", params.chorus)
                        dpObj.put(key.toString(), pObj)
                    }
                    trackObj.put("drumParams", dpObj)
                }

                tracksArray.put(trackObj)
            }
            sectionsObj.put(section.name, tracksArray)
            stringKeyedMap[section.name] = trackStates
        }

        root.put("usedSoundFontsCount", sfCount)
        root.put("sections", sectionsObj)

        file.writeText(root.toString(2))

        return SavedRhythmProject(
            id = id,
            name = name,
            dateMs = dateMs,
            bpm = bpm,
            measures = measures,
            timeSigNum = timeSigNum,
            timeSigDen = timeSigDen,
            sectionTracks = stringKeyedMap
        )
    }

    fun loadRhythm(context: Context, id: String): SavedRhythmProject? {
        val dir = getStorageDir(context)
        val file = File(dir, "$id.json")
        if (!file.exists()) return null

        return try {
            val jsonStr = file.readText()
            val root = JSONObject(jsonStr)

            val name = root.optString("name", "Ritmo")
            val dateMs = root.optLong("dateMs", file.lastModified())
            val bpm = root.optDouble("bpm", 120.0).toFloat()
            val measures = root.optInt("measures", 2)
            val timeSigNum = root.optInt("timeSigNum", 4)
            val timeSigDen = root.optInt("timeSigDen", 4)

            val sectionsObj = root.optJSONObject("sections") ?: JSONObject()
            val sectionMap = mutableMapOf<String, List<TrackState>>()

            val keys = sectionsObj.keys()
            while (keys.hasNext()) {
                val secName = keys.next()
                val tracksArray = sectionsObj.optJSONArray(secName) ?: JSONArray()
                val trackList = mutableListOf<TrackState>()

                for (i in 0 until tracksArray.length()) {
                    val trackObj = tracksArray.getJSONObject(i)
                    val tName = trackObj.optString("name", "Track ${i + 1}")
                    val channel = trackObj.optInt("channel", i + 9)
                    val bankMsb = trackObj.optInt("bankMsb", 0)
                    val bankLsb = trackObj.optInt("bankLsb", 112)
                    val program = trackObj.optInt("program", 0)
                    val volume = trackObj.optInt("volume", 100)
                    val pan = trackObj.optInt("pan", 64)
                    val reverb = trackObj.optInt("reverb", 40)
                    val chorus = trackObj.optInt("chorus", 0)
                    val dsp = trackObj.optInt("dsp", 0)
                    val isMuted = trackObj.optBoolean("isMuted", false)
                    val isDrumTrack = trackObj.optBoolean("isDrumTrack", channel == 9 || channel == 10)

                    // SoundFont Voice
                    var sfVoice: SoundFontVoice? = null
                    val sfObj = trackObj.optJSONObject("soundFontVoice")
                    if (sfObj != null) {
                        val sfType = try {
                            SoundFontType.valueOf(sfObj.optString("fileType", "SF2"))
                        } catch (_: Exception) {
                            SoundFontType.SF2
                        }
                        sfVoice = SoundFontVoice(
                            id = sfObj.optString("id"),
                            name = sfObj.optString("name"),
                            category = sfObj.optString("category", "Geral"),
                            filePath = sfObj.optString("filePath"),
                            fileType = sfType,
                            bank = sfObj.optInt("bank", 0),
                            program = sfObj.optInt("program", 0),
                            isStereo = sfObj.optBoolean("isStereo", true),
                            fileSizeFormatted = sfObj.optString("fileSizeFormatted", "")
                        )
                    }

                    // Notes
                    val notesArray = trackObj.optJSONArray("notes") ?: JSONArray()
                    val notesList = mutableListOf<RecordedNote>()
                    for (nIdx in 0 until notesArray.length()) {
                        val nObj = notesArray.getJSONObject(nIdx)
                        notesList.add(
                            RecordedNote(
                                tick = nObj.optInt("tick", 0),
                                note = nObj.optInt("note", 60),
                                velocity = nObj.optInt("velocity", 90),
                                durationTicks = nObj.optInt("durationTicks", 1)
                            )
                        )
                    }

                    // CC Events
                    val ccArray = trackObj.optJSONArray("ccEvents") ?: JSONArray()
                    val ccList = mutableListOf<RecordedCC>()
                    for (cIdx in 0 until ccArray.length()) {
                        val cObj = ccArray.getJSONObject(cIdx)
                        ccList.add(
                            RecordedCC(
                                tick = cObj.optInt("tick", 0),
                                cc = cObj.optInt("cc", 7),
                                value = cObj.optInt("value", 100)
                            )
                        )
                    }

                    // Drum Params
                    val drumParamsMap = mutableMapOf<Int, DrumKeyParams>()
                    val dpObj = trackObj.optJSONObject("drumParams")
                    if (dpObj != null) {
                        val dpKeys = dpObj.keys()
                        while (dpKeys.hasNext()) {
                            val kStr = dpKeys.next()
                            val kInt = kStr.toIntOrNull() ?: continue
                            val pObj = dpObj.getJSONObject(kStr)
                            drumParamsMap[kInt] = DrumKeyParams(
                                pitchCoarse = pObj.optInt("pitchCoarse", 64),
                                pitchFine = pObj.optInt("pitchFine", 64),
                                level = pObj.optInt("level", 127),
                                pan = pObj.optInt("pan", 64),
                                reverb = pObj.optInt("reverb", 0),
                                chorus = pObj.optInt("chorus", 0)
                            )
                        }
                    }

                    trackList.add(
                        TrackState(
                            name = tName,
                            channel = channel,
                            bankMsb = bankMsb,
                            bankLsb = bankLsb,
                            program = program,
                            volume = volume,
                            isMuted = isMuted,
                            isDrumTrack = isDrumTrack,
                            notes = notesList,
                            ccEvents = ccList,
                            pan = pan,
                            reverb = reverb,
                            chorus = chorus,
                            dsp = dsp,
                            drumParams = drumParamsMap,
                            soundFontVoice = sfVoice
                        )
                    )
                }
                sectionMap[secName] = trackList
            }

            SavedRhythmProject(
                id = id,
                name = name,
                dateMs = dateMs,
                bpm = bpm,
                measures = measures,
                timeSigNum = timeSigNum,
                timeSigDen = timeSigDen,
                sectionTracks = sectionMap
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteRhythm(context: Context, id: String): Boolean {
        val dir = getStorageDir(context)
        val file = File(dir, "$id.json")
        return if (file.exists()) file.delete() else false
    }
}
