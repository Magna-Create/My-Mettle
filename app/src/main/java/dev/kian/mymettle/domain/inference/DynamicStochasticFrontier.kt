package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ln

/** Behaviour-driving one-sided slack family. 7B.2 v1 deliberately supports one replaceable choice. */
enum class DynamicSlackDistribution(val storageValue: String) {
    HALF_NORMAL("half_normal"),
}

/** Symmetric ordinary performance/day noise family. */
enum class DynamicPerformanceNoiseDistribution(val storageValue: String) {
    STUDENT_T("student_t"),
}

enum class DynamicWithinSessionPolicy(val storageValue: String) {
    EQUAL_TOTAL_WEIGHT_PER_SESSION_V1("equal_total_weight_per_session_v1"),
}

enum class DynamicCurrentCapabilityPolicy(val storageValue: String) {
    RECENT_INDEPENDENT_SESSION_WINDOW_V1("recent_independent_session_window_v1"),
}

enum class DynamicPosteriorApproximation(val storageValue: String) {
    DETERMINISTIC_TENSOR_GRID_WITH_SLACK_QUADRATURE_V1("deterministic_tensor_grid_with_slack_quadrature_v1"),
}

enum class DynamicParameterIdentification(val storageValue: String) {
    FIXED_BY_CONFIG("fixed_by_config"),
    PRIOR_DOMINATED("prior_dominated"),
    PARTIALLY_LEARNED("partially_learned"),
    DATA_INFORMED("data_informed"),
}

enum class DynamicCapabilityFitWarning(val storageValue: String) {
    TEMPORAL_WINDOW_TRUNCATED("temporal_window_truncated"),
    SLOPE_PRIOR_DOMINATED("slope_prior_dominated"),
    NUISANCE_SCALES_FIXED("nuisance_scales_fixed"),
    APPROXIMATE_POSTERIOR("approximate_posterior"),
}

enum class DynamicCapabilityFitFailureReason(val storageValue: String) {
    NO_ELIGIBLE_EVIDENCE("no_eligible_evidence"),
    MODEL_CONFIG_MISMATCH("model_config_mismatch"),
    EVIDENCE_POLICY_MISMATCH("evidence_policy_mismatch"),
    NUMERICAL_BUDGET_EXCEEDED("numerical_budget_exceeded"),
    NON_FINITE_POSTERIOR("non_finite_posterior"),
    DEGENERATE_POSTERIOR("degenerate_posterior"),
}

class DynamicCapabilityFitException(
    val reason: DynamicCapabilityFitFailureReason,
    message: String,
) : IllegalStateException(message)

/**
 * Exact behaviour-driving 7B.2 v1 configuration. Every numeric model choice and every numerical
 * approximation choice that can alter an answer participates in ModelConfigDefinition identity.
 *
 * The priors below are modelling assumptions, not physiology. The frontier location uses a broad
 * log-uniform prior between [frontierPriorMinimumKg, frontierPriorMaximumKg]; observed data only
 * chooses the numerical grid window inside that proper prior and therefore is not reused as a
 * narrow empirical capability prior.
 */
