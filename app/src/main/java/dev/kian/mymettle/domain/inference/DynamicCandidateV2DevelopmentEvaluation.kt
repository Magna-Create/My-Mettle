package dev.kian.mymettle.domain.inference

import kotlin.math.abs

/**
 * Candidate-v2 comparison is MODEL-DEVELOPMENT RETROSPECTIVE EVALUATION.
 * The same inspected chronology that rejected v1 motivated v2, so these thresholds can select a
 * provisional development candidate but can never manufacture fresh confirmatory evidence.
 */
data class DynamicCandidateV2DevelopmentPolicy(
    val semanticVersion: String = "n-bio-7b-candidate-v2-development-comparison-v1",
    val minimumComparableEvaluableEvents: Int = 20,
    val minimumCrpsRelativeImprovement: Double = 0.03,
    val minimumAbsoluteSignedBiasRelativeImprovement: Double = 0.15,
    val minimumHighPitExcessReduction: Double = 0.05,
    val minimumCatastrophicRateReduction: Double = 0.05,
    val minimumCoverageImprovement: Double = 0.05,
    val maximumPredictiveLogWidthRatio: Double = 1.20,
    val maximumMedianMaeRatio: Double = 1.10,
    val maximumModelFailureRate: Double = 0.05,
    val minimumDirectionalImprovementCountForPromising: Int = 3,
) {
    init {
        require(semanticVersion.isNotBlank())
        require(minimumComparableEvaluableEvents > 0)
        require(minimumCrpsRelativeImprovement in 0.0..1.0)
        require(minimumAbsoluteSignedBiasRelativeImprovement in 0.0..1.0)
        require(minimumHighPitExcessReduction in 0.0..1.0)
        require(minimumCatastrophicRateReduction in 0.0..1.0)
        require(minimumCoverageImprovement in 0.0..1.0)
        require(maximumPredictiveLogWidthRatio >= 1.0)
        require(maximumMedianMaeRatio >= 1.0)
        require(maximumModelFailureRate in 0.0..1.0)
        require(minimumDirectionalImprovementCountForPromising in 1..5)
    }
}

enum class DynamicCandidateV2DevelopmentVerdict(val storageValue: String) {
    INSUFFICIENT_COMPARABLE_EVIDENCE("insufficient_comparable_evidence"),
    ACCEPTABLE_AS_7B_CANDIDATE_PENDING_FRESH_DATA("acceptable_as_7b_candidate_pending_fresh_data"),
    PROMISING_BUT_NEEDS_FRESH_CONFIRMATION("promising_but_needs_fresh_confirmation"),
    NO_MEANINGFUL_IMPROVEMENT("no_meaningful_improvement"),
    REQUIRES_DIFFERENT_TEMPORAL_MODEL("requires_different_temporal_model"),
    REQUIRES_NOISE_SLACK_REVISIT("requires_noise_slack_revisit"),
    REJECTED("rejected"),
}

data class DynamicCandidateDistributionMetrics(
    val evaluableCount: Int,
    val modelFailureRate: Double,
    val meanSignedLogResidual: Double?,
    val medianSignedLogResidual: Double?,
    val positiveResidualProportion: Double?,
    val coverage: Double?,
    val pitHighRate: Double?,
    val catastrophicContradictionRate: Double?,
    val meanCrpsLogResistance: Double?,
    val meanPredictiveLogWidth: Double?,
    val demonstrationMedianMaeKg: Double?,
    val meanLogPredictiveDensity: Double?,
) {
    init {
        require(evaluableCount >= 0)
        require(modelFailureRate in 0.0..1.0)
    }
}

data class DynamicCandidateV2DevelopmentComparison(
    val policyVersion: String,
    val v1: DynamicCandidateDistributionMetrics,
    val v2: DynamicCandidateDistributionMetrics,
    val crpsRelativeImprovement: Double?,
    val absoluteSignedBiasRelativeImprovement: Double?,
    val highPitExcessReduction: Double?,
    val coverageImprovement: Double?,
    val catastrophicRateReduction: Double?,
    val predictiveLogWidthRatio: Double?,
    val medianMaeRatio: Double?,
    val directionalImprovementCount: Int,
    val v2AbsoluteValidationVerdict: DynamicCapabilityCandidateVerdict,
    val verdict: DynamicCandidateV2DevelopmentVerdict,
    val limitations: List<String>,
)

object DynamicCandidateV2DevelopmentVerdictPolicy {
    /** Expected PIT high-bin mass under a coarse calibrated three-bin diagnostic. */
    private const val EXPECTED_HIGH_PIT_RATE = 1.0 / 3.0

