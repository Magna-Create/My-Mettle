package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.inference.MuscleExposure
import dev.kian.mymettle.domain.inference.NBio7DConfig
import dev.kian.mymettle.domain.inference.NBio7DPosteriorMath
import dev.kian.mymettle.domain.inference.SetDemandStructuralSupport
import dev.kian.mymettle.domain.inference.WeightedScalarNode
import dev.kian.mymettle.domain.performance.MetricFamily

data class NBio7DDeltaSensitivityPoint(
    val deltaLog: Double,
    val highDemandProbability: Double,
    val effectiveDoseMedian: Double,
)

data class NBio7DTauSensitivityPoint(
    val tau: Double,
    val rawDoseMedian: Double,
    val concaveDoseMedian: Double,
)

data class NBio7DSensitivityValidationReport(
    val deltaPoints: List<NBio7DDeltaSensitivityPoint>,
    val tauPoints: List<NBio7DTauSensitivityPoint>,
    val deltaMonotonic: Boolean,
    val deltaLeavesGapAndExposureUnchanged: Boolean,
    val tauLeavesRawDoseUnchanged: Boolean,
    val largerTauApproachesRawDose: Boolean,
) {
    val passed: Boolean get() = deltaMonotonic && deltaLeavesGapAndExposureUnchanged &&
        tauLeavesRawDoseUnchanged && largerTauApproachesRawDose
}

object NBio7DSensitivityValidation {
    fun run(): NBio7DSensitivityValidationReport {
        val frontier = listOf(
            WeightedScalarNode("a", 0.00, 0.20),
            WeightedScalarNode("b", 0.04, 0.30),
            WeightedScalarNode("c", 0.08, 0.30),
            WeightedScalarNode("d", 0.14, 0.20),
        )
        val exposure = MuscleExposure("segment", "bilateral", 0.7, "recruitment:v1")
        val deltaRuns = listOf(0.025, 0.05, 0.10).map { delta ->
            val demand = NBio7DPosteriorMath.setDemandFromLogFrontier(
                family = MetricFamily.DYNAMIC_RESISTANCE,
                logFrontierNodes = frontier,
                logObservedPerformance = 0.0,
                inheritedSupport = SetDemandStructuralSupport.RESOLVED,
                config = NBio7DConfig(dynamicResistanceDeltaLog = delta),
            )
            val dose = NBio7DPosteriorMath.effectiveDose(exposure, demand)
            Triple(demand, dose, NBio7DDeltaSensitivityPoint(
                deltaLog = delta,
                highDemandProbability = requireNotNull(demand.probabilityAtOrWithinDelta),
                effectiveDoseMedian = requireNotNull(dose.summary).estimateMedian,
            ))
        }
        val deltaPoints = deltaRuns.map { it.third }
        val deltaMonotonic = deltaPoints.zipWithNext().all { (left, right) ->
            left.highDemandProbability <= right.highDemandProbability &&
                left.effectiveDoseMedian <= right.effectiveDoseMedian
        }
        val deltaLeavesGapAndExposureUnchanged = deltaRuns.all { (demand, dose, _) ->
            demand.frontierGapSummary == deltaRuns.first().first.frontierGapSummary &&
                dose.exposure.conservativeExposure == exposure.conservativeExposure
        }

        val raw = listOf(
            WeightedScalarNode("a", 1.0, 0.25),
            WeightedScalarNode("b", 2.0, 0.25),
            WeightedScalarNode("c", 3.0, 0.25),
            WeightedScalarNode("d", 4.0, 0.25),
        )
        val tauRuns = listOf(2.0, 4.0, 8.0).map { tau ->
            NBio7DPosteriorMath.sessionDose(
                resolvedStreamNodes = listOf(raw),
                contributingSetCount = 4,
                unresolvedSetCount = 0,
                config = NBio7DConfig(tau = tau),
            )
        }
        val tauPoints = tauRuns.map { result ->
            NBio7DTauSensitivityPoint(
                tau = result.tau,
                rawDoseMedian = requireNotNull(result.rawSummary).estimateMedian,
                concaveDoseMedian = requireNotNull(result.concaveSummary).estimateMedian,
            )
        }
        val tauLeavesRawDoseUnchanged = tauRuns.all {
            it.rawNodes == tauRuns.first().rawNodes && it.rawSummary == tauRuns.first().rawSummary
        }
        val largerTauApproachesRawDose = tauPoints.zipWithNext().all { (left, right) ->
            right.concaveDoseMedian > left.concaveDoseMedian &&
                (right.rawDoseMedian - right.concaveDoseMedian) < (left.rawDoseMedian - left.concaveDoseMedian)
        } && tauPoints.all { it.concaveDoseMedian < it.rawDoseMedian }

        return NBio7DSensitivityValidationReport(
            deltaPoints = deltaPoints,
            tauPoints = tauPoints,
            deltaMonotonic = deltaMonotonic,
            deltaLeavesGapAndExposureUnchanged = deltaLeavesGapAndExposureUnchanged,
            tauLeavesRawDoseUnchanged = tauLeavesRawDoseUnchanged,
            largerTauApproachesRawDose = largerTauApproachesRawDose,
        )
    }
}
