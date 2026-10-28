package com.example.domain.soundfont.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.MidiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manages storage, file imports, folder tree traversal, and scanning for SoundFont (SF2 / SFZ) libraries.
 * Keeps SoundFont indexing lightweight (zero sample RAM) while allowing flexible file management.
 */
object SoundFontStorageManager {

    private const val PREFS_NAME = "soundfont_storage_prefs"
    private const val KEY_LAST_FOLDER = "last_imported_folder"

    val STANDARD_CATEGORIES = listOf(
        "Piano", "E.Piano", "Órgão", "Guitarra", "Baixo", "Cordas", 
        "Metais", "Sax / Palhetas", "Pad / Coro", "Sintetizador", "Baterias", "Outros"
    )

    /**
     * Returns the primary application SoundFonts directory:
     * Android/data/com.aistudio.arrangerpro.okhqcd/files/soundfonts
     */
    fun getDefaultSoundFontsDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val sfDir = File(base, "soundfonts")
        if (!sfDir.exists()) {
            sfDir.mkdirs()
            // Create default category subdirectories for user organization
            STANDARD_CATEGORIES.forEach { cat ->
                val safeCat = cat.replace(" / ", "_").replace(" ", "_").replace("Ó", "O").replace("ó", "o")
                File(sfDir, safeCat).mkdirs()
            }
        }
        return sfDir
    }

    /**
     * Scans the default soundfont directory and standard storage locations.
     */
    suspend fun scanAll(context: Context) = withContext(Dispatchers.IO) {
        val rootDir = getDefaultSoundFontsDir(context)
        MidiState.soundFontRepository.scanDirectory(rootDir)

        // Also scan public storage if accessible
        try {
            val musicSf = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SoundFonts")
            if (musicSf.exists() && musicSf.isDirectory) {
                MidiState.soundFontRepository.scanDirectory(musicSf)
            }
            val downloadSf = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SoundFonts")
            if (downloadSf.exists() && downloadSf.isDirectory) {
                MidiState.soundFontRepository.scanDirectory(downloadSf)
            }
        } catch (e: Exception) {
            // Ignore storage access limits on newer Android
        }
    }

    /**
     * Imports individual SF2 / SFZ files selected via file picker.
     */
    suspend fun importFiles(
        context: Context,
        uris: List<Uri>,
        targetCategory: String? = null,
        onProgress: (fileName: String, current: Int, total: Int) -> Unit = { _, _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val baseDir = getDefaultSoundFontsDir(context)
        val targetDir = if (targetCategory != null && targetCategory != "Todos" && targetCategory != "Outros") {
            val safeCat = targetCategory.replace(" / ", "_").replace(" ", "_")
            File(baseDir, safeCat).apply { mkdirs() }
        } else {
            baseDir
        }

        var importedCount = 0
        val total = uris.size

        uris.forEachIndexed { index, uri ->
            try {
                val fileName = getFileNameFromUri(context, uri) ?: "instrument_${System.currentTimeMillis()}.sf2"
                onProgress(fileName, index + 1, total)

                val destFile = File(targetDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                importedCount++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        scanAll(context)
        importedCount
    }

    /**
     * Recursively traverses a folder selected via Storage Access Framework (SAF) OpenDocumentTree
     * and imports all .sf2, .sfz, and .wav files preserving folder/category hierarchy.
     */
    suspend fun importFolderTree(
        context: Context,
        treeUri: Uri,
        onProgress: (fileName: String, count: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val baseDir = getDefaultSoundFontsDir(context)
        var count = 0

        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            count = traverseAndCopy(context, treeUri, childrenUri, baseDir, onProgress, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        scanAll(context)
        count
    }

    private fun traverseAndCopy(
        context: Context,
        treeUri: Uri,
        childrenUri: Uri,
        targetDir: File,
        onProgress: (fileName: String, count: Int) -> Unit,
        currentCount: Int
    ): Int {
        var count = currentCount
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val cursor: Cursor? = context.contentResolver.query(childrenUri, projection, null, null, null)
        cursor?.use {
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIndex)
                val displayName = cursor.getString(nameIndex) ?: continue
                val mimeType = cursor.getString(mimeIndex)

                if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                    val subChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                    val subTargetDir = File(targetDir, displayName).apply { mkdirs() }
                    count = traverseAndCopy(context, treeUri, subChildrenUri, subTargetDir, onProgress, count)
                } else {
                    val isSf2 = displayName.endsWith(".sf2", ignoreCase = true)
                    val isSfz = displayName.endsWith(".sfz", ignoreCase = true)
                    val isWav = displayName.endsWith(".wav", ignoreCase = true)

                    if (isSf2 || isSfz || isWav) {
                        try {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val destFile = File(targetDir, displayName)
                            if (!destFile.exists() || destFile.length() == 0L) {
                                context.contentResolver.openInputStream(docUri)?.use { input ->
                                    FileOutputStream(destFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                            count++
                            onProgress(displayName, count)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        return count
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) return cursor.getString(nameIdx)
                }
            }
        }
        return uri.path?.let { File(it).name }
    }
}
