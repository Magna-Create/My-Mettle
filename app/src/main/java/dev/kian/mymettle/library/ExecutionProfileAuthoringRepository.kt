package dev.kian.mymettle.library

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ExecutionProfileVersionEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseExecutionProfileEntity
import dev.kian.mymettle.data.local.entity.PerformanceSchemaEntity
import dev.kian.mymettle.data.local.entity.PerformanceSchemaMetricEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.RecruitmentProfileVersionEntity
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersion
import dev.kian.mymettle.domain.exercise.ExerciseId
import java.time.Instant
import org.json.JSONArray

data class ExecutionProfileAuthoringRequest(
    val exerciseId: ExerciseId,
    val exerciseName: String,
    val profileName: String,
    val isDefault: Boolean,
    val version: ExecutionProfileVersion,
) {
    init {
        require(exerciseName.isNotBlank())
        require(profileName.isNotBlank())
        require(version.executionProfileId.value.isNotBlank())
    }
}

/**
 * Canonical production write path for non-Lite performance profiles.
 *
 * Semantic rows are insert-only. Publishing a successor adds new schema, recruitment and
 * execution-version rows; the only update to the preceding rows is their lifecycle
 * `supersededAt` marker. Historical observations continue to reference the original version ids.
 */
class ExecutionProfileAuthoringRepository(
    private val database: MyMettleDatabase,
) {
    private val dao get() = database.workoutDao()

    suspend fun createProfile(request: ExecutionProfileAuthoringRequest) = database.withTransaction {
        val version = request.version
        if (version.version != 1 || version.supersededAt != null || version.recruitment.version != 1 || version.recruitment.supersededAt != null) {
            throw ExecutionProfileAuthoringException("A new execution profile must begin with current version 1 semantics.")
        }
        if (dao.executionProfile(version.executionProfileId.value) != null) {
            throw ExecutionProfileAuthoringException("Execution profile ${version.executionProfileId.value} already exists.")
        }

        val existingExercise = dao.exercises(listOf(request.exerciseId.value)).singleOrNull()
        val existingProfiles = dao.executionProfiles(listOf(request.exerciseId.value))
        if (existingExercise == null) {
            if (!request.isDefault) {
                throw ExecutionProfileAuthoringException("The first profile for a new exercise must be the default.")
            }
            dao.upsertExercises(
                listOf(
                    ExerciseEntity(
                        id = request.exerciseId.value,
                        name = request.exerciseName,
                        archived = false,
                        essentialCue = null,
                        createdAt = version.createdAt,
                        updatedAt = version.createdAt,
                    ),
                ),
            )
        } else if (existingExercise.name != request.exerciseName) {
            throw ExecutionProfileAuthoringException("Stable exercise identity ${request.exerciseId.value} already has a different name.")
        }
        if (request.isDefault && existingProfiles.any { it.isDefault }) {
            throw ExecutionProfileAuthoringException("Exercise ${request.exerciseId.value} already has a default execution profile.")
        }

        dao.upsertExecutionProfiles(
            listOf(
                ExerciseExecutionProfileEntity(
                    id = version.executionProfileId.value,
                    exerciseId = request.exerciseId.value,
                    name = request.profileName,
                    isDefault = request.isDefault,
                    archived = false,
                ),
            ),
        )
        validateRecruitmentSegments(version)
        insertVersion(version)
    }

    suspend fun publishSuccessor(version: ExecutionProfileVersion) = database.withTransaction {
        val profileId = version.executionProfileId.value
        if (dao.executionProfile(profileId) == null) {
            throw ExecutionProfileAuthoringException("Execution profile $profileId does not exist.")
        }
        val existing = dao.executionProfileVersions(listOf(profileId))
        val current = existing.filter { it.supersededAt == null }.singleOrNull()
            ?: throw ExecutionProfileAuthoringException("Execution profile $profileId must have exactly one current version.")
        if (version.supersededAt != null || version.version != existing.maxOf { it.version } + 1) {
            throw ExecutionProfileAuthoringException("A successor must be the next current execution-profile version.")
        }
        if (Instant.parse(version.effectiveAt) < Instant.parse(current.effectiveAt)) {
            throw ExecutionProfileAuthoringException("A successor cannot become effective before its predecessor.")
        }

        val recruitmentIds = existing.map { it.recruitmentProfileVersionId }.distinct()
        val existingRecruitment = dao.recruitmentProfileVersions(recruitmentIds)
        if (version.recruitment.id.value in recruitmentIds || version.recruitment.supersededAt != null) {
            throw ExecutionProfileAuthoringException("A successor must reference a new current recruitment version.")
        }
        val nextRecruitmentVersion = (existingRecruitment.maxOfOrNull { it.version } ?: 0) + 1
        if (version.recruitment.version != nextRecruitmentVersion) {
            throw ExecutionProfileAuthoringException("Recruitment versions must be contiguous for an execution profile.")
        }

        validateRecruitmentSegments(version)
        insertVersion(version)
        if (dao.supersedeExecutionProfileVersion(current.id, version.effectiveAt) != 1) {
            throw ExecutionProfileAuthoringException("The preceding execution version changed while publishing its successor.")
        }
        val currentRecruitment = existingRecruitment.singleOrNull { it.id == current.recruitmentProfileVersionId }
            ?: throw ExecutionProfileAuthoringException("The current execution version references missing recruitment semantics.")
        if (currentRecruitment.supersededAt == null &&
            dao.supersedeRecruitmentProfileVersion(currentRecruitment.id, version.effectiveAt) != 1
        ) {
            throw ExecutionProfileAuthoringException("The preceding recruitment version changed while publishing its successor.")
        }
    }

    private suspend fun validateRecruitmentSegments(version: ExecutionProfileVersion) {
        val ids = version.recruitment.allocations.map { it.segmentId.value }.distinct()
        if (ids.isEmpty()) return
        val known = database.referenceDao().segments(ids).mapTo(hashSetOf()) { it.id }
        val missing = ids.filterNot { it in known }
        if (missing.isNotEmpty()) {
            throw ExecutionProfileAuthoringException("Recruitment references unknown canonical segments: ${missing.joinToString()}.")
        }
    }

    private suspend fun insertVersion(version: ExecutionProfileVersion) {
        dao.insertPerformanceSchemas(listOf(version.schema.toEntity(version.createdAt)))
        dao.insertPerformanceSchemaMetrics(version.schema.metrics.map { metric ->
            PerformanceSchemaMetricEntity(
                performanceSchemaId = version.schema.id,
                metric = metric.metric.storageValue,
                required = metric.required,
                targetable = metric.targetable,
                defaultUnit = metric.defaultUnit.storageValue,
                minimumCanonical = metric.minimumCanonical,
                maximumCanonical = metric.maximumCanonical,
                incrementCanonical = metric.incrementCanonical,
                allowedCanonicalValuesJson = metric.allowedCanonicalValues.takeIf { it.isNotEmpty() }
                    ?.let { JSONArray(it) }?.toString(),
            )
        })
        dao.insertRecruitmentProfileVersions(
            listOf(
                RecruitmentProfileVersionEntity(
                    id = version.recruitment.id.value,
                    executionProfileId = version.executionProfileId.value,
                    version = version.recruitment.version,
                    createdAt = version.recruitment.createdAt,
                    effectiveAt = version.recruitment.effectiveAt,
                    supersededAt = version.recruitment.supersededAt,
                    provenance = version.recruitment.provenance,
                    modelVersion = version.recruitment.modelVersion,
                ),
            ),
        )
        if (version.recruitment.allocations.isNotEmpty()) {
            dao.insertRecruitmentAllocations(version.recruitment.allocations.map { allocation ->
                RecruitmentAllocationEntity(
                    recruitmentProfileVersionId = version.recruitment.id.value,
                    muscleSegmentId = allocation.segmentId.value,
                    role = allocation.role.storageValue,
                    weighting = allocation.weighting,
                    confidence = allocation.confidence,
                    provenanceType = allocation.source?.provenanceType ?: "unknown",
                    provenanceReference = allocation.source?.reference,
                    applicableRom = allocation.applicableRom,
                    applicableTechnique = allocation.applicableTechnique,
                    resistanceCurveClass = allocation.resistanceCurveClass,
                    modelVersion = allocation.modelVersion,
                )
            })
        }
        dao.insertExecutionProfileVersions(listOf(version.toEntity()))
    }
}

