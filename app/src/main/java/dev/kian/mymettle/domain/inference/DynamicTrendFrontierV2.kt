package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import java.time.Instant
import kotlin.math.abs

/**
 * N-BIO-7B development Candidate v2.
 *
 * This is deliberately NOT physiology. The added parameter [frontierTrend] is only the statistical
 * trajectory of the execution-profile frontier per independent-session ordinal under this model.
 * It is not strength growth, recovery, adaptation, fatigue, detraining, or Development state.
 *
 * Observation model inherited unchanged from the frozen/rejected Candidate v1:
 *
 *   y_s = c_0 + g*z_s - b*x_s - u_s + epsilon_s
 *   u_s ~ HalfNormal(sigma_u)
 *   epsilon_s ~ StudentT(df=5, 0, sigma_e)
 *
 * where the latest selected training session has z=0, older selected sessions are -1,-2,..., and
 * the next independent-session forecast is evaluated at z=+1. All Candidate-v1 slope, slack/noise,
 * session weighting, evidence, temporal-window, and rep-extrapolation assumptions are inherited.
 */
data class DynamicTrendFrontierConfig(
    val semanticVersion: String = "n-bio-7b2-half-normal-student-t-session-trend-frontier-v2",
    val baseConfig: DynamicStochasticFrontierConfig = DynamicStochasticFrontierEvidenceV2.config,
    val trendCoordinateVersion: String = "latest-selected-session-zero-older-negative-next-plus-one-v1",
    val trendPriorSdLogResistancePerSession: Double = 0.04,
    val trendMinimumIndependentSessionsToLearn: Int = 3,
    val trendDataInformedMinimumIndependentSessions: Int = 6,
    val trendPriorDominatedPosteriorSdFraction: Double = 0.95,
    val trendDataInformedPosteriorSdFraction: Double = 0.80,
    val trendQuadraturePoints: Int = 5,
    val importanceTargetBasePosteriorMass: Double = 0.995,
    val importanceMinimumBasePosteriorMass: Double = 0.98,
    val importanceMinimumBaseNodes: Int = 64,
    val importanceMaximumBaseNodes: Int = 512,
    val nextIndependentSessionOffset: Double = 1.0,
    val approximationVersion: String = "v1-posterior-top-mass-importance-gh5-trend-v1",
) {
    init {
        require(semanticVersion.isNotBlank() && trendCoordinateVersion.isNotBlank() && approximationVersion.isNotBlank())
        require(trendPriorSdLogResistancePerSession.isFinite() && trendPriorSdLogResistancePerSession > 0.0)
        require(trendMinimumIndependentSessionsToLearn >= 3)
        require(trendDataInformedMinimumIndependentSessions >= trendMinimumIndependentSessionsToLearn)
        require(trendPriorDominatedPosteriorSdFraction in 0.0..1.0)
        require(trendDataInformedPosteriorSdFraction in 0.0..trendPriorDominatedPosteriorSdFraction)
        require(trendQuadraturePoints == 5) { "Candidate v2 freezes five-point Gauss-Hermite trend quadrature." }
        require(importanceTargetBasePosteriorMass in 0.0..1.0)
        require(importanceMinimumBasePosteriorMass in 0.0..importanceTargetBasePosteriorMass)
        require(importanceMinimumBaseNodes > 0)
        require(importanceMaximumBaseNodes >= importanceMinimumBaseNodes)
        require(nextIndependentSessionOffset == 1.0)
        require(baseConfig.contextConsumption.startsWith("NONE:"))
    }

    val evidencePolicyIdentity: String get() = baseConfig.evidencePolicyIdentity
    val contextConsumption: String get() = baseConfig.contextConsumption

    fun toModelConfig(createdAt: Instant): ModelConfigDefinition {
        val inherited = baseConfig.toModelConfig(createdAt)
        return ModelConfigDefinition.create(
            component = InferenceModelComponent.DYNAMIC_CAPABILITY,
            modelFamily = "stochastic_frontier_session_trend",
            modelName = "half_normal_slack_student_t_noise_importance_trend",
            semanticVersion = semanticVersion,
            configSchemaVersion = 1,
            parameters = mapOf(
                "capabilityStateSemantics" to DynamicResistanceV1Contract.CAPABILITY_STATE_SEMANTICS,
                "inheritedCandidateV1ModelVersion" to baseConfig.semanticVersion,
                "inheritedCandidateV1ModelConfigId" to inherited.id.value,
                "evidencePolicyIdentity" to baseConfig.evidencePolicyIdentity,
                "resistanceResolverIdentity" to baseConfig.resistanceResolverIdentity,
                "referenceRepPolicy" to baseConfig.referenceRepPolicy.storageValue,
                "slackDistribution" to baseConfig.slackDistribution.storageValue,
                "noiseDistribution" to baseConfig.noiseDistribution.storageValue,
                "studentTDegreesOfFreedom" to baseConfig.studentTDegreesOfFreedom.toString(),
                "slopePrior" to "lognormal(median=${baseConfig.slopePriorMedian},logSd=${baseConfig.slopePriorLogSd})",
                "slackScalePrior" to "lognormal(median=${baseConfig.slackScalePriorMedian},logSd=${baseConfig.slackScalePriorLogSd})",
                "noiseScalePrior" to "lognormal(median=${baseConfig.noiseScalePriorMedian},logSd=${baseConfig.noiseScalePriorLogSd})",
                "withinSessionPolicy" to baseConfig.withinSessionPolicy.storageValue,
                "currentCapabilityPolicy" to baseConfig.currentCapabilityPolicy.storageValue,
                "recentIndependentSessionWindow" to baseConfig.recentIndependentSessionWindow.toString(),
                "repExtrapolationLogSdPerUnitOutsideDomain" to baseConfig.extrapolationLogSdPerUnitOutsideDomain.toString(),
                "trendCoordinateVersion" to trendCoordinateVersion,
                "frontierTrendPrior" to "normal(mean=0,sd=$trendPriorSdLogResistancePerSession)_log_resistance_per_independent_session",
                "trendLearningUnlock" to "sessions>=$trendMinimumIndependentSessionsToLearn",
                "trendDataInformed" to "sessions>=$trendDataInformedMinimumIndependentSessions,posteriorSd<=${trendDataInformedPosteriorSdFraction}*priorSd",
                "trendPriorDominated" to "posteriorSd>=${trendPriorDominatedPosteriorSdFraction}*priorSd",
                "trendQuadrature" to "gauss_hermite_$trendQuadraturePoints",
                "nextIndependentSessionOffset" to nextIndependentSessionOffset.toString(),
                "importanceTargetBasePosteriorMass" to importanceTargetBasePosteriorMass.toString(),
                "importanceMinimumBasePosteriorMass" to importanceMinimumBasePosteriorMass.toString(),
                "importanceMinimumBaseNodes" to importanceMinimumBaseNodes.toString(),
                "importanceMaximumBaseNodes" to importanceMaximumBaseNodes.toString(),
                "approximationVersion" to approximationVersion,
                "successfulSetSemantics" to DynamicResistanceSuccessfulSetSemantics.LOWER_BOUND_DEMONSTRATION.storageValue,
                "contextConsumption" to contextConsumption,
            ),
            createdAt = createdAt,
        )
    }
}