data class DynamicStochasticFrontierConfig(
    val semanticVersion: String = "n-bio-7b2-half-normal-student-t-frontier-v1",
    val evidencePolicyIdentity: String = DynamicResistanceV1Contract.evidencePolicy.identity,
    val resistanceResolverIdentity: String = DynamicResistanceV1Contract.RESISTANCE_RESOLVER_VERSION,
    val referenceRepPolicy: DynamicResistanceReferenceRepPolicy = DynamicResistanceReferenceRepPolicy.MEDIAN_OBSERVED_LOWER_V1,
    val slackDistribution: DynamicSlackDistribution = DynamicSlackDistribution.HALF_NORMAL,
    val noiseDistribution: DynamicPerformanceNoiseDistribution = DynamicPerformanceNoiseDistribution.STUDENT_T,
    val studentTDegreesOfFreedom: Double = 5.0,
    val slopePriorMedian: Double = 0.16,
    val slopePriorLogSd: Double = 0.55,
    val slackScalePriorMedian: Double = 0.12,
    val slackScalePriorLogSd: Double = 0.55,
    val noiseScalePriorMedian: Double = 0.05,
    val noiseScalePriorLogSd: Double = 0.45,
    val frontierPriorMinimumKg: Double = 0.1,
    val frontierPriorMaximumKg: Double = 5_000.0,
    val nuisanceLearningMinimumIndependentSessions: Int = 8,
    val nuisanceLearningMinimumObservations: Int = 12,
    val nuisanceDataInformedMinimumIndependentSessions: Int = 10,
    val nuisanceDataInformedMinimumObservations: Int = 20,
    val slopePartialMinimumIndependentSessions: Int = 3,
    val slopeDataInformedMinimumIndependentSessions: Int = 6,
    val slopePartialMinimumLogRepSpan: Double = ln(1.5),
    val slopeDataInformedMinimumLogRepSpan: Double = ln(2.0),
    val withinSessionPolicy: DynamicWithinSessionPolicy = DynamicWithinSessionPolicy.EQUAL_TOTAL_WEIGHT_PER_SESSION_V1,
    val currentCapabilityPolicy: DynamicCurrentCapabilityPolicy = DynamicCurrentCapabilityPolicy.RECENT_INDEPENDENT_SESSION_WINDOW_V1,
    val recentIndependentSessionWindow: Int = 12,
    val approximation: DynamicPosteriorApproximation = DynamicPosteriorApproximation.DETERMINISTIC_TENSOR_GRID_WITH_SLACK_QUADRATURE_V1,
    val frontierGridPoints: Int = 31,
    val slopeGridPoints: Int = 15,
    val nuisanceScaleGridPoints: Int = 3,
    val slopePriorGridLogSdRadius: Double = 2.75,
    val nuisanceGridLogSdRadius: Double = 1.0,
    val frontierGridLowerMarginLog: Double = 0.40,
    val frontierGridUpperMarginLog: Double = 0.80,
    val slackQuadraturePoints: Int = 16,
    val slackQuadratureMaximumSd: Double = 6.0,
    val slackPosteriorTopNodeCount: Int = 96,
    val extrapolationLogSdPerUnitOutsideDomain: Double = 0.18,
    val maximumGridEvaluations: Int = 100_000,
    val numericalMinimumResistanceKg: Double = 1e-9,
    val numericalMaximumResistanceKg: Double = 1e9,
    val contextConsumption: String = "NONE:${DynamicResistanceV1Contract.contextPolicy.identity}",
) {
    init {
        require(semanticVersion.isNotBlank())
        require(evidencePolicyIdentity.isNotBlank() && resistanceResolverIdentity.isNotBlank())
        require(studentTDegreesOfFreedom > 2.0 && studentTDegreesOfFreedom.isFinite()) {
            "Student-t degrees of freedom must be finite and > 2 so variance exists."
        }
        require(slopePriorMedian > 0.0 && slopePriorLogSd > 0.0)
        require(slackScalePriorMedian > 0.0 && slackScalePriorLogSd > 0.0)
        require(noiseScalePriorMedian > 0.0 && noiseScalePriorLogSd > 0.0)
        require(frontierPriorMinimumKg > 0.0 && frontierPriorMaximumKg > frontierPriorMinimumKg)
        require(nuisanceLearningMinimumIndependentSessions > 0)
        require(nuisanceLearningMinimumObservations >= nuisanceLearningMinimumIndependentSessions)
        require(nuisanceDataInformedMinimumIndependentSessions >= nuisanceLearningMinimumIndependentSessions)
        require(nuisanceDataInformedMinimumObservations >= nuisanceLearningMinimumObservations)
        require(slopePartialMinimumIndependentSessions > 0)
        require(slopeDataInformedMinimumIndependentSessions >= slopePartialMinimumIndependentSessions)
        require(slopePartialMinimumLogRepSpan > 0.0)
        require(slopeDataInformedMinimumLogRepSpan >= slopePartialMinimumLogRepSpan)
        require(recentIndependentSessionWindow > 0)
        require(frontierGridPoints >= 9 && frontierGridPoints % 2 == 1)
        require(slopeGridPoints >= 7 && slopeGridPoints % 2 == 1)
        require(nuisanceScaleGridPoints >= 3 && nuisanceScaleGridPoints % 2 == 1)
        require(slopePriorGridLogSdRadius > 0.0 && nuisanceGridLogSdRadius > 0.0)
        require(frontierGridLowerMarginLog > 0.0 && frontierGridUpperMarginLog > 0.0)
        require(slackQuadraturePoints >= 8)
        require(slackQuadratureMaximumSd >= 4.0)
        require(slackPosteriorTopNodeCount > 0)
        require(extrapolationLogSdPerUnitOutsideDomain >= 0.0)
        require(maximumGridEvaluations > 0)
        require(numericalMinimumResistanceKg > 0.0 && numericalMaximumResistanceKg > numericalMinimumResistanceKg)
        require(contextConsumption.startsWith("NONE:")) { "7B.2 v1 consumes no context." }
    }

    fun toModelConfig(createdAt: Instant): ModelConfigDefinition = ModelConfigDefinition.create(
        component = InferenceModelComponent.DYNAMIC_CAPABILITY,
        modelFamily = "stochastic_frontier",
        modelName = "half_normal_slack_student_t_noise_tensor_grid",
        semanticVersion = semanticVersion,
        configSchemaVersion = 1,
        parameters = mapOf(
            "capabilityStateSemantics" to DynamicResistanceV1Contract.CAPABILITY_STATE_SEMANTICS,
            "evidencePolicyIdentity" to evidencePolicyIdentity,
            "resistanceResolverIdentity" to resistanceResolverIdentity,
            "referenceRepPolicy" to referenceRepPolicy.storageValue,
            "slackDistribution" to slackDistribution.storageValue,
            "noiseDistribution" to noiseDistribution.storageValue,
            "studentTDegreesOfFreedom" to studentTDegreesOfFreedom.toString(),
            "slopePrior" to "lognormal(median=$slopePriorMedian,logSd=$slopePriorLogSd)",
            "frontierPrior" to "log_uniform_kg(min=$frontierPriorMinimumKg,max=$frontierPriorMaximumKg)",
            "slackScalePrior" to "lognormal(median=$slackScalePriorMedian,logSd=$slackScalePriorLogSd)",
            "noiseScalePrior" to "lognormal(median=$noiseScalePriorMedian,logSd=$noiseScalePriorLogSd)",
            "nuisanceLearningUnlock" to "sessions>=$nuisanceLearningMinimumIndependentSessions,observations>=$nuisanceLearningMinimumObservations",
            "nuisanceDataInformed" to "sessions>=$nuisanceDataInformedMinimumIndependentSessions,observations>=$nuisanceDataInformedMinimumObservations",
            "slopePartialLearning" to "sessions>=$slopePartialMinimumIndependentSessions,logRepSpan>=$slopePartialMinimumLogRepSpan",
            "slopeDataInformed" to "sessions>=$slopeDataInformedMinimumIndependentSessions,logRepSpan>=$slopeDataInformedMinimumLogRepSpan",
            "withinSessionPolicy" to withinSessionPolicy.storageValue,
            "currentCapabilityPolicy" to currentCapabilityPolicy.storageValue,
            "recentIndependentSessionWindow" to recentIndependentSessionWindow.toString(),
            "approximation" to approximation.storageValue,
            "frontierGridPoints" to frontierGridPoints.toString(),
            "slopeGridPoints" to slopeGridPoints.toString(),
            "nuisanceScaleGridPoints" to nuisanceScaleGridPoints.toString(),
            "slopePriorGridLogSdRadius" to slopePriorGridLogSdRadius.toString(),
            "nuisanceGridLogSdRadius" to nuisanceGridLogSdRadius.toString(),
            "frontierGridLowerMarginLog" to frontierGridLowerMarginLog.toString(),
            "frontierGridUpperMarginLog" to frontierGridUpperMarginLog.toString(),
            "slackQuadraturePoints" to slackQuadraturePoints.toString(),
            "slackQuadratureMaximumSd" to slackQuadratureMaximumSd.toString(),
            "slackPosteriorTopNodeCount" to slackPosteriorTopNodeCount.toString(),
            "extrapolationLogSdPerUnitOutsideDomain" to extrapolationLogSdPerUnitOutsideDomain.toString(),
            "maximumGridEvaluations" to maximumGridEvaluations.toString(),
            "numericalResistanceDomainKg" to "$numericalMinimumResistanceKg..$numericalMaximumResistanceKg",
            "successfulSetSemantics" to DynamicResistanceSuccessfulSetSemantics.LOWER_BOUND_DEMONSTRATION.storageValue,
            "contextConsumption" to contextConsumption,
        ),
        createdAt = createdAt,
    )
}

