package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DurationOnlyCapabilityQuery
import dev.kian.mymettle.domain.inference.LoadedHoldCapabilityQuery
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityFit
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityQuery
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityV1
import dev.kian.mymettle.domain.inference.NonDynamicProfileSemantics
import dev.kian.mymettle.domain.inference.PosteriorSummary
import dev.kian.mymettle.domain.inference.RepeatedContractionCapabilityQuery
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.NonDynamicPosteriorFidelity
import dev.kian.mymettle.engine.inference.NonDynamicPosteriorFidelityResult
import dev.kian.mymettle.engine.performance.NonDynamicAdaptiveSparseSolver
import dev.kian.mymettle.engine.performance.NonDynamicCapabilityEvidenceProjector
import dev.kian.mymettle.engine.performance.NonDynamicDenseReferenceSolver
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Pre-registered synthetic latent-truth validation for N-BIO-7C.
 *
 * These fixtures establish that the implementation behaves according to its declared mathematics.
 * They are deliberately NOT empirical evidence that the priors/equations describe real human performance.
 * PD-001 keeps that empirical claim quarantined until suitable longitudinal evidence exists.
 */
object NBio7CSyntheticValidation {
    const val PROTOCOL_VERSION = "n-bio-7c-synthetic-latent-truth-v1"

    data class CaseResult(
        val family: MetricFamily,
        val scenario: String,
        val independentSessions: Int,
        val truthFrontierAtReference: Double,
        val truthSlope: Double?,
        val truthTrajectory: Double,
        val sparseFrontier: PosteriorSummary,
        val sparseSlope: PosteriorSummary?,
        val sparseTrajectory: PosteriorSummary,
        val denseFrontier: PosteriorSummary,
        val denseSlope: PosteriorSummary?,
        val denseTrajectory: PosteriorSummary,
        val fidelity: NonDynamicPosteriorFidelityResult,
        val inDomainPrediction: PosteriorSummary,
        val stressPrediction: PosteriorSummary,
        val recoveryChecks: Map<String, Boolean>,
        val runtimeMillisSparse: Long,
        val runtimeMillisDense: Long,
        val numericalFailure: String?,
    ) {
        val passed: Boolean get() = numericalFailure == null && recoveryChecks.values.all { it }
    }

    data class Report(
        val protocolVersion: String,
        val cases: List<CaseResult>,
    ) {
        val passed: Boolean get() = cases.isNotEmpty() && cases.all { it.passed }
        val familyPassed: Map<MetricFamily, Boolean>
            get() = NonDynamicCapabilityV1.supportedFamilies.associateWith { family ->
                cases.filter { it.family == family }.let { familyCases -> familyCases.isNotEmpty() && familyCases.all { it.passed } }
            }
    }

    fun run(): Report {
        val results = mutableListOf<CaseResult>()
        NonDynamicCapabilityV1.supportedFamilies.sortedBy { it.storageValue }.forEach { family ->
            results += evaluate(family, Scenario.STABLE)
            results += evaluate(family, Scenario.UPWARD)
            results += evaluate(family, Scenario.DOWNWARD)
            results += evaluate(family, Scenario.SPARSE)
            results += evaluate(family, Scenario.NUMERICAL_STRESS)
        }
        return Report(PROTOCOL_VERSION, results)
    }

    private enum class Scenario(
        val storageValue: String,
        val sessions: Int,
        val trajectory: Double,
    ) {
        STABLE("stable", 6, 0.0),
        UPWARD("upward", 6, 0.04),
        DOWNWARD("downward", 6, -0.04),
        SPARSE("sparse", 2, 0.0),
        NUMERICAL_STRESS("numerical_stress", 6, 0.02),
    }

