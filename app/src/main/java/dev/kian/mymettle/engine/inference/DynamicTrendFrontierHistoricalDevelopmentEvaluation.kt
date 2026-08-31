package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DynamicCapabilityCandidateVerdict
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicCapabilityValidationPolicy
import dev.kian.mymettle.domain.inference.DynamicCapabilityValidationSummary
import dev.kian.mymettle.domain.inference.DynamicCapabilityVerdictPolicy
import dev.kian.mymettle.domain.inference.DynamicFrontierParameterPosterior
import dev.kian.mymettle.domain.inference.DynamicHeldOutEvaluation
import dev.kian.mymettle.domain.inference.DynamicHeldOutStatus
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidencePolicy
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.engine.performance.DynamicReferenceRepSelector
import dev.kian.mymettle.engine.performance.DynamicResistanceEvidenceProjector
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import dev.kian.mymettle.engine.performance.DynamicTrendFrontierModel
import java.time.Instant
import kotlin.math.ln
import kotlin.system.measureTimeMillis

/** Per-held-out-session numerical information for the development Candidate-v2 extension. */
data class DynamicTrendSessionFitDiagnostic(
    val sessionId: String,
    val priorIndependentSessionCount: Int,
    val frontierTrend: DynamicFrontierParameterPosterior?,
    val laplaceValidBasePosteriorMass: Double?,
    val posteriorEffectiveNodeCount: Double?,
    val extensionElapsedMillis: Long,
    val failureReason: String?,
)

data class DynamicTrendHistoricalDevelopmentResult(
    val v1Observations: List<DynamicHeldOutEvaluation>,
    val v2Observations: List<DynamicHeldOutEvaluation>,
    val v1Summary: DynamicCapabilityValidationSummary,
    val v2Summary: DynamicCapabilityValidationSummary,
    val v1Verdict: DynamicCapabilityCandidateVerdict,
    val v2AbsoluteVerdict: DynamicCapabilityCandidateVerdict,
    val chronologicalFitCount: Int,
    val trendSessionFits: List<DynamicTrendSessionFitDiagnostic>,
    val v1FitElapsedMillis: Long,
    val v2ExtensionElapsedMillis: Long,
    val predictiveScoringElapsedMillis: Long,
)

/**
 * Same-history Candidate-v1/v2 evaluator for MODEL-DEVELOPMENT RETROSPECTIVE EVALUATION.
 *
 * It reconstructs the exact historical source-availability cutoffs used by the accepted 7B.3/4
 * validator. At every cutoff, frozen v1 is fitted exactly once; v2 reuses that complete joint
 * posterior as its deterministic proposal. This prevents a v2 comparison from doubling the most
 * expensive v1 grid fit while preserving an exact v1 comparator on the same sessions.
 */
