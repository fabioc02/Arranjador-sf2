package com.example.data.parser

class MarkerParser {
    val markers = mutableListOf<String>()
    val markersTimeline = mutableListOf<Pair<Long, String>>()
    var tempoBpm = 120.0
    var timeSignature = "4/4"
    
    fun processMetaEvent(metaType: Int, data: ByteArray, currentTick: Long) {
        when (metaType) {
            0x06 -> { // Marker
                val markerText = String(data, Charsets.US_ASCII)
                markers.add(markerText)
                markersTimeline.add(Pair(currentTick, markerText))
                ParserLogger.logInfo("Found Marker: $markerText at tick $currentTick")
            }
            0x51 -> { // Set Tempo
                if (data.size >= 3) {
                    val tempoUs = ((data[0].toInt() and 0xFF) shl 16) or
                                  ((data[1].toInt() and 0xFF) shl 8) or
                                  (data[2].toInt() and 0xFF)
                    if (tempoUs > 0) {
                        tempoBpm = 60000000.0 / tempoUs
                        ParserLogger.logInfo("Found Tempo: $tempoBpm BPM at tick $currentTick")
                    }
                }
            }
            0x58 -> { // Time Signature
                if (data.size >= 2) {
                    val nn = data[0].toInt() and 0xFF
                    val dd = data[1].toInt() and 0xFF
                    val denominator = 1 shl dd
                    timeSignature = "$nn/$denominator"
                    ParserLogger.logInfo("Found Time Signature: $timeSignature at tick $currentTick")
                }
            }
        }
    }
}
