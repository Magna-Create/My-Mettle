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
        validateBatch(photos)
        val created = mutableListOf<File>()
        val media = mutableListOf<ExerciseSetupMediaEntity>()

        try {
            photos.forEach { photo ->
                validatePayload(photo)
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

    /** Validates backup photo payloads without creating files. Used by isolated acceptance checks. */
    fun validate(photos: List<LegacySetupPhotoPayload>) {
        validateBatch(photos)
        photos.forEach { photo ->
            validatePayload(photo)
            decodeJpeg(photo.dataUrl)
        }
    }

    private fun validateBatch(photos: List<LegacySetupPhotoPayload>) {
        if (photos.size > MAX_SETUP_PHOTO_COUNT) {
            throw LegacyImportException("A Lite backup contains too many setup photos.")
        }
        val encodedCharacters = photos.sumOf { it.dataUrl.length.toLong() }
        if (encodedCharacters > MAX_TOTAL_DATA_URL_CHARS) {
            throw LegacyImportException("Lite setup-photo payloads are too large to import safely.")
        }
    }

    private fun validatePayload(photo: LegacySetupPhotoPayload) {
        if (photo.width !in 1..MAX_IMAGE_DIMENSION || photo.height !in 1..MAX_IMAGE_DIMENSION) {
            throw LegacyImportException("A setup photo has unsupported dimensions.")
        }
        if (photo.dataUrl.length > MAX_DATA_URL_CHARS) {
            throw LegacyImportException("A setup photo payload is too large to import safely.")
        }
    }

    private fun decodeJpeg(dataUrl: String): ByteArray {
        val prefix = "data:image/jpeg;base64,"
        if (!dataUrl.startsWith(prefix)) {
            throw LegacyImportException("A setup photo is not a JPEG data URL.")
        }
        val encoded = dataUrl.substring(prefix.length)
        if (encoded.length > MAX_DATA_URL_CHARS - prefix.length) {
            throw LegacyImportException("A setup photo payload is too large to import safely.")
        }
        val bytes = try {
            Base64.getDecoder().decode(encoded)
        } catch (error: IllegalArgumentException) {
            throw LegacyImportException("A setup photo contains invalid base64 data.", error)
        }
        if (bytes.size > MAX_DECODED_BYTES) {
            throw LegacyImportException("A decoded setup photo is too large to import safely.")
        }
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
            throw LegacyImportException("A setup photo does not contain a valid JPEG stream.")
        }
        return bytes
    }

    private fun safeSegment(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(160).ifBlank { "item" }

    private companion object {
        const val MAX_SETUP_PHOTO_COUNT = 256
        const val MAX_IMAGE_DIMENSION = 12_000
        const val MAX_DATA_URL_CHARS = 12 * 1024 * 1024
        const val MAX_DECODED_BYTES = 8 * 1024 * 1024
        const val MAX_TOTAL_DATA_URL_CHARS = 64L * 1024L * 1024L
    }
}