class DynamicTrendFrontierHistoricalDevelopmentEvaluator(
    private val v1Model: DynamicStochasticFrontierModel = DynamicStochasticFrontierModel(
        DynamicTrendFrontierV2.config.baseConfig,
    ),
    private val v2Model: DynamicTrendFrontierModel = DynamicTrendFrontierModel(),
    private val evidencePolicy: DynamicResistanceEvidencePolicy = DynamicResistanceV2Contract.evidencePolicy,
    private val validationPolicy: DynamicCapabilityValidationPolicy = DynamicCapabilityValidationPolicy(),
    private val configCreatedAt: Instant = Instant.parse("2026-08-27T00:00:00Z"),
) {
    private val summarizer = DynamicResistanceRetrospectiveEvaluator(v1Model, validationPolicy, configCreatedAt)

    fun evaluate(
        profile: DynamicResistanceProfileSemantics,
        side: Laterality,
        revisions: Collection<HistoricalCompletedSetEvidenceRevision>,
    ): DynamicTrendHistoricalDevelopmentResult {
        require(v1Model.config.evidencePolicyIdentity == evidencePolicy.identity)
        require(v2Model.config.evidencePolicyIdentity == evidencePolicy.identity)
        val candidateSessionIds = revisions
            .filter { it.evidence.matches(profile, side) }
            .map { requireNotNull(it.evidence.sessionId) }
            .toSet()
        val sessions = revisions
            .filter { requireNotNull(it.evidence.sessionId) in candidateSessionIds }
            .groupBy { requireNotNull(it.evidence.sessionId) }
            .entries
            .sortedWith(
                compareBy<Map.Entry<String, List<HistoricalCompletedSetEvidenceRevision>>> { entry ->
                    entry.value.maxOf { it.sessionCompletedAt }
                }.thenBy { it.key },
            )

        val priorSessionIds = linkedSetOf<String>()
        val v1Results = mutableListOf<DynamicHeldOutEvaluation>()
        val v2Results = mutableListOf<DynamicHeldOutEvaluation>()
        val trendFits = mutableListOf<DynamicTrendSessionFitDiagnostic>()
        var fitCount = 0
        var v1FitMillis = 0L
        var v2ExtensionMillis = 0L
        var scoringMillis = 0L

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
            val trainingProjection = DynamicResistanceEvidenceProjector.project(profile, side, trainingRaw, evidencePolicy)
            val heldOutProjection = DynamicResistanceEvidenceProjector.project(profile, side, heldOutRaw, evidencePolicy)
            val pair = evaluateHeldOutSession(trainingProjection, heldOutProjection)
            if (trainingProjection.evidence.isNotEmpty() && heldOutProjection.evidence.isNotEmpty()) fitCount += 1
            v1Results += pair.v1
            v2Results += pair.v2
            pair.trendFit?.let { trendFits += it }
            v1FitMillis += pair.v1FitElapsedMillis
            v2ExtensionMillis += pair.v2ExtensionElapsedMillis
            scoringMillis += pair.predictiveScoringElapsedMillis
            priorSessionIds += sessionId
        }
        val v1Summary = summarizer.summarize(v1Results)
        val v2Summary = summarizer.summarize(v2Results)
        return DynamicTrendHistoricalDevelopmentResult(
            v1Observations = v1Results,
            v2Observations = v2Results,
            v1Summary = v1Summary,
            v2Summary = v2Summary,
            v1Verdict = DynamicCapabilityVerdictPolicy.verdict(v1Summary, validationPolicy),
            v2AbsoluteVerdict = DynamicCapabilityVerdictPolicy.verdict(v2Summary, validationPolicy),
            chronologicalFitCount = fitCount,
            trendSessionFits = trendFits,
            v1FitElapsedMillis = v1FitMillis,
            v2ExtensionElapsedMillis = v2ExtensionMillis,
            predictiveScoringElapsedMillis = scoringMillis,
        )
    }

    private fun evaluateHeldOutSession(
        trainingProjection: DynamicResistanceEvidenceProjection,
        heldOutProjection: DynamicResistanceEvidenceProjection,
    ): SessionPair {
        require(trainingProjection.profile == heldOutProjection.profile)
        require(trainingProjection.side == heldOutProjection.side)
        val training = trainingProjection.evidence.sortedEvidence()
        val heldOut = heldOutProjection.evidence.sortedEvidence()
        if (heldOut.isEmpty()) return SessionPair(emptyList(), emptyList(), null, 0L, 0L, 0L)
        val sessionIds = heldOut.map { it.sessionId }.distinct()
        require(sessionIds.size == 1)
        val sessionId = sessionIds.single()
        require(training.none { it.sessionId == sessionId })
        if (training.isEmpty()) {
            val insufficient = heldOut.map { insufficient(it, sessionId, training) }
            return SessionPair(insufficient, insufficient, null, 0L, 0L, 0L)
        }
        val reference = DynamicReferenceRepSelector.select(training, v1Model.config.referenceRepPolicy)
            ?: return SessionPair(
                heldOut.map { insufficient(it, sessionId, training) },
                heldOut.map { insufficient(it, sessionId, training) },
                null,
                0L,
                0L,
                0L,
            )
        val fixedTraining = DynamicResistanceEvidenceProjection(
            profile = trainingProjection.profile,
            side = trainingProjection.side,
            evidence = training,
            exclusions = trainingProjection.exclusions,
            referenceRepetitions = reference,
            policy = trainingProjection.policy,
        )
        val horizon = training.maxOf { it.completedAt }
        val benchmark = training.maxWithOrNull(compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId })
            ?.resistance?.value

        var v1Fit: dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit? = null
        val v1Millis = measureTimeMillis {
            try {
                v1Fit = v1Model.fit(
                    DynamicCapabilityFitRequest(
                        projection = fixedTraining,
                        inferenceHorizon = horizon,
                        modelConfig = v1Model.config.toModelConfig(configCreatedAt),
                    ),
                )
            } catch (_: DynamicCapabilityFitException) {
                // Re-run below only to preserve the typed reason in the report without hiding timing.
            }
        }
        if (v1Fit == null) {
            val failure = try {
                v1Model.fit(
                    DynamicCapabilityFitRequest(fixedTraining, horizon, v1Model.config.toModelConfig(configCreatedAt)),
                )
                error("Expected frozen-v1 failure did not reproduce.")
            } catch (failure: DynamicCapabilityFitException) {
                failure
            }
            val failed = heldOut.map { modelFailure(it, sessionId, training, reference, benchmark, failure.reason.storageValue) }
            return SessionPair(failed, failed, null, v1Millis, 0L, 0L)
        }
        val frozenV1 = requireNotNull(v1Fit)
        lateinit var v1Scored: List<DynamicHeldOutEvaluation>
        var scoring = measureTimeMillis {
            v1Scored = score(frozenV1, heldOut, sessionId, training, horizon, benchmark)
        }

        var v2Fit: dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit? = null
        var v2Failure: DynamicCapabilityFitException? = null
        val v2Millis = measureTimeMillis {
            try {
                v2Fit = v2Model.fitFromFrozenV1(
                    request = DynamicCapabilityFitRequest(
                        projection = fixedTraining,
                        inferenceHorizon = horizon,
                        modelConfig = v2Model.config.toModelConfig(configCreatedAt),
                    ),
                    baseFit = frozenV1,
                )
            } catch (failure: DynamicCapabilityFitException) {
                v2Failure = failure
            }
        }
        if (v2Fit == null) {
            val reason = requireNotNull(v2Failure).reason.storageValue
            val failed = heldOut.map { modelFailure(it, sessionId, training, reference, benchmark, reason) }
            return SessionPair(
                v1 = v1Scored,
                v2 = failed,
                trendFit = DynamicTrendSessionFitDiagnostic(
                    sessionId = sessionId,
                    priorIndependentSessionCount = training.map { it.sessionId }.distinct().size,
                    frontierTrend = null,
                    laplaceValidBasePosteriorMass = null,
                    posteriorEffectiveNodeCount = null,
                    extensionElapsedMillis = v2Millis,
                    failureReason = reason,
                ),
                v1FitElapsedMillis = v1Millis,
                v2ExtensionElapsedMillis = v2Millis,
                predictiveScoringElapsedMillis = scoring,
            )
        }
        val trendFit = requireNotNull(v2Fit)
        val nextSessionProjection = v2Model.projectToSessionOffset(trendFit, v2Model.config.nextIndependentSessionOffset)
        lateinit var v2Scored: List<DynamicHeldOutEvaluation>
        scoring += measureTimeMillis {
            v2Scored = score(nextSessionProjection, heldOut, sessionId, training, horizon, benchmark)
        }
        return SessionPair(
            v1 = v1Scored,
            v2 = v2Scored,
            trendFit = DynamicTrendSessionFitDiagnostic(
                sessionId = sessionId,
                priorIndependentSessionCount = trendFit.support.effectiveIndependentSessionCount,
                frontierTrend = trendFit.frontierTrend,
                laplaceValidBasePosteriorMass = trendFit.laplaceValidBasePosteriorMass,
                posteriorEffectiveNodeCount = trendFit.posteriorEffectiveNodeCount,
                extensionElapsedMillis = v2Millis,
                failureReason = null,
            ),
            v1FitElapsedMillis = v1Millis,
            v2ExtensionElapsedMillis = v2Millis,
            predictiveScoringElapsedMillis = scoring,
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
            val repetitions = observation.repetitions.toDouble()
            val observed = observation.resistance.value
            val prediction = predictive.evaluate(
                fit,
                repetitions,
                observed,
                validationPolicy.predictiveLowerProbability,
                validationPolicy.predictiveUpperProbability,
            )
            val frontier = requireNotNull(v1Model.predictFrontier(fit, repetitions).summary)
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
                candidatePredictive = prediction,
                frontierAtOrAboveObservedProbability = frontierAtOrAboveObservedProbability(fit, repetitions, observed),
                benchmarkLatestResistanceAnchorKg = benchmark,
                candidateFrontierAtRepetitions = frontier,
                candidateCrpsLogResistance = predictive.crpsLogResistance(fit, repetitions, observed),
            )
        }
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
        benchmarkLatestResistanceAnchorKg = training.maxWithOrNull(compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId })
            ?.resistance?.value,
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

    private fun List<DynamicResistanceEvidence>.sortedEvidence() =
        sortedWith(compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId })

    private fun CompletedSetEvidence.matches(profile: DynamicResistanceProfileSemantics, side: Laterality): Boolean =
        executionProfileVersionId == profile.executionProfileVersionId && laterality == side

    private data class SessionPair(
        val v1: List<DynamicHeldOutEvaluation>,
        val v2: List<DynamicHeldOutEvaluation>,
        val trendFit: DynamicTrendSessionFitDiagnostic?,
        val v1FitElapsedMillis: Long,
        val v2ExtensionElapsedMillis: Long,
        val predictiveScoringElapsedMillis: Long,
    )
}
