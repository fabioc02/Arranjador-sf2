package com.example.ui

import android.net.Uri
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.models.StyFile
import com.example.data.parser.StyParser
import com.example.MidiRhythmState
import kotlinx.coroutines.launch
import java.io.File

class ArrangerViewModel : ViewModel() {
    var styFile by mutableStateOf<StyFile?>(null)
    var isParsing by mutableStateOf(false)
    var parseError by mutableStateOf<String?>(null)

    fun loadStyFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            isParsing = true
            parseError = null
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                if (bytes != null) {
                    val parser = StyParser()
                    styFile = parser.parseBytes(bytes, uri.lastPathSegment ?: "Unknown.sty")
                    MidiRhythmState.currentStyle = styFile
                } else {
                    parseError = "Could not read file."
                }
            } catch (e: Exception) {
                parseError = e.message ?: "Unknown error"
            } finally {
                isParsing = false
            }
        }
    }
}
