package dev.kian.mymettle.engine.prescription

import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.exercise.LoadResolution
import dev.kian.mymettle.domain.training.ExercisePrescription
import dev.kian.mymettle.domain.training.TrainingTargetId
import kotlin.math.abs
import kotlin.math.round

data class PrescriptionRequest(
    val exerciseId: ExerciseId,
    val executionProfileId: ExecutionProfileId,
    val targetIds: List<TrainingTargetId>,
    val sets: Int,
    val repRange: IntRange,
    val targetRir: Double?,
    val previousPerformedLoad: Double?,
    val permitsExternalLoad: Boolean,
    val loadResolution: LoadResolution?,
    val restSeconds: Int,
)

interface PrescriptionEngine {
    val modelVersion: String

    fun generate(request: PrescriptionRequest): ExercisePrescription
}

/**
 * N-BIO-5 baseline prescription implementation.
 *
 * The programme resolver supplies N-BIO-4's same-profile observed load anchor when it exists, with
 * raw latest performance as a compatibility fallback. This engine still performs no invented
 * progression: it conforms that evidence to the execution profile's physical load resolution and
 * leaves load null when no defensible anchor exists.
 */
class HistoryBackedPrescriptionEngine : PrescriptionEngine {
    override val modelVersion: String = MODEL_VERSION

    override fun generate(request: PrescriptionRequest): ExercisePrescription = ExercisePrescription(
        exerciseId = request.exerciseId,
        executionProfileId = request.executionProfileId,
        targetIds = request.targetIds.distinct(),
        sets = request.sets,
        repRange = request.repRange,
        targetRir = request.targetRir,
        prescribedLoad = if (request.permitsExternalLoad) {
            request.previousPerformedLoad?.let { request.loadResolution.conform(it) }
        } else {
            null
        },
        restSeconds = request.restSeconds,
        generatedByModelVersion = modelVersion,
    )

    private fun LoadResolution?.conform(value: Double): Double {
        if (this == null) return value.coerceAtLeast(0.0)

        val constrained = value
            .coerceAtLeast(minimumLoad ?: 0.0)
            .let { candidate -> maximumLoad?.let { maximum -> minOf(candidate, maximum) } ?: candidate }

        if (allowedValues.isNotEmpty()) {
            return allowedValues.minWith(compareBy<Double> { abs(it - constrained) }.thenBy { it })
        }

        val step = increment?.takeIf { it > 0.0 } ?: return constrained
        val origin = minimumLoad ?: 0.0
        val snapped = origin + round((constrained - origin) / step) * step
        return snapped
            .coerceAtLeast(minimumLoad ?: 0.0)
            .let { candidate -> maximumLoad?.let { maximum -> minOf(candidate, maximum) } ?: candidate }
    }

    companion object {
        const val MODEL_VERSION = "n-bio-5-observed-profile-prescription-v0"
    }
}