object DynamicTrendFrontierV2 {
    val config = DynamicTrendFrontierConfig()
    const val MODEL_VERSION = "n-bio-7b2-half-normal-student-t-session-trend-frontier-v2"
    const val DEVELOPMENT_EVALUATION_LABEL = "MODEL_DEVELOPMENT_RETROSPECTIVE_EVALUATION"
    const val STATUS = "PROVISIONAL_DEVELOPMENT_CANDIDATE_REQUIRES_FRESH_CONFIRMATION"
}

data class DynamicTrendFrontierPosteriorNode(
    val logFrontierAtLatestSession: Double,
    val slope: Double,
    val frontierTrend: Double,
    val slackScale: Double,
    val noiseScale: Double,
    val posteriorWeight: Double,
) {
    init {
        require(logFrontierAtLatestSession.isFinite())
        require(slope.isFinite() && slope > 0.0)
        require(frontierTrend.isFinite())
        require(slackScale.isFinite() && slackScale > 0.0)
        require(noiseScale.isFinite() && noiseScale > 0.0)
        require(posteriorWeight.isFinite() && posteriorWeight >= 0.0)
    }
}

data class DynamicTrendFrontierFit(
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
    val frontierAtLatestSession: PosteriorEstimate,
    val slope: DynamicFrontierParameterPosterior,
    val frontierTrend: DynamicFrontierParameterPosterior,
    val slackScale: DynamicFrontierParameterPosterior,
    val noiseScale: DynamicFrontierParameterPosterior,
    val observationSlack: List<DynamicObservationSlackPosterior>,
    val selectedObservationIds: List<String>,
    val selectedSessionIds: List<String>,
    val approximationVersion: String,
    val basePosteriorMassCaptured: Double,
    val posteriorEffectiveNodeCount: Double,
    val warnings: Set<String>,
    val posteriorNodes: List<DynamicTrendFrontierPosteriorNode>,
) : DynamicCapabilityFit {
    init {
        require(referenceRepetitions.isFinite() && referenceRepetitions > 0.0)
        require(modelVersion.isNotBlank() && evidencePolicyIdentity.isNotBlank() && approximationVersion.isNotBlank())
        require(support.observationCount > 0 && support.effectiveIndependentSessionCount > 0)
        require(observedRepMin > 0 && observedRepMax >= observedRepMin)
        require(observedResistanceMinKg > 0.0 && observedResistanceMaxKg >= observedResistanceMinKg)
        require(frontierAtLatestSession.summary != null && frontierAtLatestSession.summary.p05 > 0.0)
        require(frontierAtLatestSession.provenance.modelConfigId == modelConfigId)
        require(selectedObservationIds.size == support.observationCount)
        require(selectedObservationIds.distinct().size == selectedObservationIds.size)
        require(selectedSessionIds.distinct().size == support.effectiveIndependentSessionCount)
        require(observationSlack.map { it.observationId }.toSet() == selectedObservationIds.toSet())
        require(basePosteriorMassCaptured.isFinite() && basePosteriorMassCaptured in 0.0..1.0)
        require(posteriorEffectiveNodeCount.isFinite() && posteriorEffectiveNodeCount > 0.0)
        require(posteriorNodes.isNotEmpty())
        val weightSum = posteriorNodes.sumOf { it.posteriorWeight }
        require(weightSum.isFinite() && abs(weightSum - 1.0) <= 1e-8)
    }
}
