package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.inference.MuscleExposure
import dev.kian.mymettle.domain.inference.NBio7DConfig
import dev.kian.mymettle.domain.inference.NBio7DPosteriorMath
import dev.kian.mymettle.domain.inference.NBio7DSessionEvaluator
import dev.kian.mymettle.domain.inference.NBio7DSetInput
import dev.kian.mymettle.domain.inference.SetDemandStructuralSupport
import dev.kian.mymettle.domain.inference.WeightedScalarNode
import dev.kian.mymettle.domain.performance.MetricFamily
import kotlin.math.abs

/**
 * Deterministic structural acceptance fixtures for N-BIO-7D. These cases validate implementation
 * semantics only; they are not empirical validation of delta, recruitment biology, or tau.
 */
data class NBio7DSyntheticCaseResult(
    val id: String,
    val passed: Boolean,
    val detail: String,
)

data class NBio7DSyntheticValidationReport(
    val cases: List<NBio7DSyntheticCaseResult>,
) {
    val passedCount: Int get() = cases.count { it.passed }
    val failedCount: Int get() = cases.size - passedCount
    val allPassed: Boolean get() = cases.isNotEmpty() && failedCount == 0
}

object NBio7DSyntheticValidation {
    fun run(config: NBio7DConfig = NBio7DConfig()): NBio7DSyntheticValidationReport {
        val exposure = MuscleExposure("muscle", "bilateral", 0.7, "recruitment:v1")
        val cases = listOf(
            case("high_demand") {
                val demand = demand(MetricFamily.DYNAMIC_RESISTANCE, listOf(0.01, 0.03, 0.04, 0.08), config = config)
                val dose = NBio7DPosteriorMath.effectiveDose(exposure, demand)
                demand.probabilityAtOrWithinDelta!! >= 0.75 && dose.summary != null
            },
            case("sub_frontier") {
                val demand = demand(MetricFamily.DYNAMIC_RESISTANCE, listOf(0.12, 0.16, 0.20, 0.24), config = config)
                demand.probabilityAtOrWithinDelta!! <= 0.01 && demand.structuralSupport == SetDemandStructuralSupport.RESOLVED
            },
            case("broad_capability") {
                val demand = demand(
                    MetricFamily.DYNAMIC_RESISTANCE,
                    listOf(0.02, 0.05, 0.18),
                    support = SetDemandStructuralSupport.BROAD,
                    config = config,
                )
                demand.structuralSupport == SetDemandStructuralSupport.BROAD && NBio7DPosteriorMath.effectiveDose(exposure, demand).summary != null
            },
            case("sparse_prior_dominated") {
                val demand = demand(
                    MetricFamily.DYNAMIC_RESISTANCE,
                    listOf(0.02, 0.08),
                    support = SetDemandStructuralSupport.PRIOR_DOMINATED,
                    config = config,
                )
                demand.structuralSupport == SetDemandStructuralSupport.PRIOR_DOMINATED && demand.frontierGapSummary != null
            },
            case("positive_trajectory_projection") {
                val baseline = demand(MetricFamily.DYNAMIC_RESISTANCE, listOf(0.02, 0.06, 0.10), config = config)
                val improved = demand(MetricFamily.DYNAMIC_RESISTANCE, listOf(0.08, 0.12, 0.16), config = config)
                improved.probabilityAtOrWithinDelta!! < baseline.probabilityAtOrWithinDelta!!
            },
            case("declining_trajectory_projection") {
                val baseline = demand(MetricFamily.DYNAMIC_RESISTANCE, listOf(0.08, 0.12, 0.16), config = config)
                val declined = demand(MetricFamily.DYNAMIC_RESISTANCE, listOf(0.02, 0.06, 0.10), config = config)
                declined.probabilityAtOrWithinDelta!! > baseline.probabilityAtOrWithinDelta!!
            },
            case("rep_extrapolation") {
                val demand = demand(
                    MetricFamily.DYNAMIC_RESISTANCE,
                    listOf(0.03, 0.09, 0.16),
                    support = SetDemandStructuralSupport.BROAD,
                    config = config,
                )
                demand.structuralSupport == SetDemandStructuralSupport.BROAD && demand.probabilityAtOrWithinDelta != null
            },
            case("hold_duration_extrapolation") {
                val demand = demand(
                    MetricFamily.LOADED_HOLD,
                    listOf(0.02, 0.09, 0.20),
                    support = SetDemandStructuralSupport.BROAD,
                    config = config,
                )
                demand.structuralSupport == SetDemandStructuralSupport.BROAD && demand.empiricalStatuses.size == 2
            },
            case("repeated_contraction_extrapolation") {
                val demand = demand(
                    MetricFamily.REPEATED_CONTRACTION,
                    listOf(0.03, 0.10, 0.22),
                    support = SetDemandStructuralSupport.BROAD,
                    config = config,
                )
                demand.structuralSupport == SetDemandStructuralSupport.BROAD && demand.empiricalStatuses.size == 2
            },
            case("duration_only") {
                val demand = demand(MetricFamily.DURATION_ONLY, listOf(0.01, 0.07, 0.13), config = config)
                demand.family == MetricFamily.DURATION_ONLY && demand.probabilityAtOrWithinDelta in 0.0..1.0 && demand.empiricalStatuses.size == 2
            },
            case("semantic_boundary_fail_closed") {
                val demand = NBio7DPosteriorMath.unsupportedDemand(MetricFamily.DYNAMIC_RESISTANCE, config)
                val dose = NBio7DPosteriorMath.effectiveDose(exposure, demand)
                demand.structuralSupport == SetDemandStructuralSupport.UNSUPPORTED &&
                    dose.summary == null && dose.nodes.isEmpty() && abs(dose.exposure.conservativeExposure - 0.7) < 1e-12
            },
            case("side_isolation") {
                val evaluator = NBio7DSessionEvaluator(config)
                val result = evaluator.evaluate(
                    listOf(
                        NBio7DSetInput(
                            setObservationId = "set-side",
                            capabilityStreamKey = "stream",
                            family = MetricFamily.DYNAMIC_RESISTANCE,
                            logObservedPerformance = 0.0,
                            logFrontierNodes = nodes(0.01, 0.08),
                            inheritedDemandSupport = SetDemandStructuralSupport.RESOLVED,
                            exposures = listOf(
                                MuscleExposure("muscle", "left", 0.7, "recruitment:v1"),
                                MuscleExposure("muscle", "right", 0.4, "recruitment:v1"),
                            ),
                        ),
                    ),
                )
                result.muscleResults.map { it.key.side }.toSet() == setOf("left", "right") && result.muscleResults.size == 2
            },
            case("numerical_stress") {
                val stress = listOf(
                    WeightedScalarNode("tiny", 25.0, 1e-300),
                    WeightedScalarNode("a", 0.049999999999, 1.0),
                    WeightedScalarNode("b", 0.050000000001, 1.0),
                    WeightedScalarNode("far", 30.0, 1e-250),
                )
                val demand = NBio7DPosteriorMath.setDemandFromLogFrontier(
                    MetricFamily.DYNAMIC_RESISTANCE,
                    stress,
                    0.0,
                    SetDemandStructuralSupport.RESOLVED,
                    config,
                )
                val q = demand.probabilityAtOrWithinDelta!!
                demand.frontierGapSummary != null && q.isFinite() && q in 0.0..1.0
            },
            case("frontier_contradiction_fail_closed") {
                val demand = demand(MetricFamily.DYNAMIC_RESISTANCE, listOf(-0.20, -0.10, -0.02, -0.01), config = config)
                val dose = NBio7DPosteriorMath.effectiveDose(exposure, demand)
                demand.structuralSupport == SetDemandStructuralSupport.FRONTIER_CONTRADICTION &&
                    dose.summary == null && dose.nodes.isEmpty()
            },
        )
        return NBio7DSyntheticValidationReport(cases)
    }

    private inline fun case(id: String, check: () -> Boolean): NBio7DSyntheticCaseResult = runCatching(check)
        .fold(
            onSuccess = { passed -> NBio7DSyntheticCaseResult(id, passed, if (passed) "pass" else "invariant_not_met") },
            onFailure = { failure -> NBio7DSyntheticCaseResult(id, false, failure::class.simpleName ?: "failure") },
        )

    private fun demand(
        family: MetricFamily,
        gaps: List<Double>,
        support: SetDemandStructuralSupport = SetDemandStructuralSupport.RESOLVED,
        config: NBio7DConfig,
    ) = NBio7DPosteriorMath.setDemandFromLogFrontier(
        family = family,
        logFrontierNodes = gaps.mapIndexed { index, gap -> WeightedScalarNode("n$index", gap, 1.0) },
        logObservedPerformance = 0.0,
        inheritedSupport = support,
        config = config,
    )

    private fun nodes(vararg values: Double): List<WeightedScalarNode> = values.mapIndexed { index, value ->
        WeightedScalarNode("n$index", value, 1.0)
    }
}
