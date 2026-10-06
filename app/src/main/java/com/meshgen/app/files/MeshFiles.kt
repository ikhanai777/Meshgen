package com.meshgen.app.files

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.meshgen.core.export.ExportFormat
import com.meshgen.core.export.MeshExporter
import com.meshgen.core.mesh.Mesh
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes exported meshes for sharing (app cache + FileProvider) or into Downloads/MeshGen. */
object MeshFiles {

    fun fileName(baseName: String, format: ExportFormat): String {
        val slug = baseName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "model" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "meshgen-$slug-$stamp.${format.extension}"
    }

    /** Writes to the cache and returns a share-sheet intent. Old exports are cleared first. */
    fun shareIntent(context: Context, mesh: Mesh, format: ExportFormat, baseName: String): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName(baseName, format))
        file.outputStream().buffered().use { MeshExporter.write(mesh, format, it, baseName) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share ${format.label}")
    }

    /** Saves into Downloads/MeshGen (no permission needed on Android 10+). Returns the visible path. */
    fun saveToDownloads(context: Context, mesh: Mesh, format: ExportFormat, baseName: String): String {
        val name = fileName(baseName, format)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            // Generic type so Android keeps our file extension unchanged.
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MeshGen")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Android refused to create the file in Downloads.")
        try {
            resolver.openOutputStream(uri)!!.buffered().use { MeshExporter.write(mesh, format, it, baseName) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        return "Downloads/MeshGen/$name"
    }
}
