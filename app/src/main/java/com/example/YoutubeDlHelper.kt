package com.example

import android.content.Context
import android.os.Environment
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object YoutubeDlHelper {
    private var isInitialized = false

    suspend fun init(context: Context): String? = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext null
        try {
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            isInitialized = true
            null
        } catch (e: Exception) {
            Log.e("YoutubeDlHelper", "Failed to init", e)
            e.message ?: e.toString()
        }
    }

    suspend fun downloadAudio(url: String, context: Context, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val downloadDir = context.cacheDir
            val request = YoutubeDLRequest(url)
            request.addOption("-x") // extract audio
            request.addOption("--audio-format", "mp3") // we can parse mp3 via our AudioDecoder
            request.addOption("-o", downloadDir.absolutePath + "/%(id)s.%(ext)s")

            val response = YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                onProgress(progress / 100f)
            }
            
            // The response doesn't give us the exact file path easily if the extension changes to .mp3,
            // but we can parse the output or just find the newest file in the directory.
            // Better: specify a static name or parse stdout
            
            val out = response.out
            val m = Regex("Destination: (.*\\.mp3)").find(out)
            if (m != null) {
                return@withContext File(m.groupValues[1])
            }
            
            // fallback, find the newest mp3 in cache
            val files = downloadDir.listFiles { _, name -> name.endsWith(".mp3") }
            return@withContext files?.maxByOrNull { it.lastModified() }
        } catch (e: Exception) {
            Log.e("YoutubeDlHelper", "Download failed", e)
            null
        }
    }
}