    private fun evaluate(family: MetricFamily, scenario: Scenario): CaseResult {
        val profile = profile(family, scenario)
        val config = NonDynamicCapabilityV1.configFor(family)
        val truthFrontier = when (family) {
            MetricFamily.LOADED_HOLD -> if (scenario == Scenario.NUMERICAL_STRESS) 75.0 else 40.0
            MetricFamily.DURATION_ONLY -> if (scenario == Scenario.NUMERICAL_STRESS) 240.0 else 70.0
            MetricFamily.REPEATED_CONTRACTION -> if (scenario == Scenario.NUMERICAL_STRESS) 42.0 else 30.0
            else -> error("Unsupported N-BIO-7C family")
        }
        val truthSlope = when (family) {
            MetricFamily.LOADED_HOLD -> 0.55
            MetricFamily.REPEATED_CONTRACTION -> 0.30
            MetricFamily.DURATION_ONLY -> null
            else -> error("Unsupported N-BIO-7C family")
        }
        val evidence = syntheticEvidence(profile, scenario, truthFrontier, truthSlope)
        val projection = NonDynamicCapabilityEvidenceProjector.project(profile, Laterality.UNKNOWN, evidence)
        val horizon = projection.evidence.maxOf { it.completedAt }
        val sparseSolver = NonDynamicAdaptiveSparseSolver(config)
        val denseSolver = NonDynamicDenseReferenceSolver(config)
        var sparse: NonDynamicCapabilityFit? = null
        var dense: NonDynamicCapabilityFit? = null
        var sparseMillis = 0L
        var denseMillis = 0L
        var failure: Throwable? = null
        try {
            val sparseStart = System.nanoTime()
            sparse = sparseSolver.fit(projection, horizon, CREATED_AT)
            sparseMillis = elapsedMillis(sparseStart)
            val denseStart = System.nanoTime()
            dense = denseSolver.fit(projection, horizon, CREATED_AT)
            denseMillis = elapsedMillis(denseStart)
        } catch (problem: Throwable) {
            failure = problem
        }
        if (failure != null || sparse == null || dense == null) {
            val placeholder = PosteriorSummary(1.0, 1.0, 1.0, 0.0)
            return CaseResult(
                family = family,
                scenario = scenario.storageValue,
                independentSessions = scenario.sessions,
                truthFrontierAtReference = truthFrontier,
                truthSlope = truthSlope,
                truthTrajectory = scenario.trajectory,
                sparseFrontier = placeholder,
                sparseSlope = null,
                sparseTrajectory = placeholder,
                denseFrontier = placeholder,
                denseSlope = null,
                denseTrajectory = placeholder,
                fidelity = NonDynamicPosteriorFidelityResult(
                    referenceSolver = "unavailable",
                    candidateSolver = "unavailable",
                    referenceNodeCount = 0,
                    candidateNodeCount = 0,
                    referenceRetainedBaseNodeCount = 0,
                    candidateRetainedBaseNodeCount = 0,
                    referencePositiveTrajectoryProbability = 0.0,
                    candidatePositiveTrajectoryProbability = 0.0,
                    positiveTrajectoryProbabilityAbsoluteError = 1.0,
                    marginals = emptyList(),
                    covariances = emptyList(),
                    queries = emptyList(),
                ),
                inDomainPrediction = placeholder,
                stressPrediction = placeholder,
                recoveryChecks = mapOf("fit_completed" to false),
                runtimeMillisSparse = sparseMillis,
                runtimeMillisDense = denseMillis,
                numericalFailure = "${failure?.javaClass?.simpleName ?: "unknown"}:${failure?.message ?: "fit unavailable"}",
            )
        }

        val queries = representativeQueries(family)
        val fidelity = NonDynamicPosteriorFidelity.compare(denseSolver, dense, sparseSolver, sparse, queries)
        val inDomain = requireNotNull(sparseSolver.predict(sparse, queries.first().second).summary)
        val stress = requireNotNull(sparseSolver.predict(sparse, queries.last().second).summary)
        val frontier = requireNotNull(sparse.frontierAtReference.summary)
        val denseFrontier = requireNotNull(dense.frontierAtReference.summary)
        val sparseTrajectory = sparse.trajectory.summary
        val denseTrajectory = dense.trajectory.summary
        val checks = linkedMapOf<String, Boolean>()
        checks["frontier_truth_in_sparse_90pct_interval"] = truthFrontier in frontier.p05..frontier.p95
        checks["frontier_truth_in_dense_90pct_interval"] = truthFrontier in denseFrontier.p05..denseFrontier.p95
        if (truthSlope != null) {
            checks["slope_truth_in_sparse_90pct_interval"] = containsWithFloatingPointTolerance(requireNotNull(sparse.slope).summary, truthSlope)
            checks["slope_truth_in_dense_90pct_interval"] = containsWithFloatingPointTolerance(requireNotNull(dense.slope).summary, truthSlope)
        }
        when (scenario) {
            Scenario.UPWARD -> checks["trajectory_direction_recovered"] = sparseTrajectory.p50 > 0.0
            Scenario.DOWNWARD -> checks["trajectory_direction_recovered"] = sparseTrajectory.p50 < 0.0
            Scenario.STABLE -> checks["zero_trajectory_retained_in_interval"] = sparseTrajectory.p05 <= 0.0 && sparseTrajectory.p95 >= 0.0
            Scenario.SPARSE -> {
                checks["sparse_trajectory_fixed_neutral"] = sparseTrajectory.p50 == 0.0
                checks["sparse_frontier_remains_broad"] = relativeWidth(frontier) >= 0.15
            }
            Scenario.NUMERICAL_STRESS -> checks["stress_query_remains_finite"] = listOf(stress.p05, stress.p50, stress.p95, stress.posteriorVariance).all { it.isFinite() }
        }
        checks["adaptive_sparse_median_fidelity"] = fidelity.queries.all { it.p50RelativeError <= 0.10 }
        checks["adaptive_sparse_tail_fidelity"] = fidelity.maximumQueryTailRelativeError <= 0.30
        checks["adaptive_sparse_trajectory_fidelity"] = fidelity.positiveTrajectoryProbabilityAbsoluteError <= 0.15
        checks["adaptive_sparse_retains_no_more_base_nodes_than_dense"] = sparse.retainedBaseNodeCount <= dense.retainedBaseNodeCount
        if (scenario == Scenario.NUMERICAL_STRESS) {
            checks["out_of_domain_or_horizon_uncertainty_not_narrower"] =
                relativeWidth(stress) >= relativeWidth(inDomain) || stress.posteriorVariance >= inDomain.posteriorVariance
        }

        return CaseResult(
            family = family,
            scenario = scenario.storageValue,
            independentSessions = scenario.sessions,
            truthFrontierAtReference = truthFrontier,
            truthSlope = truthSlope,
            truthTrajectory = scenario.trajectory,
            sparseFrontier = frontier,
            sparseSlope = sparse.slope?.summary,
            sparseTrajectory = sparseTrajectory,
            denseFrontier = denseFrontier,
            denseSlope = dense.slope?.summary,
            denseTrajectory = denseTrajectory,
            fidelity = fidelity,
            inDomainPrediction = inDomain,
            stressPrediction = stress,
            recoveryChecks = checks,
            runtimeMillisSparse = sparseMillis,
            runtimeMillisDense = denseMillis,
            numericalFailure = null,
        )
    }