class ExecutionProfileAuthoringException(message: String) : IllegalStateException(message)

private fun dev.kian.mymettle.domain.performance.PerformanceSchema.toEntity(createdAt: String) = PerformanceSchemaEntity(
    id = id,
    version = version,
    metricFamily = family.storageValue,
    createdAt = createdAt,
    provenance = provenance,
)

private fun ExecutionProfileVersion.toEntity() = ExecutionProfileVersionEntity(
    id = id.value,
    executionProfileId = executionProfileId.value,
    version = version,
    metricFamily = metricFamily.storageValue,
    performanceSchemaId = schema.id,
    equipmentIdentity = equipment.identity,
    equipmentType = equipment.type,
    resistanceSemantics = resistanceModel.semantics.storageValue,
    resistanceModelVersion = resistanceModel.modelVersion,
    bodyweightCoefficient = resistanceModel.bodyweightCoefficient,
    externalLoadCoefficient = resistanceModel.externalLoadCoefficient,
    assistanceCoefficient = resistanceModel.assistanceCoefficient,
    entryBasis = entryBasis.storageValue,
    implementCount = implementCount,
    lateralityMode = lateralityMode.storageValue,
    romClass = romClass,
    techniqueClass = techniqueClass,
    resistanceCurveClass = resistanceCurveClass,
    movementPattern = movementPattern,
    jointActionsJson = jointActions.takeIf { it.isNotEmpty() }?.let { JSONArray(it) }?.toString(),
    kineticChain = kineticChain,
    contractionType = contractionType,
    gripSupportConstraintsJson = gripSupportConstraints.takeIf { it.isNotEmpty() }?.let { JSONArray(it) }?.toString(),
    recruitmentProfileVersionId = recruitment.id.value,
    createdAt = createdAt,
    effectiveAt = effectiveAt,
    supersededAt = supersededAt,
    provenance = provenance,
    modelVersion = modelVersion,
)
