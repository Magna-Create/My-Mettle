package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.inference.BodySide
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.ExerciseTranslationState
import dev.kian.mymettle.domain.inference.InferenceRunId
import dev.kian.mymettle.domain.inference.RecruitmentEvidence
import dev.kian.mymettle.domain.inference.StimulusEstimate
import dev.kian.mymettle.domain.inference.UserMuscleState
import dev.kian.mymettle.domain.physiology.Estimate
import dev.kian.mymettle.domain.physiology.EstimateSourceKind
import java.time.Instant

interface StimulusEstimator {
    val modelVersion: String

    fun estimate(
        set: CompletedSetEvidence,
        recruitment: List<RecruitmentEvidence>,
    ): List<StimulusEstimate>
}

/**
 * A deliberately non-biological v0 evidence projection.
 *
 * One completed working set contributes recruitment-weighted set units. This scaffold deliberately
 * has no subjective effort modifier or claimed hypertrophy equation. A later estimator can
 * reinterpret the same immutable set history.
 */
class WeightedWorkingSetStimulusEstimator : StimulusEstimator {
    override val modelVersion: String = MODEL_VERSION

    override fun estimate(
        set: CompletedSetEvidence,
        recruitment: List<RecruitmentEvidence>,
    ): List<StimulusEstimate> {
        if (set.warmUp || !set.hasPerformedWork) return emptyList()

        return recruitment
            .filter { it.weighting > 0.0 }
            .map { allocation ->
                StimulusEstimate(
                    setRecordId = set.setRecordId,
                    sessionExerciseId = set.sessionExerciseId,
                    segmentId = allocation.segmentId,
                    side = BodySide.BILATERAL,
                    role = allocation.role,
                    recruitmentWeighting = allocation.weighting,
                    estimatedStimulus = allocation.weighting,
                    confidence = (allocation.confidence * WORKING_SET_CONFIDENCE).coerceIn(0.0, 1.0),
                    modelVersion = modelVersion,
                )
            }
    }

    companion object {
        const val MODEL_VERSION = "n-bio-4-weighted-working-set-v0"
        private const val WORKING_SET_CONFIDENCE = 0.40
    }
}

data class MuscleStateUpdateRequest(
    val inferenceRunId: InferenceRunId,
    val trackedSegmentIds: List<MuscleSegmentId>,
    val stimuli: List<StimulusEstimate>,
    val calculatedAt: Instant,
)

interface MuscleStateUpdater {
    val modelVersion: String

    fun update(request: MuscleStateUpdateRequest): List<UserMuscleState>
}

/**
 * Seeds a neutral development baseline and records only the evidence count N-BIO-4 can defend
 * today. Volume, structural-capacity, recency and recovery estimates remain null until their
 * models exist; all-time stimulus is not mislabeled as recent stimulus.
 */
class NeutralPriorMuscleStateUpdater : MuscleStateUpdater {
    override val modelVersion: String = MODEL_VERSION

    override fun update(request: MuscleStateUpdateRequest): List<UserMuscleState> {
        val stimuliBySegment = request.stimuli.groupBy { it.segmentId }
        return request.trackedSegmentIds.distinct().map { segmentId ->
            val evidence = stimuliBySegment[segmentId].orEmpty()
            val evidenceCount = evidence.map { it.setRecordId }.distinct().size
            UserMuscleState(
                segmentId = segmentId,
                side = BodySide.BILATERAL,
                developmentIndex = Estimate(
                    value = 1.0,
                    uncertainty = 1.0,
                    sourceKind = EstimateSourceKind.MODEL_DERIVED,
                    sourceId = request.inferenceRunId.value,
                    modelVersion = modelVersion,
                ),
                volumeScale = null,
                structuralCapacityScale = null,
                recentStimulus = null,
                recovery = null,
                evidenceCount = evidenceCount,
                updatedAt = request.calculatedAt,
                inferenceModelVersion = modelVersion,
            )
        }
    }

    companion object {
        const val MODEL_VERSION = "n-bio-4-neutral-muscle-state-v0"
    }
}

interface ExerciseTranslationModel {
    val modelVersion: String

    fun infer(evidence: List<CompletedSetEvidence>): List<ExerciseTranslationState>
}

/**
 * Stores a same-profile observed performance anchor. It intentionally does not claim transfer
 * between exercises: cross-profile translation remains an independently replaceable later model.
 */
class ObservedPerformanceTranslationModel : ExerciseTranslationModel {
    override val modelVersion: String = MODEL_VERSION

    override fun infer(evidence: List<CompletedSetEvidence>): List<ExerciseTranslationState> = evidence
        .filter { !it.warmUp && it.hasPerformedWork }
        .groupBy { it.executionProfileId }
        .map { (profileId, samples) ->
            val latest = samples.maxWith(compareBy<CompletedSetEvidence> { it.completedAt }.thenBy { it.setRecordId })
            val normalisedUncertainty = 1.0
            ExerciseTranslationState(
                executionProfileId = profileId,
                observedLoadAnchor = latest.load?.let { value ->
                    estimate(value, normalisedUncertainty, latest.setRecordId)
                },
                observedLoadUnit = latest.load?.let { latest.unit },
                observedRepAnchor = latest.reps?.toDouble()?.let { value ->
                    estimate(value, normalisedUncertainty, latest.setRecordId)
                },
                observedDurationSecondsAnchor = latest.durationSeconds?.toDouble()?.let { value ->
                    estimate(value, normalisedUncertainty, latest.setRecordId)
                },
                observedDistanceMetresAnchor = latest.distanceMetres?.let { value ->
                    estimate(value, normalisedUncertainty, latest.setRecordId)
                },
                sampleCount = samples.map { it.setRecordId }.distinct().size,
                updatedAt = latest.completedAt,
                modelVersion = modelVersion,
            )
        }
        .sortedBy { it.executionProfileId.value }

    private fun estimate(value: Double, uncertainty: Double, sourceId: String): Estimate<Double> = Estimate(
        value = value,
        uncertainty = uncertainty,
        sourceKind = EstimateSourceKind.MODEL_DERIVED,
        sourceId = sourceId,
        modelVersion = modelVersion,
    )

    companion object {
        const val MODEL_VERSION = "n-bio-4-observed-performance-anchor-v0"
    }
}
