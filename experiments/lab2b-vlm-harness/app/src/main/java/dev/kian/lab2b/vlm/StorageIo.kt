package dev.kian.lab2b.vlm

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object StorageIo {
    fun copyModelFolder(context: Context, treeUri: Uri): ModelBundleInfo {
        val resolver = context.contentResolver
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId)

        val stagingParent = File(context.filesDir, "lab2b")
        val inflight = File(stagingParent, "model-staging.inflight")
        val stable = File(stagingParent, "model-staging")
        inflight.deleteRecursively()
        check(inflight.mkdirs()) { "Could not create private model staging directory" }

        val copied = mutableListOf<ModelFileInfo>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

        try {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeIndex)
                    if (DocumentsContract.Document.MIME_TYPE_DIR == mime) continue
                    val name = cursor.getString(nameIndex) ?: continue
                    if (!name.endsWith(".gguf", ignoreCase = true)) continue
                    val documentId = cursor.getString(idIndex)
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val safeName = sanitizeFilename(name)
                    val output = File(inflight, safeName)
                    val digest = MessageDigest.getInstance("SHA-256")
                    resolver.openInputStream(documentUri).use { input ->
                        requireNotNull(input) { "Could not open $name" }
                        FileOutputStream(output).use { target ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                target.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                            }
                        }
                    }
                    copied += ModelFileInfo(
                        name = safeName,
                        bytes = output.length(),
                        sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                        role = if (safeName.contains("mmproj", ignoreCase = true)) ModelFileRole.MMPROJ else ModelFileRole.MAIN,
                    )
                }
            } ?: error("The selected provider did not return the folder contents")

            val bundle = ModelBundleInfo(inflight.absolutePath, copied)
            val errors = bundle.validationErrors()
            require(errors.isEmpty()) { errors.joinToString("; ") }

            stable.deleteRecursively()
            check(inflight.renameTo(stable)) { "Could not atomically promote model staging directory" }
            return bundle.copy(stagingDir = stable.absolutePath)
        } catch (t: Throwable) {
            inflight.deleteRecursively()
            throw t
        }
    }

    fun copyImage(context: Context, uri: Uri): SelectedImageInfo {
        val resolver = context.contentResolver
        val imageDir = File(context.filesDir, "lab2b/images").apply { mkdirs() }
        val source = File(imageDir, "source-image")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open selected image" }
            FileOutputStream(source).use { output -> input.copyTo(output) }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected file is not a decodable image" }

        return SelectedImageInfo(
            sourceName = queryDisplayName(context, uri) ?: "selected-image",
            sourcePrivatePath = source.absolutePath,
            sourceBytes = source.length(),
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
        )
    }

    fun directoryBytes(path: String?): Long {
        if (path.isNullOrBlank()) return 0L
        val root = File(path)
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
        }
    }

    private fun sanitizeFilename(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._+-]"), "_")
}
