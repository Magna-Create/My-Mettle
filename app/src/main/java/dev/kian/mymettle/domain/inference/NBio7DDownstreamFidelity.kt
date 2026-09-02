package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.performance.MetricFamily
import kotlin.math.abs
import kotlin.math.ln

/**
 * Compares two already-fit capability posteriors only after projection through N-BIO-7D.
 * This deliberately does not judge or tune the capability solvers themselves; it quantifies how
 * an existing solver approximation propagates into SetDemand, EffectiveDose and SessionDose.
 */
object NBio7DDownstreamFidelity {
    data class DynamicPerformedSet(
        val repetitions: Int,
        val resistanceKg: Double,
        val exposure: MuscleExposure,
    ) {
        init {
            require(repetitions > 0)
            require(resistanceKg.isFinite() && resistanceKg > 0.0)
        }
    }

    data class SetComparison(
        val repetitions: Int,
        val gapP05AbsoluteError: Double,
        val gapP50AbsoluteError: Double,
        val gapP95AbsoluteError: Double,
        val demandProbabilityAbsoluteError: Double,
        val effectiveDoseP05AbsoluteError: Double,
        val effectiveDoseP50AbsoluteError: Double,
        val effectiveDoseP95AbsoluteError: Double,
    )

    data class Result(
        val sets: List<SetComparison>,
        val sessionRawP05AbsoluteError: Double,
        val sessionRawP50AbsoluteError: Double,
        val sessionRawP95AbsoluteError: Double,
        val sessionConcaveP05AbsoluteError: Double,
        val sessionConcaveP50AbsoluteError: Double,
        val sessionConcaveP95AbsoluteError: Double,
        val referenceSession: SessionDosePosterior,
        val challengerSession: SessionDosePosterior,
    ) {
        val maximumGapQuantileAbsoluteError: Double
            get() = sets.maxOfOrNull { maxOf(it.gapP05AbsoluteError, it.gapP50AbsoluteError, it.gapP95AbsoluteError) } ?: 0.0
        val maximumDemandProbabilityAbsoluteError: Double
            get() = sets.maxOfOrNull { it.demandProbabilityAbsoluteError } ?: 0.0
        val maximumEffectiveDoseQuantileAbsoluteError: Double
            get() = sets.maxOfOrNull {
                maxOf(
                    it.effectiveDoseP05AbsoluteError,
                    it.effectiveDoseP50AbsoluteError,
                    it.effectiveDoseP95AbsoluteError,
                )
            } ?: 0.0
    }

    fun compareDynamic(
        reference: DynamicTrendFrontierFit,
        challenger: DynamicTrendFrontierFit,
        performedSets: List<DynamicPerformedSet>,
        inheritedSupport: SetDemandStructuralSupport = SetDemandStructuralSupport.RESOLVED,
        config: NBio7DConfig = NBio7DConfig(),
    ): Result {
        require(reference.mathematicalModelIdentity == challenger.mathematicalModelIdentity) {
            "7D downstream fidelity requires the same capability mathematics."
        }
        require(reference.executionProfileVersionId == challenger.executionProfileVersionId)
        require(reference.side == challenger.side)
        require(performedSets.isNotEmpty())

        val referenceDoses = ArrayList<EffectiveDosePosterior>(performedSets.size)
        val challengerDoses = ArrayList<EffectiveDosePosterior>(performedSets.size)
        val comparisons = performedSets.map { performed ->
            val referenceDemand = NBio7DPosteriorMath.setDemandFromLogFrontier(
                family = MetricFamily.DYNAMIC_RESISTANCE,
                logFrontierNodes = NBio7DCapabilityProjection.dynamicResistanceLogFrontier(reference, performed.repetitions),
                logObservedPerformance = ln(performed.resistanceKg),
                inheritedSupport = inheritedSupport,
                config = config,
            )
            val challengerDemand = NBio7DPosteriorMath.setDemandFromLogFrontier(
                family = MetricFamily.DYNAMIC_RESISTANCE,
                logFrontierNodes = NBio7DCapabilityProjection.dynamicResistanceLogFrontier(challenger, performed.repetitions),
                logObservedPerformance = ln(performed.resistanceKg),
                inheritedSupport = inheritedSupport,
                config = config,
            )
            val referenceDose = NBio7DPosteriorMath.effectiveDose(performed.exposure, referenceDemand)
            val challengerDose = NBio7DPosteriorMath.effectiveDose(performed.exposure, challengerDemand)
            require(referenceDose.isResolvedEnoughToAggregate && challengerDose.isResolvedEnoughToAggregate) {
                "Downstream solver fidelity fixture must remain structurally aggregatable in both representations."
            }
            referenceDoses += referenceDose
            challengerDoses += challengerDose
            val rg = requireNotNull(referenceDemand.frontierGapSummary)
            val cg = requireNotNull(challengerDemand.frontierGapSummary)
            val re = requireNotNull(referenceDose.summary)
            val ce = requireNotNull(challengerDose.summary)
            SetComparison(
                repetitions = performed.repetitions,
                gapP05AbsoluteError = abs(rg.credibleLower05 - cg.credibleLower05),
                gapP50AbsoluteError = abs(rg.estimateMedian - cg.estimateMedian),
                gapP95AbsoluteError = abs(rg.credibleUpper95 - cg.credibleUpper95),
                demandProbabilityAbsoluteError = abs(
                    requireNotNull(referenceDemand.probabilityAtOrWithinDelta) -
                        requireNotNull(challengerDemand.probabilityAtOrWithinDelta),
                ),
                effectiveDoseP05AbsoluteError = abs(re.credibleLower05 - ce.credibleLower05),
                effectiveDoseP50AbsoluteError = abs(re.estimateMedian - ce.estimateMedian),
                effectiveDoseP95AbsoluteError = abs(re.credibleUpper95 - ce.credibleUpper95),
            )
        }

        val referenceSession = NBio7DPosteriorMath.sessionDose(
            resolvedStreamNodes = listOf(NBio7DPosteriorMath.aggregateSharedStream(referenceDoses)),
            contributingSetCount = referenceDoses.size,
            unresolvedSetCount = 0,
            config = config,
        )
        val challengerSession = NBio7DPosteriorMath.sessionDose(
            resolvedStreamNodes = listOf(NBio7DPosteriorMath.aggregateSharedStream(challengerDoses)),
            contributingSetCount = challengerDoses.size,
            unresolvedSetCount = 0,
            config = config,
        )
        val rr = requireNotNull(referenceSession.rawSummary)
        val cr = requireNotNull(challengerSession.rawSummary)
        val rc = requireNotNull(referenceSession.concaveSummary)
        val cc = requireNotNull(challengerSession.concaveSummary)
        return Result(
            sets = comparisons,
            sessionRawP05AbsoluteError = abs(rr.credibleLower05 - cr.credibleLower05),
            sessionRawP50AbsoluteError = abs(rr.estimateMedian - cr.estimateMedian),
            sessionRawP95AbsoluteError = abs(rr.credibleUpper95 - cr.credibleUpper95),
            sessionConcaveP05AbsoluteError = abs(rc.credibleLower05 - cc.credibleLower05),
            sessionConcaveP50AbsoluteError = abs(rc.estimateMedian - cc.estimateMedian),
            sessionConcaveP95AbsoluteError = abs(rc.credibleUpper95 - cc.credibleUpper95),
            referenceSession = referenceSession,
            challengerSession = challengerSession,
        )
    }
}
