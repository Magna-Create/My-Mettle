package dev.kian.mymettle.inference

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.dao.CompletedSetEvidenceRow
import dev.kian.mymettle.data.local.entity.ExerciseTranslationMetricAnchorEntity
import dev.kian.mymettle.data.local.entity.ExerciseTranslationStateEntity
import dev.kian.mymettle.data.local.entity.InferenceRunEntity
import dev.kian.mymettle.data.local.entity.MuscleStateSnapshotEntity
import dev.kian.mymettle.data.local.entity.StimulusEstimateEntity
import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.RecruitmentRole
import dev.kian.mymettle.domain.inference.BodySide
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.ExerciseTranslationState
import dev.kian.mymettle.domain.inference.PerformanceAnchor
import dev.kian.mymettle.domain.inference.InferenceRun
import dev.kian.mymettle.domain.inference.InferenceRunId
import dev.kian.mymettle.domain.inference.RecruitmentEvidence
import dev.kian.mymettle.domain.inference.StimulusEstimate
import dev.kian.mymettle.domain.inference.UserInferenceSnapshot
import dev.kian.mymettle.domain.inference.UserMuscleState
import dev.kian.mymettle.domain.physiology.Estimate
import dev.kian.mymettle.domain.physiology.EstimateSourceKind
import dev.kian.mymettle.domain.physiology.ReferenceProfileId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.ExerciseTranslationModel
import dev.kian.mymettle.engine.inference.MuscleStateUpdateRequest
import dev.kian.mymettle.engine.inference.MuscleStateUpdater
import dev.kian.mymettle.engine.inference.NeutralPriorMuscleStateUpdater
import dev.kian.mymettle.engine.inference.ObservedPerformanceTranslationModel
import dev.kian.mymettle.engine.inference.StimulusEstimator
import dev.kian.mymettle.engine.inference.WeightedWorkingSetStimulusEstimator
import java.time.Instant
import java.util.UUID

class InferenceException(message: String) : IllegalStateException(message)

/**
 * Explicit persistence boundary for derived biological interpretation.
 *
 * Full-history replay is never triggered by ordinary navigation or set entry. Callers must start
 * it as a visible maintenance/background task; every output remains tied to one immutable run and
 * can be discarded and rebuilt without changing raw workout history.
 */
