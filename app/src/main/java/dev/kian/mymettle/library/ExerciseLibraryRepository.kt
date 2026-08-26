package dev.kian.mymettle.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.toDomain
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseMemoryEntity
import dev.kian.mymettle.data.local.entity.ExerciseSetupMediaEntity
import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.EquipmentProfile
import dev.kian.mymettle.domain.exercise.Exercise
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.exercise.ExerciseMemory
import dev.kian.mymettle.domain.exercise.ExerciseSetupMedia
import dev.kian.mymettle.domain.exercise.ExecutionProfile
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersion
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.LoadResolution
import dev.kian.mymettle.domain.exercise.RecruitmentAllocation
import dev.kian.mymettle.domain.exercise.RecruitmentProfile
import dev.kian.mymettle.domain.exercise.RecruitmentProfileVersionId
import dev.kian.mymettle.domain.exercise.RecruitmentRole
import dev.kian.mymettle.domain.exercise.RecruitmentSource
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.workout.NativeWorkoutException
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import org.json.JSONArray

data class WorkoutSetupDetails(
    val exerciseId: String,
    val exerciseInstructions: String,
    val setupInstructions: String,
    val videoReferenceUrl: String,
    /** Nullable presentation slot reserved for the allow-listed on-device note interpreter. */
    val reviewReminder: String? = null,
)

