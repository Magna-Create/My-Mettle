package dev.kian.lab2b.vlm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

object StorageIo {
    fun displayName(context: Context, uri: Uri): String = context.contentResolver.query(uri,
        arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "selected-file"
    fun copyImage(context: Context, uri: Uri, storageFolder: String = "lab2b/images"): SelectedImageInfo {
        val parent = File(context.filesDir, storageFolder).apply { mkdirs() }
        val folder = File(parent, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val source = File(folder, "source")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                source.outputStream().use { output ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= 64L * 1024 * 1024) { "Image exceeds 64 MB" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            return ImagePreprocessor.prepare(source, folder, displayName(context, uri))
        } catch (e: Exception) { folder.deleteRecursively(); throw e }
    }
    fun directoryBytes(path: String?) = path?.let { File(it).walkTopDown().filter(File::isFile).sumOf(File::length) } ?: 0L
}