class RoomInferenceRepository(
    private val database: MyMettleDatabase,
    private val stimulusEstimator: StimulusEstimator = WeightedWorkingSetStimulusEstimator(),
    private val muscleStateUpdater: MuscleStateUpdater = NeutralPriorMuscleStateUpdater(),
    private val exerciseTranslationModel: ExerciseTranslationModel = ObservedPerformanceTranslationModel(),
    private val clock: () -> Instant = Instant::now,
    private val idFactory: () -> String = { "inference_run_${UUID.randomUUID()}" },
) {
    private val dao get() = database.inferenceDao()

    suspend fun recomputeFromRawHistory(
        userProfileId: String? = null,
    ): UserInferenceSnapshot = database.withTransaction {
        val resolvedUserProfileId = resolveUserProfileId(userProfileId)
        val referenceProfile = dao.latestReferenceProfile()
            ?: throw InferenceException("Runtime reference profile has not been seeded.")
        val evidenceRows = dao.completedSetEvidence()
        val metricValuesByObservation = if (evidenceRows.isEmpty()) emptyMap() else {
            dao.completedMetricValues(evidenceRows.map { it.observationId })
                .groupBy { it.observationId }
        }
        val evidence = evidenceRows.map { row ->
            row.toDomain(metricValuesByObservation[row.observationId].orEmpty().map { value ->
                PerformanceMetricValue(
                    metric = PerformanceMetric.fromStorage(value.metric),
                    entered = Quantity(value.enteredValue, UnitId.fromStorage(value.enteredUnit)),
                    canonical = Quantity(value.canonicalValue, UnitId.fromStorage(value.canonicalUnit)),
                )
            })
        }
        val recruitmentByProfile = if (evidence.isEmpty()) {
            emptyMap()
        } else {
            dao.recruitmentAllocations(evidence.map { it.executionProfileVersionId.value }.distinct())
                .groupBy { it.executionProfileVersionId }
        }
        val calculatedAt = clock()
        val runId = InferenceRunId(idFactory())
        val stimuli = evidence.flatMap { set ->
            val recruitment = recruitmentByProfile[set.executionProfileVersionId.value].orEmpty().map { allocation ->
                RecruitmentEvidence(
                    segmentId = MuscleSegmentId(allocation.muscleSegmentId),
                    role = RecruitmentRole.fromStorage(allocation.role),
                    weighting = allocation.weighting,
                    confidence = allocation.confidence,
                )
            }
            stimulusEstimator.estimate(set, recruitment)
        }
        val muscleStates = muscleStateUpdater.update(
            MuscleStateUpdateRequest(
                inferenceRunId = runId,
                trackedSegmentIds = dao.independentlyTrackedSegments().map { MuscleSegmentId(it.id) },
                stimuli = stimuli,
                calculatedAt = calculatedAt,
            ),
        )
        val translationStates = exerciseTranslationModel.infer(evidence)
        val run = InferenceRun(
            id = runId,
            userProfileId = resolvedUserProfileId,
            modelVersion = MODEL_VERSION,
            referenceProfileId = ReferenceProfileId(referenceProfile.id),
            referenceProfileVersion = referenceProfile.version,
            referenceModelVersion = referenceProfile.modelVersion,
            recruitmentModelVersion = RECRUITMENT_MODEL_VERSION,
            stimulusModelVersion = stimulusEstimator.modelVersion,
            muscleStateModelVersion = muscleStateUpdater.modelVersion,
            exerciseTranslationModelVersion = exerciseTranslationModel.modelVersion,
            calculatedAt = calculatedAt,
            evidenceThrough = evidence.maxOfOrNull { it.completedAt },
            evidenceSetCount = evidence.map { it.setRecordId }.distinct().size,
        )

        dao.insertInferenceRun(run.toEntity())
        if (stimuli.isNotEmpty()) {
            dao.insertStimulusEstimates(stimuli.map { it.toEntity(runId) })
        }
        if (muscleStates.isNotEmpty()) {
            dao.insertMuscleStateSnapshots(muscleStates.map { it.toEntity(runId) })
        }
        if (translationStates.isNotEmpty()) {
            dao.insertExerciseTranslationStates(translationStates.map { it.toEntity(runId) })
            dao.insertExerciseTranslationMetricAnchors(
                translationStates.flatMap { state -> state.anchors.map { it.toEntity(runId, state.executionProfileVersionId) } },
            )
        }

        UserInferenceSnapshot(run, muscleStates, stimuli, translationStates)
    }

    suspend fun latestSnapshot(
        userProfileId: String? = null,
    ): UserInferenceSnapshot? {
        val runEntity = dao.latestInferenceRun(resolveUserProfileId(userProfileId)) ?: return null
        val run = runEntity.toDomain()
        val translationEntities = dao.exerciseTranslationStates(runEntity.id)
        val anchorsByVersion = dao.exerciseTranslationMetricAnchors(runEntity.id)
            .groupBy { it.executionProfileVersionId }
        return UserInferenceSnapshot(
            run = run,
            muscleStates = dao.muscleStateSnapshots(runEntity.id).map { it.toDomain() },
            stimulusEstimates = dao.stimulusEstimates(runEntity.id).map { it.toDomain() },
            exerciseTranslationStates = translationEntities.map { it.toDomain(anchorsByVersion[it.executionProfileVersionId].orEmpty()) },
        )
    }

    suspend fun discardDerivedStateForRebuild(userProfileId: String? = null) {
        dao.deleteDerivedState(resolveUserProfileId(userProfileId))
    }

    private suspend fun resolveUserProfileId(requestedId: String?): String {
        val profileIds = dao.userProfileIds()
        if (requestedId != null) {
            if (requestedId !in profileIds) {
                throw InferenceException("User profile $requestedId does not exist.")
            }
            return requestedId
        }
        if (profileIds.size != 1) {
            throw InferenceException("Inference requires exactly one user profile or an explicit profile id.")
        }
        return profileIds.single()
    }

    companion object {
        const val MODEL_VERSION = "n-bio-6-generic-evidence-adapter-v1"
        const val RECRUITMENT_MODEL_VERSION = "n-bio-6-versioned-execution-recruitment-v1"
    }
}