    private fun syntheticEvidence(
        profile: NonDynamicProfileSemantics,
        scenario: Scenario,
        currentFrontier: Double,
        slope: Double?,
    ): List<CompletedSetEvidence> = buildList {
        for (session in 0 until scenario.sessions) {
            val z = (session - (scenario.sessions - 1)).toDouble()
            when (profile.metricFamily) {
                MetricFamily.LOADED_HOLD -> {
                    val durations = if (scenario == Scenario.NUMERICAL_STRESS) listOf(12.0, 90.0) else listOf(20.0, 45.0)
                    durations.forEachIndexed { ordinal, duration ->
                        val logFrontier = ln(currentFrontier) + scenario.trajectory * z - requireNotNull(slope) * ln(duration / 30.0)
                        val demonstrated = exp(logFrontier - 0.12)
                        add(observation(profile, session, ordinal, duration = duration, load = demonstrated))
                    }
                }
                MetricFamily.DURATION_ONLY -> {
                    val logFrontier = ln(currentFrontier) + scenario.trajectory * z
                    add(observation(profile, session, 0, duration = exp(logFrontier - 0.16)))
                }
                MetricFamily.REPEATED_CONTRACTION -> {
                    val cycles = if (scenario == Scenario.NUMERICAL_STRESS) listOf(4, 32) else listOf(8, 16)
                    // Lower-median C_ref is 8 for the ordinary pair and 4 for the stress pair.
                    val reference = cycles.first().toDouble()
                    cycles.forEachIndexed { ordinal, cycleCount ->
                        val logFrontier = ln(currentFrontier) + scenario.trajectory * z - requireNotNull(slope) * ln(cycleCount / reference)
                        val demonstrated = exp(logFrontier - 0.12)
                        add(
                            observation(
                                profile,
                                session,
                                ordinal,
                                load = demonstrated,
                                cycles = cycleCount,
                                cadence = 30.0,
                                duration = cycleCount / 30.0 * 60.0,
                            ),
                        )
                    }
                }
                else -> error("Unsupported N-BIO-7C family")
            }
        }
    }