class ExerciseLibraryRepository(
    private val context: Context,
    private val database: MyMettleDatabase,
) {
    private val dao get() = database.libraryDao()
    @Volatile private var muscleNameCache: Map<String, String>? = null

    suspend fun all(): List<Exercise> = dao.activeExercises().map { load(it) }

    suspend fun exercise(exerciseId: String): Exercise {
        val exercise = dao.exercise(exerciseId) ?: throw NativeWorkoutException("Exercise not found.")
        return load(exercise)
    }

    suspend fun workoutSetupDetails(exerciseId: String): WorkoutSetupDetails {
        val exercise = dao.exercise(exerciseId) ?: throw NativeWorkoutException("Exercise not found.")
        val memory = dao.memory(exerciseId)
        return WorkoutSetupDetails(
            exerciseId = exerciseId,
            exerciseInstructions = exercise.essentialCue.orEmpty(),
            setupInstructions = memory?.setupNotes.orEmpty(),
            videoReferenceUrl = memory?.videoReferenceUrl.orEmpty(),
            reviewReminder = null,
        )
    }

    suspend fun updateWorkoutSetupDetails(
        exerciseId: String,
        exerciseInstructions: String,
        setupInstructions: String,
        videoReferenceUrl: String,
    ): WorkoutSetupDetails = database.withTransaction {
        val exercise = dao.exercise(exerciseId) ?: throw NativeWorkoutException("Exercise not found.")
        val currentMemory = dao.memory(exerciseId)
        val now = Instant.now().toString()
        val cleanExerciseInstructions = exerciseInstructions.trim().take(MAX_INSTRUCTION_CHARS)
        val cleanSetupInstructions = setupInstructions.trim().take(MAX_INSTRUCTION_CHARS)
        val cleanUrl = videoReferenceUrl.trim().take(MAX_LINK_CHARS)
        if (cleanUrl.isNotEmpty() && !cleanUrl.startsWith("http://", ignoreCase = true) &&
            !cleanUrl.startsWith("https://", ignoreCase = true)
        ) {
            throw NativeWorkoutException("Exercise links must start with http:// or https://.")
        }

        dao.upsertExercise(
            exercise.copy(
                essentialCue = cleanExerciseInstructions.takeIf(String::isNotEmpty),
                updatedAt = now,
            ),
        )
        dao.upsertMemory(
            currentMemory?.copy(
                setupNotes = cleanSetupInstructions,
                videoReferenceUrl = cleanUrl,
            ) ?: ExerciseMemoryEntity(
                exerciseId = exerciseId,
                category = "",
                equipment = "",
                fatigueCost = 0,
                skillDifficulty = 0,
                setupNotes = cleanSetupInstructions,
                videoReferenceUrl = cleanUrl,
                machineSettings = "",
            ),
        )
        WorkoutSetupDetails(
            exerciseId = exerciseId,
            exerciseInstructions = cleanExerciseInstructions,
            setupInstructions = cleanSetupInstructions,
            videoReferenceUrl = cleanUrl,
            reviewReminder = null,
        )
    }

    suspend fun addSetupPhotos(exerciseId: String, uris: List<Uri>): Exercise {
        if (uris.isEmpty()) return exercise(exerciseId)
        val exercise = dao.exercise(exerciseId) ?: throw NativeWorkoutException("Exercise not found.")
        val existing = dao.setupMedia(exerciseId)
        val capacity = (MAX_SETUP_PHOTOS - existing.size).coerceAtLeast(0)
        if (capacity == 0) throw NativeWorkoutException("This exercise already has $MAX_SETUP_PHOTOS setup photos.")

        val createdFiles = mutableListOf<File>()
        val createdEntities = mutableListOf<ExerciseSetupMediaEntity>()
        try {
            withContext(Dispatchers.IO) {
                uris.take(capacity).forEachIndexed { index, uri ->
                    val media = writeSetupPhoto(
                        exerciseId = exercise.id,
                        source = ImageDecoder.createSource(context.contentResolver, uri),
                        sortOrder = existing.size + index,
                    )
                    createdFiles += File(context.filesDir, media.relativePath)
                    createdEntities += media
                }
            }
            database.withTransaction {
                createdEntities.forEach { dao.upsertSetupMedia(it) }
            }
        } catch (error: Throwable) {
            withContext(Dispatchers.IO) { createdFiles.forEach(File::delete) }
            throw error
        }
        return exercise(exerciseId)
    }

    suspend fun addCapturedSetupPhoto(exerciseId: String, captureFile: File): Exercise {
        val exercise = dao.exercise(exerciseId) ?: throw NativeWorkoutException("Exercise not found.")
        val existing = dao.setupMedia(exerciseId)
        if (existing.size >= MAX_SETUP_PHOTOS) {
            captureFile.delete()
            throw NativeWorkoutException("This exercise already has $MAX_SETUP_PHOTOS setup photos.")
        }

        var createdFile: File? = null
        try {
            val media = withContext(Dispatchers.IO) {
                writeSetupPhoto(
                    exerciseId = exercise.id,
                    source = ImageDecoder.createSource(captureFile),
                    sortOrder = existing.size,
                ).also { createdFile = File(context.filesDir, it.relativePath) }
            }
            database.withTransaction { dao.upsertSetupMedia(media) }
        } catch (error: Throwable) {
            withContext(Dispatchers.IO) { createdFile?.delete() }
            throw error
        } finally {
            withContext(Dispatchers.IO) { captureFile.delete() }
        }
        return exercise(exerciseId)
    }

    suspend fun deleteSetupPhoto(mediaId: String): Exercise {
        val media = dao.setupMediaById(mediaId) ?: throw NativeWorkoutException("Setup photo not found.")
        database.withTransaction { dao.deleteSetupMedia(mediaId) }
        withContext(Dispatchers.IO) { File(context.filesDir, media.relativePath).delete() }
        return exercise(media.exerciseId)
    }

    private suspend fun load(entity: ExerciseEntity): Exercise {
        val memoryEntity = dao.memory(entity.id)
        val executionEntities = dao.executionProfiles(entity.id)
        val versionEntities = dao.executionProfileVersions(executionEntities.map { it.id })
        val schemaEntities = dao.performanceSchemas(versionEntities.map { it.performanceSchemaId }.distinct())
        val schemaMetrics = dao.performanceSchemaMetrics(schemaEntities.map { it.id })
        val recruitmentVersions = dao.recruitmentProfileVersions(versionEntities.map { it.recruitmentProfileVersionId }.distinct())
        val allocationEntities = dao.recruitmentAllocations(recruitmentVersions.map { it.id })
        val segmentIds = allocationEntities.map { it.muscleSegmentId }.distinct()
        val segments = if (segmentIds.isEmpty()) emptyList() else database.referenceDao().segments(segmentIds)
        val segmentById = segments.associateBy { it.id }
        val muscleNameById = if (segments.isEmpty()) emptyMap() else muscleNames()
        val allocationsByRecruitmentVersion = allocationEntities.groupBy { it.recruitmentProfileVersionId }
        val versionsByProfile = versionEntities.groupBy { it.executionProfileId }
        val schemaById = schemaEntities.associateBy { it.id }
        val schemaMetricsById = schemaMetrics.groupBy { it.performanceSchemaId }
        val recruitmentVersionById = recruitmentVersions.associateBy { it.id }

        return Exercise(
            id = ExerciseId(entity.id),
            name = entity.name,
            archived = entity.archived,
            essentialCue = entity.essentialCue,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            memory = memoryEntity?.let { memory ->
                ExerciseMemory(
                    category = memory.category,
                    equipment = memory.equipment,
                    fatigueCost = memory.fatigueCost,
                    skillDifficulty = memory.skillDifficulty,
                    setupNotes = memory.setupNotes,
                    videoReferenceUrl = memory.videoReferenceUrl,
                    machineSettings = memory.machineSettings,
                    cues = dao.cues(entity.id).map { it.cue },
                    commonMistakes = dao.commonMistakes(entity.id).map { it.mistake },
                    substitutions = dao.substitutions(entity.id).map { it.substitution },
                )
            },
            setupMedia = dao.setupMedia(entity.id).map { media ->
                ExerciseSetupMedia(
                    id = media.id,
                    exerciseId = ExerciseId(media.exerciseId),
                    relativePath = media.relativePath,
                    mimeType = media.mimeType,
                    sortOrder = media.sortOrder,
                    createdAt = media.createdAt,
                    width = media.width,
                    height = media.height,
                )
            },
            executionProfiles = executionEntities.map { execution ->
                ExecutionProfile(
                    id = ExecutionProfileId(execution.id),
                    exerciseId = ExerciseId(execution.exerciseId),
                    name = execution.name,
                    isDefault = execution.isDefault,
                    archived = execution.archived,
                    versions = versionsByProfile[execution.id].orEmpty().map { version ->
                        val schemaEntity = requireNotNull(schemaById[version.performanceSchemaId])
                        val recruitmentVersion = requireNotNull(recruitmentVersionById[version.recruitmentProfileVersionId])
                        ExecutionProfileVersion(
                            id = ExecutionProfileVersionId(version.id),
                            executionProfileId = ExecutionProfileId(execution.id),
                            version = version.version,
                            metricFamily = MetricFamily.fromStorage(version.metricFamily),
                            schema = schemaEntity.toDomain(schemaMetricsById[schemaEntity.id].orEmpty()),
                            equipment = EquipmentProfile(version.equipmentIdentity, version.equipmentType),
                            resistanceModel = ResistanceModel(
                                modelVersion = version.resistanceModelVersion,
                                semantics = ResistanceSemantics.entries.first { it.storageValue == version.resistanceSemantics },
                                bodyweightCoefficient = version.bodyweightCoefficient,
                                externalLoadCoefficient = version.externalLoadCoefficient,
                                assistanceCoefficient = version.assistanceCoefficient,
                            ),
                            entryBasis = EntryBasis.fromStorage(version.entryBasis),
                            implementCount = version.implementCount,
                            lateralityMode = LateralityMode.entries.first { it.storageValue == version.lateralityMode },
                            romClass = version.romClass,
                            techniqueClass = version.techniqueClass,
                            resistanceCurveClass = version.resistanceCurveClass,
                            movementPattern = version.movementPattern,
                            jointActions = version.jointActionsJson.jsonStrings(),
                            kineticChain = version.kineticChain,
                            contractionType = version.contractionType,
                            gripSupportConstraints = version.gripSupportConstraintsJson.jsonStrings(),
                            recruitment = RecruitmentProfile(
                                id = RecruitmentProfileVersionId(recruitmentVersion.id),
                                version = recruitmentVersion.version,
                                allocations = allocationsByRecruitmentVersion[recruitmentVersion.id].orEmpty().map { allocation ->
                                    val segment = requireNotNull(segmentById[allocation.muscleSegmentId])
                                    val muscleName = muscleNameById[segment.muscleId].orEmpty()
                                    RecruitmentAllocation(
                                        segmentId = MuscleSegmentId(segment.id),
                                        segmentName = if (segment.segmentType == "WHOLE_MUSCLE") muscleName else "$muscleName — ${segment.name}",
                                        role = RecruitmentRole.fromStorage(allocation.role),
                                        weighting = allocation.weighting,
                                        confidence = allocation.confidence,
                                        source = RecruitmentSource(allocation.provenanceType, allocation.provenanceReference),
                                        applicableRom = allocation.applicableRom,
                                        applicableTechnique = allocation.applicableTechnique,
                                        resistanceCurveClass = allocation.resistanceCurveClass,
                                        modelVersion = allocation.modelVersion,
                                    )
                                },
                                createdAt = recruitmentVersion.createdAt,
                                effectiveAt = recruitmentVersion.effectiveAt,
                                supersededAt = recruitmentVersion.supersededAt,
                                provenance = recruitmentVersion.provenance,
                                modelVersion = recruitmentVersion.modelVersion,
                            ),
                            createdAt = version.createdAt,
                            effectiveAt = version.effectiveAt,
                            supersededAt = version.supersededAt,
                            provenance = version.provenance,
                            modelVersion = version.modelVersion,
                        )
                    },
                )
            },
        )
    }

    private suspend fun muscleNames(): Map<String, String> = muscleNameCache ?: database.referenceDao()
        .muscles()
        .associate { it.id to it.name }
        .also { muscleNameCache = it }

    private fun writeSetupPhoto(
        exerciseId: String,
        source: ImageDecoder.Source,
        sortOrder: Int,
    ): ExerciseSetupMediaEntity {
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
        const val MAX_INSTRUCTION_CHARS = 1600
        const val MAX_LINK_CHARS = 2048
    }
}

private fun String?.jsonStrings(): List<String> = this?.let { encoded ->
    JSONArray(encoded).let { array -> List(array.length()) { index -> array.getString(index) } }
}.orEmpty()
