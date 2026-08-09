package dev.kian.mymettle.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseMemoryEntity
import dev.kian.mymettle.data.local.entity.ExerciseSetupMediaEntity
import dev.kian.mymettle.workout.NativeWorkoutException
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

data class LibraryExercise(
    val exercise: ExerciseEntity,
    val memory: ExerciseMemoryEntity?,
    val targetMuscles: List<String>,
    val cues: List<String>,
    val commonMistakes: List<String>,
    val substitutions: List<String>,
    val setupMedia: List<ExerciseSetupMediaEntity>,
)

class ExerciseLibraryRepository(
    private val context: Context,
    private val database: MyMettleDatabase,
) {
    private val dao get() = database.libraryDao()

    suspend fun all(): List<LibraryExercise> = dao.activeExercises().map { load(it) }

    suspend fun exercise(exerciseId: String): LibraryExercise {
        val exercise = dao.exercise(exerciseId) ?: throw NativeWorkoutException("Exercise not found.")
        return load(exercise)
    }

    suspend fun addSetupPhotos(exerciseId: String, uris: List<Uri>): LibraryExercise {
        if (uris.isEmpty()) return exercise(exerciseId)
        val exercise = dao.exercise(exerciseId) ?: throw NativeWorkoutException("Exercise not found.")
        val existing = dao.setupMedia(exerciseId)
        val capacity = (MAX_SETUP_PHOTOS - existing.size).coerceAtLeast(0)
        if (capacity == 0) throw NativeWorkoutException("This exercise already has $MAX_SETUP_PHOTOS setup photos.")

        val createdFiles = mutableListOf<File>()
        val createdEntities = mutableListOf<ExerciseSetupMediaEntity>()
        try {
            uris.take(capacity).forEachIndexed { index, uri ->
                val media = writeSetupPhoto(
                    exerciseId = exercise.id,
                    uri = uri,
                    sortOrder = existing.size + index,
                )
                createdFiles += File(context.filesDir, media.relativePath)
                createdEntities += media
            }
            database.withTransaction {
                createdEntities.forEach { dao.upsertSetupMedia(it) }
            }
        } catch (error: Throwable) {
            createdFiles.forEach(File::delete)
            throw error
        }
        return exercise(exerciseId)
    }

    suspend fun deleteSetupPhoto(mediaId: String): LibraryExercise {
        val media = dao.setupMediaById(mediaId) ?: throw NativeWorkoutException("Setup photo not found.")
        database.withTransaction { dao.deleteSetupMedia(mediaId) }
        File(context.filesDir, media.relativePath).delete()
        return exercise(media.exerciseId)
    }

    private suspend fun load(exercise: ExerciseEntity): LibraryExercise = LibraryExercise(
        exercise = exercise,
        memory = dao.memory(exercise.id),
        targetMuscles = dao.targetMuscles(exercise.id).map { it.muscle },
        cues = dao.cues(exercise.id).map { it.cue },
        commonMistakes = dao.commonMistakes(exercise.id).map { it.mistake },
        substitutions = dao.substitutions(exercise.id).map { it.substitution },
        setupMedia = dao.setupMedia(exercise.id),
    )

    private fun writeSetupPhoto(
        exerciseId: String,
        uri: Uri,
        sortOrder: Int,
    ): ExerciseSetupMediaEntity {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width.coerceAtLeast(1)
            val height = info.size.height.coerceAtLeast(1)
            val longest = maxOf(width, height)
            if (longest > MAX_LONG_EDGE) {
                val scale = MAX_LONG_EDGE.toFloat() / longest
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
        val outputWidth = bitmap.width
        val outputHeight = bitmap.height

        val id = "setup_${UUID.randomUUID()}"
        val directory = File(context.filesDir, "exercise-setup/${safeSegment(exerciseId)}").apply { mkdirs() }
        val target = File(directory, "${safeSegment(id)}.jpg")
        val temp = File(directory, ".${safeSegment(id)}.tmp")

        try {
            FileOutputStream(temp).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw NativeWorkoutException("Android could not encode that setup photo.")
                }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } finally {
            bitmap.recycle()
            temp.delete()
        }

        return ExerciseSetupMediaEntity(
            id = id,
            exerciseId = exerciseId,
            relativePath = target.relativeTo(context.filesDir).path,
            mimeType = "image/jpeg",
            sortOrder = sortOrder,
            createdAt = Instant.now().toString(),
            width = outputWidth,
            height = outputHeight,
        )
    }

    private fun safeSegment(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    private companion object {
        const val MAX_SETUP_PHOTOS = 12
        const val MAX_LONG_EDGE = 1600
        const val JPEG_QUALITY = 72
    }
}