    fun compare(
        v1: DynamicCandidateDistributionMetrics,
        v2: DynamicCandidateDistributionMetrics,
        v2AbsoluteValidationVerdict: DynamicCapabilityCandidateVerdict,
        policy: DynamicCandidateV2DevelopmentPolicy = DynamicCandidateV2DevelopmentPolicy(),
    ): DynamicCandidateV2DevelopmentComparison {
        val limitations = mutableListOf(
            "MODEL_DEVELOPMENT_RETROSPECTIVE_EVALUATION: these sessions were already inspected while diagnosing Candidate v1.",
            "A favourable v2 result cannot close N-BIO-7B without genuinely fresh future evidence.",
        )
        if (minOf(v1.evaluableCount, v2.evaluableCount) < policy.minimumComparableEvaluableEvents) {
            limitations += "Too few comparable evaluable demonstrations for the pre-registered development policy."
            return result(policy, v1, v2, v2AbsoluteValidationVerdict, DynamicCandidateV2DevelopmentVerdict.INSUFFICIENT_COMPARABLE_EVIDENCE, limitations)
        }
        if (v2.modelFailureRate > policy.maximumModelFailureRate || !v2.coreFinite()) {
            limitations += "Candidate v2 exceeded the numerical/availability guard or produced an incomplete core metric set."
            return result(policy, v1, v2, v2AbsoluteValidationVerdict, DynamicCandidateV2DevelopmentVerdict.REJECTED, limitations)
        }

        val crpsImprovement = relativeReduction(v1.meanCrpsLogResistance, v2.meanCrpsLogResistance)
        val biasImprovement = relativeReduction(v1.meanSignedLogResidual?.let(::abs), v2.meanSignedLogResidual?.let(::abs))
        val highPitReduction = if (v1.pitHighRate != null && v2.pitHighRate != null) {
            (v1.pitHighRate - EXPECTED_HIGH_PIT_RATE).coerceAtLeast(0.0) -
                (v2.pitHighRate - EXPECTED_HIGH_PIT_RATE).coerceAtLeast(0.0)
        } else null
        val coverageImprovement = pairedDifference(v2.coverage, v1.coverage)
        val catastrophicReduction = pairedDifference(v1.catastrophicContradictionRate, v2.catastrophicContradictionRate)
        val widthRatio = ratio(v2.meanPredictiveLogWidth, v1.meanPredictiveLogWidth)
        val maeRatio = ratio(v2.demonstrationMedianMaeKg, v1.demonstrationMedianMaeKg)

        val sharpnessGuard = widthRatio != null && widthRatio <= policy.maximumPredictiveLogWidthRatio
        val pointGuard = maeRatio != null && maeRatio <= policy.maximumMedianMaeRatio
        val crpsWin = crpsImprovement != null && crpsImprovement >= policy.minimumCrpsRelativeImprovement
        val biasWin = biasImprovement != null && biasImprovement >= policy.minimumAbsoluteSignedBiasRelativeImprovement
        val highPitWin = highPitReduction != null && highPitReduction >= policy.minimumHighPitExcessReduction
        val coverageWin = coverageImprovement != null && coverageImprovement >= policy.minimumCoverageImprovement
        val catastrophicWin = catastrophicReduction != null && catastrophicReduction >= policy.minimumCatastrophicRateReduction
        val directionalWins = listOf(crpsWin, biasWin, highPitWin, coverageWin, catastrophicWin).count { it }

        val absoluteAcceptable = v2AbsoluteValidationVerdict in setOf(
            DynamicCapabilityCandidateVerdict.ACCEPTABLE_FOR_SHADOW,
            DynamicCapabilityCandidateVerdict.ACCEPTABLE_WITH_LIMITATIONS,
        )
        val verdict = when {
            !sharpnessGuard && !crpsWin -> {
                limitations += "Coverage/sharpness guard failed: wider intervals without a pre-registered CRPS improvement are not success."
                DynamicCandidateV2DevelopmentVerdict.REQUIRES_NOISE_SLACK_REVISIT
            }
            !pointGuard -> {
                limitations += "Candidate v2 materially degraded demonstration-median point accuracy."
                DynamicCandidateV2DevelopmentVerdict.REJECTED
            }
            absoluteAcceptable && crpsWin && biasWin && sharpnessGuard ->
                DynamicCandidateV2DevelopmentVerdict.ACCEPTABLE_AS_7B_CANDIDATE_PENDING_FRESH_DATA
            directionalWins >= policy.minimumDirectionalImprovementCountForPromising && crpsWin && sharpnessGuard ->
                DynamicCandidateV2DevelopmentVerdict.PROMISING_BUT_NEEDS_FRESH_CONFIRMATION
            crpsWin && !biasWin && !highPitWin && !catastrophicWin -> {
                limitations += "Proper score improved without resolving the directional pessimism that motivated the temporal experiment."
                DynamicCandidateV2DevelopmentVerdict.REQUIRES_NOISE_SLACK_REVISIT
            }
            biasWin || highPitWin || catastrophicWin -> {
                limitations += "The temporal direction improved some diagnostics but not enough jointly under the pre-registered policy."
                DynamicCandidateV2DevelopmentVerdict.REQUIRES_DIFFERENT_TEMPORAL_MODEL
            }
            else -> DynamicCandidateV2DevelopmentVerdict.NO_MEANINGFUL_IMPROVEMENT
        }
        return DynamicCandidateV2DevelopmentComparison(
            policyVersion = policy.semanticVersion,
            v1 = v1,
            v2 = v2,
            crpsRelativeImprovement = crpsImprovement,
            absoluteSignedBiasRelativeImprovement = biasImprovement,
            highPitExcessReduction = highPitReduction,
            coverageImprovement = coverageImprovement,
            catastrophicRateReduction = catastrophicReduction,
            predictiveLogWidthRatio = widthRatio,
            medianMaeRatio = maeRatio,
            directionalImprovementCount = directionalWins,
            v2AbsoluteValidationVerdict = v2AbsoluteValidationVerdict,
            verdict = verdict,
            limitations = limitations,
        )
    }

