package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DynamicCandidateDistributionMetrics
import dev.kian.mymettle.domain.inference.DynamicCandidateV2DevelopmentComparison
import dev.kian.mymettle.domain.inference.DynamicCandidateV2DevelopmentPolicy
import dev.kian.mymettle.domain.inference.DynamicCandidateV2DevelopmentVerdictPolicy
import dev.kian.mymettle.domain.inference.DynamicCapabilityCandidateVerdict
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicCapabilityValidationPolicy
import dev.kian.mymettle.domain.inference.DynamicCapabilityValidationSummary
import dev.kian.mymettle.domain.inference.DynamicCapabilityVerdictPolicy
import dev.kian.mymettle.domain.inference.DynamicHeldOutEvaluation
import dev.kian.mymettle.domain.inference.DynamicHeldOutStatus
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidencePolicy
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.InferenceSolverIdentity
import dev.kian.mymettle.domain.inference.PrequentialWeightedIntervalScore
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.engine.performance.DynamicReferenceRepSelector
import dev.kian.mymettle.engine.performance.DynamicResistanceEvidenceProjector
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ln
import kotlin.system.measureTimeMillis

/** Predictive metrics that remain comparable across solver implementations of one mathematical model. */
data class DynamicTrendSolverPredictiveMetrics(
    val distribution: DynamicCandidateDistributionMetrics,
    val meanWeightedIntervalScoreLogResistance: Double?,
    val medianWeightedIntervalScoreLogResistance: Double?,
)

data class DynamicTrendSolverSessionDiagnostic(
    val sessionId: String,
    val priorIndependentSessionCount: Int,
    val solverIdentity: InferenceSolverIdentity,
    val trendP05: Double?,
    val trendP50: Double?,
    val trendP95: Double?,
    val effectivePosteriorNodeCount: Double?,
    val evaluatedNodeCount: Long?,
    val solverRuntimeNanos: Long?,
    val wallElapsedMillis: Long,
    val approximationFailure: String?,
    val fitFailureReason: String?,
)

data class DynamicTrendSolverHistoricalCandidateResult(
    val solverIdentity: InferenceSolverIdentity,
    val observations: List<DynamicHeldOutEvaluation>,
    val validationSummary: DynamicCapabilityValidationSummary,
    val predictiveMetrics: DynamicTrendSolverPredictiveMetrics,
    val absoluteValidationVerdict: DynamicCapabilityCandidateVerdict,
    val developmentComparisonAgainstV1: DynamicCandidateV2DevelopmentComparison,
    val sessionDiagnostics: List<DynamicTrendSolverSessionDiagnostic>,
    val extensionWallElapsedMillis: Long,
    val predictiveScoringElapsedMillis: Long,
)

data class DynamicTrendSolverHistoricalBakeoffResult(
    val protocolVersion: String,
    val v1Observations: List<DynamicHeldOutEvaluation>,
    val v1ValidationSummary: DynamicCapabilityValidationSummary,
    val v1PredictiveMetrics: DynamicTrendSolverPredictiveMetrics,
    val v1Verdict: DynamicCapabilityCandidateVerdict,
    val v1FitElapsedMillis: Long,
    val v1PredictiveScoringElapsedMillis: Long,
    val chronologicalFitCount: Int,
    val candidates: List<DynamicTrendSolverHistoricalCandidateResult>,
)

/**
 * Same-history Candidate-v2 mathematical/solver bake-off.
 *
 * One frozen Candidate-v1 fit is constructed at each historical cutoff. Every Candidate-v2 solver
 * receives that exact same frozen-v1 posterior, the same mathematical model identity, the same raw
 * evidence projection and the same held-out whole session. The dense reference can therefore answer
 * the mathematical Candidate-v2 question while faster solvers are evaluated separately for
 * approximation fidelity/runtime. No solver gets a different likelihood, prior or chronology.
 */
