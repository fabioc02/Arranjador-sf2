package com.example.domain.audio

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object AssetHelper {
    fun copyAssetIfNotExist(context: Context, assetName: String, fileName: String): String {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.absolutePath
    }
}