    private fun representativeQueries(family: MetricFamily): List<Pair<String, NonDynamicCapabilityQuery>> = when (family) {
        MetricFamily.LOADED_HOLD -> listOf(
            "reference_30s" to LoadedHoldCapabilityQuery(30.0),
            "far_duration_180s" to LoadedHoldCapabilityQuery(180.0),
        )
        MetricFamily.DURATION_ONLY -> listOf(
            "current" to DurationOnlyCapabilityQuery(0),
            "future_8_sessions" to DurationOnlyCapabilityQuery(8),
        )
        MetricFamily.REPEATED_CONTRACTION -> listOf(
            "reference_region_8_cycles" to RepeatedContractionCapabilityQuery(8),
            "far_cycles_64" to RepeatedContractionCapabilityQuery(64),
        )
        else -> error("Unsupported N-BIO-7C family")
    }

    private fun profile(family: MetricFamily, scenario: Scenario): NonDynamicProfileSemantics = NonDynamicProfileSemantics(
        executionProfileVersionId = ExecutionProfileVersionId("synthetic_7c_${family.storageValue}_${scenario.storageValue}_v1"),
        executionProfileId = ExecutionProfileId("synthetic_7c_${family.storageValue}_${scenario.storageValue}"),
        metricFamily = family,
        resistanceModel = if (family == MetricFamily.DURATION_ONLY) {
            ResistanceModel("synthetic-none-v1", ResistanceSemantics.NONE, 0.0, 0.0, 0.0)
        } else {
            ResistanceModel("synthetic-external-v1", ResistanceSemantics.EXTERNAL, 0.0, 1.0, 0.0)
        },
        entryBasis = EntryBasis.TOTAL,
        lateralityMode = LateralityMode.UNKNOWN,
    )

    private fun observation(
        profile: NonDynamicProfileSemantics,
        session: Int,
        ordinal: Int,
        duration: Double? = null,
        load: Double? = null,
        cycles: Int? = null,
        cadence: Double? = null,
    ): CompletedSetEvidence {
        val values = buildList {
            load?.let { add(metric(PerformanceMetric.EXTERNAL_LOAD, it, UnitId.KILOGRAM)) }
            cycles?.let { add(metric(PerformanceMetric.REPETITIONS, it.toDouble(), UnitId.REPETITION)) }
            duration?.let { add(metric(PerformanceMetric.DURATION, it, UnitId.SECOND)) }
            cadence?.let { add(metric(PerformanceMetric.CADENCE, it, UnitId.EVENTS_PER_MINUTE)) }
        }
        val suffix = "${profile.executionProfileVersionId.value}_${session}_$ordinal"
        return CompletedSetEvidence(
            setRecordId = "set_$suffix",
            observationId = "obs_$suffix",
            sessionExerciseId = "se_$suffix",
            executionProfileVersionId = profile.executionProfileVersionId,
            metricFamily = profile.metricFamily,
            laterality = Laterality.UNKNOWN,
            completedAt = BASE.plusSeconds(86_400L * session + ordinal),
            metricValues = values,
            bodyMassContextKg = null,
            warmUp = false,
            kind = "work",
            observationSource = "corrected_lite_import",
            sessionId = "session_${profile.executionProfileVersionId.value}_$session",
        )
    }

    private fun metric(metric: PerformanceMetric, value: Double, unit: UnitId) =
        PerformanceMetricValue(metric = metric, entered = Quantity(value, unit))

    private fun containsWithFloatingPointTolerance(summary: PosteriorSummary, truth: Double): Boolean {
        val scale = maxOf(1.0, abs(truth), abs(summary.p05), abs(summary.p95))
        val tolerance = 1e-12 * scale
        return truth >= summary.p05 - tolerance && truth <= summary.p95 + tolerance
    }

    private fun relativeWidth(summary: PosteriorSummary): Double =
        (summary.p95 - summary.p05) / max(1e-12, summary.p50)

    private fun elapsedMillis(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L

    private val BASE: Instant = Instant.parse("2026-01-01T12:00:00Z")
    private val CREATED_AT: Instant = Instant.parse("2026-09-01T00:00:00Z")
}