object DynamicStochasticFrontierV1 {
    val config: DynamicStochasticFrontierConfig = DynamicStochasticFrontierConfig()
    const val MODEL_VERSION = "n-bio-7b2-half-normal-student-t-frontier-v1"
    const val APPROXIMATION_VERSION = "tensor-grid-midpoint-slack-quadrature-v1"
}

data class DynamicFrontierParameterPosterior(
    val summary: PosteriorSummary,
    val identification: DynamicParameterIdentification,
    val semanticUnit: String,
) {
    init { require(semanticUnit.isNotBlank()) }
}

data class DynamicSlackPosteriorMass(
    val slack: Double,
    val probability: Double,
) {
    init {
        require(slack.isFinite() && slack >= 0.0)
        require(probability.isFinite() && probability >= 0.0)
    }
}

data class DynamicObservationSlackPosterior(
    val observationId: String,
    val summary: PosteriorSummary,
    val identification: DynamicParameterIdentification,
    val massPoints: List<DynamicSlackPosteriorMass>,
    val semanticDefinition: String = "distance below estimated frontier on the model log-performance scale",
) {
    init {
        require(observationId.isNotBlank())
        require(summary.p05 >= 0.0)
        require(massPoints.isNotEmpty())
        require(abs(massPoints.sumOf { it.probability } - 1.0) <= 1e-8)
        require(!semanticDefinition.contains("rir", ignoreCase = true))
    }

    fun probabilityAtMost(deltaLogPerformance: Double): Double {
        require(deltaLogPerformance.isFinite() && deltaLogPerformance >= 0.0)
        return massPoints.filter { it.slack <= deltaLogPerformance }.sumOf { it.probability }.coerceIn(0.0, 1.0)
    }
}