    private fun result(
        policy: DynamicCandidateV2DevelopmentPolicy,
        v1: DynamicCandidateDistributionMetrics,
        v2: DynamicCandidateDistributionMetrics,
        absolute: DynamicCapabilityCandidateVerdict,
        verdict: DynamicCandidateV2DevelopmentVerdict,
        limitations: List<String>,
    ) = DynamicCandidateV2DevelopmentComparison(
        policyVersion = policy.semanticVersion,
        v1 = v1,
        v2 = v2,
        crpsRelativeImprovement = relativeReduction(v1.meanCrpsLogResistance, v2.meanCrpsLogResistance),
        absoluteSignedBiasRelativeImprovement = relativeReduction(v1.meanSignedLogResidual?.let(::abs), v2.meanSignedLogResidual?.let(::abs)),
        highPitExcessReduction = if (v1.pitHighRate != null && v2.pitHighRate != null) {
            (v1.pitHighRate - EXPECTED_HIGH_PIT_RATE).coerceAtLeast(0.0) -
                (v2.pitHighRate - EXPECTED_HIGH_PIT_RATE).coerceAtLeast(0.0)
        } else null,
        coverageImprovement = pairedDifference(v2.coverage, v1.coverage),
        catastrophicRateReduction = pairedDifference(v1.catastrophicContradictionRate, v2.catastrophicContradictionRate),
        predictiveLogWidthRatio = ratio(v2.meanPredictiveLogWidth, v1.meanPredictiveLogWidth),
        medianMaeRatio = ratio(v2.demonstrationMedianMaeKg, v1.demonstrationMedianMaeKg),
        directionalImprovementCount = 0,
        v2AbsoluteValidationVerdict = absolute,
        verdict = verdict,
        limitations = limitations,
    )

    private fun DynamicCandidateDistributionMetrics.coreFinite(): Boolean = listOfNotNull(
        meanSignedLogResidual,
        coverage,
        pitHighRate,
        catastrophicContradictionRate,
        meanCrpsLogResistance,
        meanPredictiveLogWidth,
        demonstrationMedianMaeKg,
        meanLogPredictiveDensity,
    ).size == 8 && listOfNotNull(
        meanSignedLogResidual,
        coverage,
        pitHighRate,
        catastrophicContradictionRate,
        meanCrpsLogResistance,
        meanPredictiveLogWidth,
        demonstrationMedianMaeKg,
        meanLogPredictiveDensity,
    ).all { it.isFinite() }

    private fun relativeReduction(before: Double?, after: Double?): Double? =
        if (before == null || after == null || !before.isFinite() || !after.isFinite() || before <= 0.0) null
        else (before - after) / before

    private fun ratio(numerator: Double?, denominator: Double?): Double? =
        if (numerator == null || denominator == null || !numerator.isFinite() || !denominator.isFinite() || denominator <= 0.0) null
        else numerator / denominator

    private fun pairedDifference(left: Double?, right: Double?): Double? =
        if (left == null || right == null || !left.isFinite() || !right.isFinite()) null else left - right
}
