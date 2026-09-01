package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import java.time.Instant
import kotlin.math.abs

/**
 * N-BIO-7B development Candidate v2 mathematical family.
 *
 * This is deliberately NOT physiology. [frontierTrend] is only the statistical trajectory of the
 * execution-profile frontier per independent-session ordinal under this mathematical model. It is
 * not strength growth, recovery, adaptation, fatigue, detraining, SkillState or Development.
 *
 *   y_s = c_0 + g*z_s - b*x_s - u_s + epsilon_s
 *   u_s ~ HalfNormal(sigma_u)
 *   epsilon_s ~ StudentT(df=5, 0, sigma_e)
 *
 * The latest selected training session has z=0, older selected sessions are -1,-2,..., and the next
 * independent-session forecast is z=+1. Candidate-v1 evidence, slope/slack/noise priors, session
 * weighting, temporal window and rep extrapolation are inherited unchanged.
 *
 * Historical note: [toModelConfig] is the pre-consolidation Candidate-v2 CONDITIONAL-LAPLACE
 * composite config. It remains immutable/readable. N-BIO-7B.X introduces independent mathematical
 * and solver identities rather than silently rewriting this existing config fingerprint.
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
    val trendLaplaceFiniteDifferenceSdFraction: Double = 0.50,
    val trendPosteriorQuadraturePoints: Int = 5,
    val laplaceMinimumValidBasePosteriorMass: Double = 0.995,
    val nextIndependentSessionOffset: Double = 1.0,
    val approximationVersion: String = "v1-joint-posterior-conditional-laplace-fd-gh5-trend-v1",
) {
    init {
        require(semanticVersion.isNotBlank() && trendCoordinateVersion.isNotBlank() && approximationVersion.isNotBlank())
        require(trendPriorSdLogResistancePerSession.isFinite() && trendPriorSdLogResistancePerSession > 0.0)
        require(trendMinimumIndependentSessionsToLearn >= 3)
        require(trendDataInformedMinimumIndependentSessions >= trendMinimumIndependentSessionsToLearn)
        require(trendPriorDominatedPosteriorSdFraction in 0.0..1.0)
        require(trendDataInformedPosteriorSdFraction in 0.0..trendPriorDominatedPosteriorSdFraction)
        require(trendLaplaceFiniteDifferenceSdFraction in 0.1..1.0)
        require(trendPosteriorQuadraturePoints == 5)
        require(laplaceMinimumValidBasePosteriorMass in 0.95..1.0)
        require(nextIndependentSessionOffset == 1.0)
        require(baseConfig.contextConsumption.startsWith("NONE:"))
    }

    val evidencePolicyIdentity: String get() = baseConfig.evidencePolicyIdentity
    val contextConsumption: String get() = baseConfig.contextConsumption
    val trendLaplaceFiniteDifferenceStep: Double
        get() = trendPriorSdLogResistancePerSession * trendLaplaceFiniteDifferenceSdFraction

    /** Historical pre-consolidation composite config: math + conditional-Laplace approximation. */
    fun toModelConfig(createdAt: Instant): ModelConfigDefinition {
        val inherited = baseConfig.toModelConfig(createdAt)
        return ModelConfigDefinition.create(
            component = InferenceModelComponent.DYNAMIC_CAPABILITY,
            modelFamily = "stochastic_frontier_session_trend",
            modelName = "half_normal_slack_student_t_noise_conditional_laplace_trend",
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
                "trendConditionalApproximation" to "second_order_laplace_about_zero;finiteDifferenceStep=${trendLaplaceFiniteDifferenceStep}",
                "trendPosteriorQuadrature" to "gauss_hermite_$trendPosteriorQuadraturePoints",
                "laplaceMinimumValidBasePosteriorMass" to laplaceMinimumValidBasePosteriorMass.toString(),
                "nextIndependentSessionOffset" to nextIndependentSessionOffset.toString(),
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

    val mathematicalModelIdentity: InferenceMathematicalModelIdentity = mathematicalIdentity(config)

    fun conditionalLaplaceSolverIdentity(value: DynamicTrendFrontierConfig): InferenceSolverIdentity = InferenceSolverIdentity(
        solverFamily = InferenceSolverFamily.SEQUENTIAL_LAPLACE,
        semanticVersion = "candidate-v2-conditional-laplace-v1",
        computeBackend = InferenceComputeBackend.KOTLIN_JVM,
        deterministicReplay = true,
        approximationDefinition = value.approximationVersion,
    )

    val conditionalLaplaceSolverIdentity: InferenceSolverIdentity = conditionalLaplaceSolverIdentity(config)

    fun mathematicalIdentity(value: DynamicTrendFrontierConfig): InferenceMathematicalModelIdentity =
        InferenceMathematicalModelIdentity(
            family = "dynamic_profile_local_frontier",
            semanticVersion = "candidate-v2-linear-session-trend-math-v1",
            definition = listOf(
                "y=c+g*z-b*x-u+epsilon",
                "trendCoordinate=${value.trendCoordinateVersion}",
                "trendPrior=normal(0,${value.trendPriorSdLogResistancePerSession})",
                "trendUnlock=${value.trendMinimumIndependentSessionsToLearn}",
                "slopePrior=lognormal(${value.baseConfig.slopePriorMedian},${value.baseConfig.slopePriorLogSd})",
                "slack=half_normal(${value.baseConfig.slackScalePriorMedian},${value.baseConfig.slackScalePriorLogSd})",
                "noise=student_t_df_${value.baseConfig.studentTDegreesOfFreedom}",
                "noisePrior=${value.baseConfig.noiseScalePriorMedian},${value.baseConfig.noiseScalePriorLogSd}",
                "sessionWeight=${value.baseConfig.withinSessionPolicy.storageValue}",
                "window=${value.baseConfig.recentIndependentSessionWindow}",
                "evidence=${value.baseConfig.evidencePolicyIdentity}",
                "context=${value.baseConfig.contextConsumption}",
            ).joinToString(";"),
        )
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
    /** Populated only by the historical conditional-Laplace solver. */
    val laplaceValidBasePosteriorMass: Double?,
    /** Populated only by the historical conditional-Laplace solver. */
    val laplaceFiniteDifferenceStep: Double?,
    val posteriorEffectiveNodeCount: Double,
    val warnings: Set<String>,
    val posteriorNodes: List<DynamicTrendFrontierPosteriorNode>,
    /** Required explicitly: mathematical candidate identity is independent of numerical solver identity. */
    val mathematicalModelIdentity: InferenceMathematicalModelIdentity,
    /** Required explicitly: no default solver identity may masquerade as the implementation that produced this fit. */
    val solverDiagnostics: InferenceSolverDiagnostics,
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
        require(laplaceValidBasePosteriorMass == null || laplaceValidBasePosteriorMass.isFinite() && laplaceValidBasePosteriorMass in 0.0..1.0)
        require(laplaceFiniteDifferenceStep == null || laplaceFiniteDifferenceStep.isFinite() && laplaceFiniteDifferenceStep > 0.0)
        require(posteriorEffectiveNodeCount.isFinite() && posteriorEffectiveNodeCount > 0.0)
        require(posteriorNodes.isNotEmpty())
        val weightSum = posteriorNodes.sumOf { it.posteriorWeight }
        require(weightSum.isFinite() && abs(weightSum - 1.0) <= 1e-8)
    }
}