/** One deterministic posterior-integration node retained for exact replay/prediction within v1. */
data class DynamicFrontierPosteriorNode(
    val logFrontierAtReference: Double,
    val slope: Double,
    val slackScale: Double,
    val noiseScale: Double,
    val posteriorWeight: Double,
) {
    init {
        require(logFrontierAtReference.isFinite())
        require(slope.isFinite() && slope > 0.0)
        require(slackScale.isFinite() && slackScale > 0.0)
        require(noiseScale.isFinite() && noiseScale > 0.0)
        require(posteriorWeight.isFinite() && posteriorWeight >= 0.0)
    }
}

/**
 * Real 7B.2 in-memory stochastic-frontier result. Normal Room persistence is intentionally deferred
 * to 7B.3; this typed structure is the source for later CapabilityState/CapabilityParameterState mapping.
 */
data class DynamicStochasticFrontierFit(
    override val executionProfileVersionId: ExecutionProfileVersionId,
    override val side: Laterality,
    override val inferenceHorizon: Instant,
    override val referenceRepetitions: Double,
    override val modelConfigId: ModelConfigId,
    val modelVersion: String,
    val evidencePolicyIdentity: String,
    val support: EvidenceSupport,
    val observedRepMin: Int,
    val observedRepMax: Int,
    val observedResistanceMinKg: Double,
    val observedResistanceMaxKg: Double,
    val frontierAtReference: PosteriorEstimate,
    val slope: DynamicFrontierParameterPosterior,
    val slackScale: DynamicFrontierParameterPosterior,
    val noiseScale: DynamicFrontierParameterPosterior,
    val observationSlack: List<DynamicObservationSlackPosterior>,
    val selectedObservationIds: List<String>,
    val selectedSessionIds: List<String>,
    val approximationVersion: String,
    val warnings: Set<DynamicCapabilityFitWarning>,
    val posteriorNodes: List<DynamicFrontierPosteriorNode>,
) : DynamicCapabilityFit {
    init {
        require(referenceRepetitions.isFinite() && referenceRepetitions > 0.0)
        require(modelVersion.isNotBlank() && evidencePolicyIdentity.isNotBlank() && approximationVersion.isNotBlank())
        require(support.observationCount > 0 && support.effectiveIndependentSessionCount > 0)
        require(observedRepMin > 0 && observedRepMax >= observedRepMin)
        require(observedResistanceMinKg > 0.0 && observedResistanceMaxKg >= observedResistanceMinKg)
        require(frontierAtReference.summary != null)
        require(frontierAtReference.provenance.modelConfigId == modelConfigId)
        require(frontierAtReference.summary.p05 > 0.0)
        require(selectedObservationIds.size == support.observationCount)
        require(selectedObservationIds.distinct().size == selectedObservationIds.size)
        require(selectedSessionIds.distinct().size == support.effectiveIndependentSessionCount)
        require(observationSlack.map { it.observationId }.toSet() == selectedObservationIds.toSet())
        require(posteriorNodes.isNotEmpty())
        val weightSum = posteriorNodes.sumOf { it.posteriorWeight }
        require(weightSum.isFinite() && abs(weightSum - 1.0) <= 1e-8)
    }
}
