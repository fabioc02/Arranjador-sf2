package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RhythmEntity
import com.example.data.RhythmRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

class RhythmViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = RhythmRepository.getDatabase(application).rhythmDao()

    val rhythms = dao.getAllRhythms().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun saveCurrentRhythm(name: String, bpm: Float, measures: Int, events: IntArray?) {
        if (events == null) return
        
        // Convert IntArray to JSON
        val jsonArray = JSONArray()
        for (event in events) {
            jsonArray.put(event)
        }
        val jsonString = jsonArray.toString()

        viewModelScope.launch {
            dao.insertRhythm(
                RhythmEntity(
                    name = name,
                    bpm = bpm,
                    measures = measures,
                    eventsJson = jsonString
                )
            )
        }
    }

    fun loadRhythm(rhythm: RhythmEntity) {
        // Parse JSON back to IntArray
        val jsonArray = JSONArray(rhythm.eventsJson)
        val events = IntArray(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            events[i] = jsonArray.getInt(i)
        }
    }

    fun deleteRhythm(id: Int) {
        viewModelScope.launch {
            dao.deleteRhythm(id)
        }
    }
}
