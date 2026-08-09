package dev.kian.mymettle.data.migration

import android.content.Context
import dev.kian.mymettle.data.local.entity.ExerciseSetupMediaEntity
import java.io.File
import java.util.Base64

class LegacySetupPhotoImporter(private val context: Context) {
    data class Result(
        val media: List<ExerciseSetupMediaEntity>,
        val createdFiles: List<File>,
    )

    fun import(photos: List<LegacySetupPhotoPayload>): Result {
        val created = mutableListOf<File>()
        val media = mutableListOf<ExerciseSetupMediaEntity>()

        try {
            photos.forEach { photo ->
                val bytes = decodeJpeg(photo.dataUrl)
                val exerciseDirectory = File(context.filesDir, "exercise-setup/${safeSegment(photo.exerciseId)}")
                if (!exerciseDirectory.exists() && !exerciseDirectory.mkdirs()) {
                    throw LegacyImportException("Could not create native setup-photo storage.")
                }

                val file = File(exerciseDirectory, "${safeSegment(photo.id)}.jpg")
                val temporary = File(exerciseDirectory, "${safeSegment(photo.id)}.importing")
                temporary.writeBytes(bytes)
                if (file.exists() && !file.delete()) {
                    temporary.delete()
                    throw LegacyImportException("Could not replace an existing setup-photo file.")
                }
                if (!temporary.renameTo(file)) {
                    temporary.delete()
                    throw LegacyImportException("Could not finish writing a setup-photo file.")
                }
                created += file

                val relativePath = file.relativeTo(context.filesDir).invariantSeparatorsPath
                media += ExerciseSetupMediaEntity(
                    id = photo.id,
                    exerciseId = photo.exerciseId,
                    relativePath = relativePath,
                    mimeType = "image/jpeg",
                    sortOrder = photo.sortOrder,
                    createdAt = photo.createdAt,
                    width = photo.width,
                    height = photo.height,
                )
            }
            return Result(media, created)
        } catch (error: Throwable) {
            created.forEach { it.delete() }
            throw error
        }
    }

    fun cleanup(files: Iterable<File>) {
        files.forEach { file ->
            file.delete()
            file.parentFile?.takeIf { it.isDirectory && it.list()?.isEmpty() == true }?.delete()
        }
    }

    private fun decodeJpeg(dataUrl: String): ByteArray {
        val prefix = "data:image/jpeg;base64,"
        if (!dataUrl.startsWith(prefix)) {
            throw LegacyImportException("A setup photo is not a JPEG data URL.")
        }
        val bytes = try {
            Base64.getDecoder().decode(dataUrl.substring(prefix.length))
        } catch (error: IllegalArgumentException) {
            throw LegacyImportException("A setup photo contains invalid base64 data.", error)
        }
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
            throw LegacyImportException("A setup photo does not contain a valid JPEG stream.")
        }
        return bytes
    }

    private fun safeSegment(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(160).ifBlank { "item" }
}