private fun CompletedSetEvidenceRow.toDomain(values: List<PerformanceMetricValue>): CompletedSetEvidence = CompletedSetEvidence(
    setRecordId = setRecordId,
    observationId = observationId,
    sessionExerciseId = sessionExerciseId,
    executionProfileVersionId = ExecutionProfileVersionId(executionProfileVersionId),
    laterality = Laterality.fromStorage(side),
    completedAt = Instant.parse(completedAt),
    metricValues = values,
    bodyMassContextKg = observationBodyMassContextKg ?: sessionBodyMassSnapshotKg,
    warmUp = warmUp,
    kind = kind,
)

private fun InferenceRun.toEntity(): InferenceRunEntity = InferenceRunEntity(
    id = id.value,
    userProfileId = userProfileId,
    modelVersion = modelVersion,
    referenceProfileId = referenceProfileId.value,
    referenceProfileVersion = referenceProfileVersion,
    referenceModelVersion = referenceModelVersion,
    recruitmentModelVersion = recruitmentModelVersion,
    stimulusModelVersion = stimulusModelVersion,
    muscleStateModelVersion = muscleStateModelVersion,
    exerciseTranslationModelVersion = exerciseTranslationModelVersion,
    calculatedAt = calculatedAt.toString(),
    evidenceThrough = evidenceThrough?.toString(),
    evidenceSetCount = evidenceSetCount,
)

private fun InferenceRunEntity.toDomain(): InferenceRun = InferenceRun(
    id = InferenceRunId(id),
    userProfileId = userProfileId,
    modelVersion = modelVersion,
    referenceProfileId = ReferenceProfileId(referenceProfileId),
    referenceProfileVersion = referenceProfileVersion,
    referenceModelVersion = referenceModelVersion,
    recruitmentModelVersion = recruitmentModelVersion,
    stimulusModelVersion = stimulusModelVersion,
    muscleStateModelVersion = muscleStateModelVersion,
    exerciseTranslationModelVersion = exerciseTranslationModelVersion,
    calculatedAt = Instant.parse(calculatedAt),
    evidenceThrough = evidenceThrough?.let(Instant::parse),
    evidenceSetCount = evidenceSetCount,
)

private fun StimulusEstimate.toEntity(runId: InferenceRunId): StimulusEstimateEntity = StimulusEstimateEntity(
    id = "${runId.value}:stimulus:$observationId:${segmentId.value}:${side.storageValue}",
    inferenceRunId = runId.value,
    sessionExerciseId = sessionExerciseId,
    setRecordId = setRecordId,
    setObservationId = observationId,
    muscleSegmentId = segmentId.value,
    side = side.storageValue,
    role = role.storageValue,
    recruitmentWeighting = recruitmentWeighting,
    estimatedStimulus = estimatedStimulus,
    confidence = confidence,
    modelVersion = modelVersion,
)

private fun StimulusEstimateEntity.toDomain(): StimulusEstimate = StimulusEstimate(
    setRecordId = setRecordId,
    observationId = setObservationId,
    sessionExerciseId = sessionExerciseId,
    segmentId = MuscleSegmentId(muscleSegmentId),
    side = BodySide.fromStorage(side),
    role = RecruitmentRole.fromStorage(role),
    recruitmentWeighting = recruitmentWeighting,
    estimatedStimulus = estimatedStimulus,
    confidence = confidence,
    modelVersion = modelVersion,
)

