package com.example.domain.audio

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

object AudioMetricsReport {
    fun generateReport(
        context: Context,
        durationSeconds: Int,
        xruns: Int,
        avgCallback: Float,
        maxCallback: Float,
        cpuLoad: Float,
        framesRendered: Long
    ): String {
        val json = JSONObject()
        json.put("duration_seconds", durationSeconds)
        json.put("xruns", xruns)
        json.put("avg_callback_ms", avgCallback)
        json.put("max_callback_ms", maxCallback)
        json.put("cpu_load", cpuLoad)
        json.put("frames_rendered", framesRendered)
        json.put("status", if (xruns == 0) "PASS" else "FAIL")

        val file = File(context.filesDir, "audio_stress_report_${System.currentTimeMillis()}.json")
        FileWriter(file).use {
            it.write(json.toString(4))
        }
        return file.absolutePath
    }
}