class DynamicTrendSolverHistoricalBakeoff(
    private val solvers: List<DynamicTrendCandidateV2Solver>,
    private val v1Model: DynamicStochasticFrontierModel = DynamicStochasticFrontierModel(
        dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2.config.baseConfig,
    ),
    private val evidencePolicy: DynamicResistanceEvidencePolicy = DynamicResistanceV2Contract.evidencePolicy,
    private val validationPolicy: DynamicCapabilityValidationPolicy = DynamicCapabilityValidationPolicy(),
    private val developmentPolicy: DynamicCandidateV2DevelopmentPolicy = DynamicCandidateV2DevelopmentPolicy(),
    private val configCreatedAt: Instant = Instant.parse("2026-08-31T00:00:00Z"),
) {
    init {
        require(solvers.isNotEmpty())
        require(solvers.map { it.solverIdentity }.distinct().size == solvers.size)
        val math = solvers.first().mathematicalModelIdentity
        require(solvers.all { it.mathematicalModelIdentity == math }) {
            "A solver bake-off must hold the mathematical model identity fixed."
        }
        require(solvers.all { it.baseConfig.evidencePolicyIdentity == evidencePolicy.identity })
        require(v1Model.config.evidencePolicyIdentity == evidencePolicy.identity)
    }

    private val summarizer = DynamicResistanceRetrospectiveEvaluator(v1Model, validationPolicy, configCreatedAt)

    fun evaluate(
        profile: DynamicResistanceProfileSemantics,
        side: Laterality,
        revisions: Collection<HistoricalCompletedSetEvidenceRevision>,
    ): DynamicTrendSolverHistoricalBakeoffResult {
        val candidateSessionIds = revisions
            .filter { it.evidence.matches(profile, side) }
            .map { requireNotNull(it.evidence.sessionId) }
            .toSet()
        val sessions = revisions
            .filter { requireNotNull(it.evidence.sessionId) in candidateSessionIds }
            .groupBy { requireNotNull(it.evidence.sessionId) }
            .entries
            .sortedWith(
                compareBy<Map.Entry<String, List<HistoricalCompletedSetEvidenceRevision>>> {
                    it.value.maxOf { revision -> revision.sessionCompletedAt }
                }.thenBy { it.key },
            )

        val priorSessionIds = linkedSetOf<String>()
        val v1Results = mutableListOf<DynamicHeldOutEvaluation>()
        val candidateResults = solvers.associateWith { mutableListOf<DynamicHeldOutEvaluation>() }
        val diagnostics = solvers.associateWith { mutableListOf<DynamicTrendSolverSessionDiagnostic>() }
        val extensionMillis = solvers.associateWith { longArrayOf(0L) }
        val scoringMillis = solvers.associateWith { longArrayOf(0L) }
        var v1FitMillis = 0L
        var v1ScoreMillis = 0L
        var fitCount = 0

        sessions.forEach { (sessionId, sessionRevisions) ->
            val cutoff = sessionRevisions.maxOf { it.sessionCompletedAt }
            require(sessionRevisions.all { it.sessionCompletedAt == cutoff })
            val trainingRaw = HistoricalObservationRevisionSelector.currentAsOf(
                revisions.filter { requireNotNull(it.evidence.sessionId) in priorSessionIds },
                cutoff,
            ).filter { it.matches(profile, side) }
            val heldOutRaw = HistoricalObservationRevisionSelector.currentAsOf(
                revisions.filter { requireNotNull(it.evidence.sessionId) == sessionId },
                cutoff,
            ).filter { it.matches(profile, side) }
            val trainingProjection = DynamicResistanceEvidenceProjector.project(
                profile, side, trainingRaw, evidencePolicy,
            )
            val heldOutProjection = DynamicResistanceEvidenceProjector.project(
                profile, side, heldOutRaw, evidencePolicy,
            )
            val training = trainingProjection.evidence.sortedEvidence()
            val heldOut = heldOutProjection.evidence.sortedEvidence()
            if (heldOut.isEmpty()) {
                priorSessionIds += sessionId
                return@forEach
            }
            if (training.isEmpty()) {
                val insufficient = heldOut.map { insufficient(it, sessionId, training) }
                v1Results += insufficient
                solvers.forEach { candidateResults.getValue(it) += insufficient }
                priorSessionIds += sessionId
                return@forEach
            }
            val reference = DynamicReferenceRepSelector.select(training, v1Model.config.referenceRepPolicy)
            if (reference == null) {
                val insufficient = heldOut.map { insufficient(it, sessionId, training) }
                v1Results += insufficient
                solvers.forEach { candidateResults.getValue(it) += insufficient }
                priorSessionIds += sessionId
                return@forEach
            }
            fitCount += 1
            val fixedTraining = DynamicResistanceEvidenceProjection(
                profile = trainingProjection.profile,
                side = trainingProjection.side,
                evidence = training,
                exclusions = trainingProjection.exclusions,
                referenceRepetitions = reference,
                policy = trainingProjection.policy,
            )
            val horizon = training.maxOf { it.completedAt }
            val benchmark = training.maxWithOrNull(
                compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId },
            )?.resistance?.value

            var frozenV1: dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit? = null
            var v1FailureReason: String? = null
            v1FitMillis += measureTimeMillis {
                try {
                    frozenV1 = v1Model.fit(
                        DynamicCapabilityFitRequest(
                            fixedTraining,
                            horizon,
                            v1Model.config.toModelConfig(configCreatedAt),
                        ),
                    )
                } catch (failure: DynamicCapabilityFitException) {
                    v1FailureReason = failure.reason.storageValue
                } catch (failure: IllegalArgumentException) {
                    v1FailureReason = "numerical_invariant:${failure.message ?: "illegal_argument"}"
                }
            }
            if (frozenV1 == null) {
                val reason = requireNotNull(v1FailureReason)
                val failed = heldOut.map { modelFailure(it, sessionId, training, reference, benchmark, reason) }
                v1Results += failed
                solvers.forEach { solver ->
                    candidateResults.getValue(solver) += failed
                    diagnostics.getValue(solver) += DynamicTrendSolverSessionDiagnostic(
                        sessionId = sessionId,
                        priorIndependentSessionCount = training.map { it.sessionId }.distinct().size,
                        solverIdentity = solver.solverIdentity,
                        trendP05 = null,
                        trendP50 = null,
                        trendP95 = null,
                        effectivePosteriorNodeCount = null,
                        evaluatedNodeCount = null,
                        solverRuntimeNanos = null,
                        wallElapsedMillis = 0L,
                        approximationFailure = null,
                        fitFailureReason = "base_v1:$reason",
                    )
                }
                priorSessionIds += sessionId
                return@forEach
            }
            val baseFit = requireNotNull(frozenV1)
            var v1Scored: List<DynamicHeldOutEvaluation> = emptyList()
            v1ScoreMillis += measureTimeMillis {
                v1Scored = score(baseFit, heldOut, sessionId, training, horizon, benchmark)
            }
            v1Results += v1Scored

            solvers.forEach { solver ->
                var fit: DynamicTrendFrontierFit? = null
                var fitFailureReason: String? = null
                val fitMillis = measureTimeMillis {
                    try {
                        fit = solver.fitFromFrozenV1(
                            DynamicCapabilityFitRequest(
                                fixedTraining,
                                horizon,
                                solver.modelConfig(configCreatedAt),
                            ),
                            baseFit,
                        )
                    } catch (failure: DynamicCapabilityFitException) {
                        fitFailureReason = failure.reason.storageValue
                    } catch (failure: IllegalArgumentException) {
                        fitFailureReason = "numerical_invariant:${failure.message ?: "illegal_argument"}"
                    }
                }
                extensionMillis.getValue(solver)[0] += fitMillis
                if (fit == null) {
                    val reason = requireNotNull(fitFailureReason)
                    candidateResults.getValue(solver) += heldOut.map {
                        modelFailure(it, sessionId, training, reference, benchmark, reason)
                    }
                    diagnostics.getValue(solver) += DynamicTrendSolverSessionDiagnostic(
                        sessionId = sessionId,
                        priorIndependentSessionCount = training.map { it.sessionId }.distinct().size,
                        solverIdentity = solver.solverIdentity,
                        trendP05 = null,
                        trendP50 = null,
                        trendP95 = null,
                        effectivePosteriorNodeCount = null,
                        evaluatedNodeCount = null,
                        solverRuntimeNanos = null,
                        wallElapsedMillis = fitMillis,
                        approximationFailure = null,
                        fitFailureReason = reason,
                    )
                } else {
                    val fitted = requireNotNull(fit)
                    val projected = solver.projectToNextSession(fitted)
                    var scored: List<DynamicHeldOutEvaluation> = emptyList()
                    val scoreMillis = measureTimeMillis {
                        scored = score(projected, heldOut, sessionId, training, horizon, benchmark)
                    }
                    scoringMillis.getValue(solver)[0] += scoreMillis
                    candidateResults.getValue(solver) += scored
                    diagnostics.getValue(solver) += DynamicTrendSolverSessionDiagnostic(
                        sessionId = sessionId,
                        priorIndependentSessionCount = fitted.support.effectiveIndependentSessionCount,
                        solverIdentity = solver.solverIdentity,
                        trendP05 = fitted.frontierTrend.summary.p05,
                        trendP50 = fitted.frontierTrend.summary.p50,
                        trendP95 = fitted.frontierTrend.summary.p95,
                        effectivePosteriorNodeCount = fitted.solverDiagnostics.effectiveNodeCount
                            ?: fitted.posteriorEffectiveNodeCount,
                        evaluatedNodeCount = fitted.solverDiagnostics.evaluatedNodeCount,
                        solverRuntimeNanos = fitted.solverDiagnostics.updateRuntimeNanos,
                        wallElapsedMillis = fitMillis,
                        approximationFailure = fitted.solverDiagnostics.approximationFailure,
                        fitFailureReason = null,
                    )
                }
            }
            priorSessionIds += sessionId
        }

        val v1Summary = summarizer.summarize(v1Results)
        val v1Metrics = predictiveMetrics(v1Results)
        val candidates = solvers.map { solver ->
            val observations = candidateResults.getValue(solver).toList()
            val summary = summarizer.summarize(observations)
            val metrics = predictiveMetrics(observations)
            val absolute = DynamicCapabilityVerdictPolicy.verdict(summary, validationPolicy)
            DynamicTrendSolverHistoricalCandidateResult(
                solverIdentity = solver.solverIdentity,
                observations = observations,
                validationSummary = summary,
                predictiveMetrics = metrics,
                absoluteValidationVerdict = absolute,
                developmentComparisonAgainstV1 = DynamicCandidateV2DevelopmentVerdictPolicy.compare(
                    v1Metrics.distribution,
                    metrics.distribution,
                    absolute,
                    developmentPolicy,
                ),
                sessionDiagnostics = diagnostics.getValue(solver).toList(),
                extensionWallElapsedMillis = extensionMillis.getValue(solver)[0],
                predictiveScoringElapsedMillis = scoringMillis.getValue(solver)[0],
            )
        }
        return DynamicTrendSolverHistoricalBakeoffResult(
            protocolVersion = "$PROTOCOL_VERSION|validation=${validationPolicy.protocolVersion}",
            v1Observations = v1Results,
            v1ValidationSummary = v1Summary,
            v1PredictiveMetrics = v1Metrics,
            v1Verdict = DynamicCapabilityVerdictPolicy.verdict(v1Summary, validationPolicy),
            v1FitElapsedMillis = v1FitMillis,
            v1PredictiveScoringElapsedMillis = v1ScoreMillis,
            chronologicalFitCount = fitCount,
            candidates = candidates,
        )
    }

    private fun score(
        fit: dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit,
        heldOut: List<DynamicResistanceEvidence>,
        sessionId: String,
        training: List<DynamicResistanceEvidence>,
        horizon: Instant,
        benchmark: Double?,
    ): List<DynamicHeldOutEvaluation> {
        val predictive = DynamicDemonstrationPredictiveEvaluator(v1Model)
        return heldOut.map { observation ->
            val reps = observation.repetitions.toDouble()
            val observed = observation.resistance.value
            val distribution = predictive.evaluate(
                fit,
                reps,
                observed,
                validationPolicy.predictiveLowerProbability,
                validationPolicy.predictiveUpperProbability,
            )
            val frontier = requireNotNull(v1Model.predictFrontier(fit, reps).summary)
            DynamicHeldOutEvaluation(
                sessionId = sessionId,
                observationId = observation.observationId,
                heldOutAt = observation.completedAt,
                repetitions = observation.repetitions,
                observedResistanceKg = observed,
                status = DynamicHeldOutStatus.EVALUABLE,
                trainingObservationIds = training.map { it.observationId },
                trainingSessionIds = training.map { it.sessionId }.distinct(),
                trainingEvidenceThrough = horizon,
                referenceRepetitions = fit.referenceRepetitions,
                candidatePredictive = distribution,
                frontierAtOrAboveObservedProbability = frontierAtOrAboveObservedProbability(fit, reps, observed),
                benchmarkLatestResistanceAnchorKg = benchmark,
                candidateFrontierAtRepetitions = frontier,
                candidateCrpsLogResistance = predictive.crpsLogResistance(fit, reps, observed),
            )
        }
    }

    private fun predictiveMetrics(observations: List<DynamicHeldOutEvaluation>): DynamicTrendSolverPredictiveMetrics {
        val evaluable = observations.filter { it.status == DynamicHeldOutStatus.EVALUABLE && it.candidatePredictive != null }
        val signed = evaluable.map { ln(it.observedResistanceKg / requireNotNull(it.candidatePredictive).p50ResistanceKg) }
        val wis = evaluable.map {
            val p = requireNotNull(it.candidatePredictive)
            PrequentialWeightedIntervalScore.score(
                it.observedResistanceKg,
                p.p05ResistanceKg,
                p.p50ResistanceKg,
                p.p95ResistanceKg,
            )
        }
        val distribution = DynamicCandidateDistributionMetrics(
            evaluableCount = evaluable.size,
            modelFailureRate = if (observations.isEmpty()) 0.0 else
                observations.count { it.status == DynamicHeldOutStatus.MODEL_FAILURE }.toDouble() / observations.size,
            meanSignedLogResidual = signed.averageOrNull(),
            medianSignedLogResidual = signed.medianOrNull(),
            positiveResidualProportion = if (signed.isEmpty()) null else signed.count { it > 0.0 }.toDouble() / signed.size,
            coverage = evaluable.map {
                val p = requireNotNull(it.candidatePredictive)
                if (it.observedResistanceKg in p.p05ResistanceKg..p.p95ResistanceKg) 1.0 else 0.0
            }.averageOrNull(),
            pitHighRate = evaluable.map { requireNotNull(it.candidatePredictive).observedCdf }
                .let { pits -> if (pits.isEmpty()) null else pits.count { it >= 2.0 / 3.0 }.toDouble() / pits.size },
            catastrophicContradictionRate = evaluable.mapNotNull { it.frontierAtOrAboveObservedProbability }
                .let { values ->
                    if (values.isEmpty()) null else values.count {
                        it < validationPolicy.descriptiveFrontierContradictionProbability
                    }.toDouble() / values.size
                },
            meanCrpsLogResistance = evaluable.mapNotNull { it.candidateCrpsLogResistance }.averageOrNull(),
            meanPredictiveLogWidth = evaluable.map {
                val p = requireNotNull(it.candidatePredictive)
                ln(p.p95ResistanceKg / p.p05ResistanceKg)
            }.averageOrNull(),
            demonstrationMedianMaeKg = evaluable.map {
                abs(requireNotNull(it.candidatePredictive).p50ResistanceKg - it.observedResistanceKg)
            }.averageOrNull(),
            meanLogPredictiveDensity = evaluable.map { requireNotNull(it.candidatePredictive).logPredictiveDensity }.averageOrNull(),
        )
        return DynamicTrendSolverPredictiveMetrics(
            distribution = distribution,
            meanWeightedIntervalScoreLogResistance = wis.averageOrNull(),
            medianWeightedIntervalScoreLogResistance = wis.medianOrNull(),
        )
    }

    private fun frontierAtOrAboveObservedProbability(
        fit: dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit,
        repetitions: Double,
        observedResistanceKg: Double,
    ): Double {
        val x = ln(repetitions / fit.referenceRepetitions)
        val observedLog = ln(observedResistanceKg)
        return fit.posteriorNodes.sumOf { node ->
            if (node.logFrontierAtReference - node.slope * x >= observedLog) node.posteriorWeight else 0.0
        }.coerceIn(0.0, 1.0)
    }

    private fun insufficient(
        observation: DynamicResistanceEvidence,
        sessionId: String,
        training: List<DynamicResistanceEvidence>,
    ) = DynamicHeldOutEvaluation(
        sessionId = sessionId,
        observationId = observation.observationId,
        heldOutAt = observation.completedAt,
        repetitions = observation.repetitions,
        observedResistanceKg = observation.resistance.value,
        status = DynamicHeldOutStatus.INSUFFICIENT_EVIDENCE,
        trainingObservationIds = training.map { it.observationId },
        trainingSessionIds = training.map { it.sessionId }.distinct(),
        trainingEvidenceThrough = training.maxOfOrNull { it.completedAt },
        referenceRepetitions = DynamicReferenceRepSelector.select(training, v1Model.config.referenceRepPolicy),
        candidatePredictive = null,
        frontierAtOrAboveObservedProbability = null,
        benchmarkLatestResistanceAnchorKg = training.maxWithOrNull(
            compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId },
        )?.resistance?.value,
    )

    private fun modelFailure(
        observation: DynamicResistanceEvidence,
        sessionId: String,
        training: List<DynamicResistanceEvidence>,
        reference: Double,
        benchmark: Double?,
        reason: String,
    ) = DynamicHeldOutEvaluation(
        sessionId = sessionId,
        observationId = observation.observationId,
        heldOutAt = observation.completedAt,
        repetitions = observation.repetitions,
        observedResistanceKg = observation.resistance.value,
        status = DynamicHeldOutStatus.MODEL_FAILURE,
        trainingObservationIds = training.map { it.observationId },
        trainingSessionIds = training.map { it.sessionId }.distinct(),
        trainingEvidenceThrough = training.maxOfOrNull { it.completedAt },
        referenceRepetitions = reference,
        candidatePredictive = null,
        frontierAtOrAboveObservedProbability = null,
        benchmarkLatestResistanceAnchorKg = benchmark,
        modelFailureReason = reason,
    )

    private fun CompletedSetEvidence.matches(
        profile: DynamicResistanceProfileSemantics,
        side: Laterality,
    ): Boolean = executionProfileVersionId == profile.executionProfileVersionId && laterality == side

    private fun List<DynamicResistanceEvidence>.sortedEvidence(): List<DynamicResistanceEvidence> =
        sortedWith(compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId })

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val ordered = sorted()
        return if (ordered.size % 2 == 1) ordered[ordered.size / 2]
        else (ordered[ordered.size / 2 - 1] + ordered[ordered.size / 2]) / 2.0
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    companion object {
        const val PROTOCOL_VERSION = "n-bio-7bx-candidate-v2-same-history-solver-bakeoff-v1"
    }
}
