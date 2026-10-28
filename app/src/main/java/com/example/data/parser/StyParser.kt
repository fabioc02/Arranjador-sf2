package com.example.data.parser

import com.example.domain.models.StyFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StyParser {
    
    private val styReader = StyReader()

    suspend fun parse(file: File): StyFile = withContext(Dispatchers.IO) {
        val data = file.readBytes()
        styReader.read(data, file.name)
    }

    suspend fun parseBytes(data: ByteArray, fileName: String): StyFile = withContext(Dispatchers.IO) {
        styReader.read(data, fileName)
    }
}