private fun UserMuscleState.toEntity(runId: InferenceRunId): MuscleStateSnapshotEntity =
    MuscleStateSnapshotEntity(
        inferenceRunId = runId.value,
        muscleSegmentId = segmentId.value,
        side = side.storageValue,
        developmentIndex = developmentIndex.value,
        developmentUncertainty = developmentIndex.uncertainty,
        volumeScale = volumeScale?.value,
        volumeScaleUncertainty = volumeScale?.uncertainty,
        structuralCapacityScale = structuralCapacityScale?.value,
        structuralCapacityScaleUncertainty = structuralCapacityScale?.uncertainty,
        recentStimulus = recentStimulus?.value,
        recentStimulusUncertainty = recentStimulus?.uncertainty,
        recovery = recovery?.value,
        recoveryUncertainty = recovery?.uncertainty,
        evidenceCount = evidenceCount,
        updatedAt = updatedAt.toString(),
        inferenceModelVersion = inferenceModelVersion,
    )

private fun MuscleStateSnapshotEntity.toDomain(): UserMuscleState = UserMuscleState(
    segmentId = MuscleSegmentId(muscleSegmentId),
    side = BodySide.fromStorage(side),
    developmentIndex = Estimate(
        value = developmentIndex,
        uncertainty = developmentUncertainty,
        sourceKind = EstimateSourceKind.MODEL_DERIVED,
        sourceId = inferenceRunId,
        modelVersion = inferenceModelVersion,
    ),
    volumeScale = estimate(volumeScale, volumeScaleUncertainty, inferenceRunId, inferenceModelVersion),
    structuralCapacityScale = estimate(
        structuralCapacityScale,
        structuralCapacityScaleUncertainty,
        inferenceRunId,
        inferenceModelVersion,
    ),
    recentStimulus = estimate(recentStimulus, recentStimulusUncertainty, inferenceRunId, inferenceModelVersion),
    recovery = estimate(recovery, recoveryUncertainty, inferenceRunId, inferenceModelVersion),
    evidenceCount = evidenceCount,
    updatedAt = Instant.parse(updatedAt),
    inferenceModelVersion = inferenceModelVersion,
)

private fun ExerciseTranslationState.toEntity(runId: InferenceRunId): ExerciseTranslationStateEntity =
    ExerciseTranslationStateEntity(
        inferenceRunId = runId.value,
        executionProfileVersionId = executionProfileVersionId.value,
        sampleCount = sampleCount,
        updatedAt = updatedAt.toString(),
        modelVersion = modelVersion,
    )

private fun ExerciseTranslationStateEntity.toDomain(
    anchors: List<ExerciseTranslationMetricAnchorEntity>,
): ExerciseTranslationState = ExerciseTranslationState(
    executionProfileVersionId = ExecutionProfileVersionId(executionProfileVersionId),
    anchors = anchors.map { anchor ->
        PerformanceAnchor(
            metric = PerformanceMetric.fromStorage(anchor.metric),
            estimate = requireNotNull(estimate(anchor.canonicalValue, anchor.uncertainty, anchor.sourceObservationId, modelVersion)),
            canonicalUnit = anchor.canonicalUnit,
            sourceObservationId = anchor.sourceObservationId,
            sourceSetRecordId = anchor.sourceSetRecordId,
        )
    },
    sampleCount = sampleCount,
    updatedAt = Instant.parse(updatedAt),
    modelVersion = modelVersion,
)

private fun PerformanceAnchor.toEntity(
    runId: InferenceRunId,
    profileVersionId: ExecutionProfileVersionId,
): ExerciseTranslationMetricAnchorEntity = ExerciseTranslationMetricAnchorEntity(
    inferenceRunId = runId.value,
    executionProfileVersionId = profileVersionId.value,
    metric = metric.storageValue,
    canonicalValue = estimate.value,
    canonicalUnit = canonicalUnit,
    uncertainty = estimate.uncertainty,
    sourceObservationId = sourceObservationId,
    sourceSetRecordId = sourceSetRecordId,
)

private fun estimate(
    value: Double?,
    uncertainty: Double?,
    sourceId: String?,
    modelVersion: String,
): Estimate<Double>? = value?.let {
    Estimate(
        value = it,
        uncertainty = uncertainty,
        sourceKind = EstimateSourceKind.MODEL_DERIVED,
        sourceId = sourceId,
        modelVersion = modelVersion,
    )
}
