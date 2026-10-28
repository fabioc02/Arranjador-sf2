package com.example.domain.midi

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object FileExporter {
    fun saveStyleFile(context: Context, styleData: ByteArray, fileName: String = "MyStyle.sty") {
        try {
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use {
                it.write(styleData)
            }
            
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export Style File"))
        } catch (e: Exception) {
            e.printStackTrace()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Error sharing: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
