package dev.kian.mymettle.inference

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.dao.CompletedSetEvidenceRow
import dev.kian.mymettle.data.local.entity.ExerciseTranslationStateEntity
import dev.kian.mymettle.data.local.entity.InferenceRunEntity
import dev.kian.mymettle.data.local.entity.MuscleStateSnapshotEntity
import dev.kian.mymettle.data.local.entity.StimulusEstimateEntity
import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.RecruitmentRole
import dev.kian.mymettle.domain.inference.BodySide
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.ExerciseTranslationState
import dev.kian.mymettle.domain.inference.InferenceRun
import dev.kian.mymettle.domain.inference.InferenceRunId
import dev.kian.mymettle.domain.inference.RecruitmentEvidence
import dev.kian.mymettle.domain.inference.StimulusEstimate
import dev.kian.mymettle.domain.inference.UserInferenceSnapshot
import dev.kian.mymettle.domain.inference.UserMuscleState
import dev.kian.mymettle.domain.physiology.Estimate
import dev.kian.mymettle.domain.physiology.EstimateSourceKind
import dev.kian.mymettle.domain.physiology.ReferenceProfileId
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
        val evidence = dao.completedSetEvidence().map(CompletedSetEvidenceRow::toDomain)
        val recruitmentByProfile = if (evidence.isEmpty()) {
            emptyMap()
        } else {
            dao.recruitmentAllocations(evidence.map { it.executionProfileId.value }.distinct())
                .groupBy { it.executionProfileId }
        }
        val calculatedAt = clock()
        val runId = InferenceRunId(idFactory())
        val stimuli = evidence.flatMap { set ->
            val recruitment = recruitmentByProfile[set.executionProfileId.value].orEmpty().map { allocation ->
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
            evidenceSetCount = evidence.size,
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
        }

        UserInferenceSnapshot(run, muscleStates, stimuli, translationStates)
    }

    suspend fun latestSnapshot(
        userProfileId: String? = null,
    ): UserInferenceSnapshot? {
        val runEntity = dao.latestInferenceRun(resolveUserProfileId(userProfileId)) ?: return null
        val run = runEntity.toDomain()
        return UserInferenceSnapshot(
            run = run,
            muscleStates = dao.muscleStateSnapshots(runEntity.id).map { it.toDomain() },
            stimulusEstimates = dao.stimulusEstimates(runEntity.id).map { it.toDomain() },
            exerciseTranslationStates = dao.exerciseTranslationStates(runEntity.id).map { it.toDomain() },
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
        const val MODEL_VERSION = "n-bio-4-inference-scaffold-v0"
        const val RECRUITMENT_MODEL_VERSION = "n-bio-2-execution-recruitment-v1"
    }
}

private fun CompletedSetEvidenceRow.toDomain(): CompletedSetEvidence = CompletedSetEvidence(
    setRecordId = setRecordId,
    sessionExerciseId = sessionExerciseId,
    executionProfileId = ExecutionProfileId(executionProfileId),
    completedAt = Instant.parse(completedAt),
    load = load,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMetres = distanceMetres,
    unit = unit,
    rir = rir,
    effortSource = effortSource,
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
    id = "${runId.value}:stimulus:$setRecordId:${segmentId.value}:${side.storageValue}",
    inferenceRunId = runId.value,
    sessionExerciseId = sessionExerciseId,
    setRecordId = setRecordId,
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
        executionProfileId = executionProfileId.value,
        observedLoadAnchor = observedLoadAnchor?.value,
        observedLoadUnit = observedLoadUnit,
        observedLoadUncertainty = observedLoadAnchor?.uncertainty,
        observedRepAnchor = observedRepAnchor?.value,
        observedRepUncertainty = observedRepAnchor?.uncertainty,
        observedDurationSecondsAnchor = observedDurationSecondsAnchor?.value,
        observedDurationUncertainty = observedDurationSecondsAnchor?.uncertainty,
        observedDistanceMetresAnchor = observedDistanceMetresAnchor?.value,
        observedDistanceUncertainty = observedDistanceMetresAnchor?.uncertainty,
        observedRirAnchor = observedRirAnchor,
        observedAnchorSetRecordId = observedLoadAnchor?.sourceId
            ?: observedRepAnchor?.sourceId
            ?: observedDurationSecondsAnchor?.sourceId
            ?: observedDistanceMetresAnchor?.sourceId,
        sampleCount = sampleCount,
        updatedAt = updatedAt.toString(),
        modelVersion = modelVersion,
    )

private fun ExerciseTranslationStateEntity.toDomain(): ExerciseTranslationState = ExerciseTranslationState(
    executionProfileId = ExecutionProfileId(executionProfileId),
    observedLoadAnchor = estimate(
        observedLoadAnchor,
        observedLoadUncertainty,
        observedAnchorSetRecordId,
        modelVersion,
    ),
    observedLoadUnit = observedLoadUnit,
    observedRepAnchor = estimate(
        observedRepAnchor,
        observedRepUncertainty,
        observedAnchorSetRecordId,
        modelVersion,
    ),
    observedDurationSecondsAnchor = estimate(
        observedDurationSecondsAnchor,
        observedDurationUncertainty,
        observedAnchorSetRecordId,
        modelVersion,
    ),
    observedDistanceMetresAnchor = estimate(
        observedDistanceMetresAnchor,
        observedDistanceUncertainty,
        observedAnchorSetRecordId,
        modelVersion,
    ),
    observedRirAnchor = observedRirAnchor,
    sampleCount = sampleCount,
    updatedAt = Instant.parse(updatedAt),
    modelVersion = modelVersion,
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
