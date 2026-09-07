package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.DynamicTrendFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.PrequentialWeightedIntervalScore
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Frozen 7F continuous prequential scoring protocol.
 *
 * This is an evaluation protocol only. The deterministic component projection is a bounded numerical
 * approximation for CDF/quantile/CRPS diagnostics and is not part of N0/M0 mathematics, source
 * admissibility, evidence weighting, or candidate identity. Log predictive density is evaluated
 * against the full frozen N0/M0 posterior mixtures.
 */
object NBio7FPrequentialScoringV1 {
    const val PROTOCOL_ID = "n-bio-7f-n0-m0-prequential-continuous-v1"
    const val DISTRIBUTION_PROJECTION_ID = "weighted-systematic-components-k129-slack-quadrature-t9-v1"
    const val MAX_PROJECTED_COMPONENTS = 129
    const val CRPS_NOISE_POINTS = 9
    const val CENTRAL_INTERVAL_ALPHA = 0.10
}

data class DynamicTransferM0HeldOutObservation(
    val observationId: String,
    val sessionId: String,
    val completedAt: Instant,
    val repetitions: Double,
    val resistanceKg: Double,
) {
    init {
        require(observationId.isNotBlank() && sessionId.isNotBlank())
        require(repetitions.isFinite() && repetitions > 0.0)
        require(resistanceKg.isFinite() && resistanceKg > 0.0)
    }
}

/** Prediction plus outcome-dependent proper/calibration/error scores for one model. */
data class DynamicTransferContinuousPredictiveScore(
    val p05ResistanceKg: Double,
    val p50ResistanceKg: Double,
    val p95ResistanceKg: Double,
    val pit: Double,
    val logPredictiveDensity: Double,
    val negativeLogScore: Double,
    val crpsLogResistance: Double,
    val weightedIntervalScoreLogResistance: Double,
    val coverage90: Boolean,
    val intervalLogWidth: Double,
    val medianAbsoluteErrorKg: Double,
    val signedLogResidual: Double,
) {
    init {
        require(p05ResistanceKg.isFinite() && p05ResistanceKg > 0.0)
        require(p50ResistanceKg.isFinite() && p50ResistanceKg >= p05ResistanceKg)
        require(p95ResistanceKg.isFinite() && p95ResistanceKg >= p50ResistanceKg)
        require(pit.isFinite() && pit in 0.0..1.0)
        require(logPredictiveDensity.isFinite() && negativeLogScore.isFinite())
        require(abs(negativeLogScore + logPredictiveDensity) <= 1e-12)
        require(crpsLogResistance.isFinite() && crpsLogResistance >= 0.0)
        require(weightedIntervalScoreLogResistance.isFinite() && weightedIntervalScoreLogResistance >= 0.0)
        require(intervalLogWidth.isFinite() && intervalLogWidth >= 0.0)
        require(medianAbsoluteErrorKg.isFinite() && medianAbsoluteErrorKg >= 0.0)
        require(signedLogResidual.isFinite())
    }
}

enum class DynamicTransferPredictiveUnavailableReason {
    OUTSIDE_M0_DESTINATION_REPETITION_DOMAIN,
    NUMERICAL_FAILURE,
}

sealed interface DynamicTransferPredictiveScoreResult {
    data class Available(val score: DynamicTransferContinuousPredictiveScore) : DynamicTransferPredictiveScoreResult

    data class Unavailable(
        val reason: DynamicTransferPredictiveUnavailableReason,
        val detail: String,
    ) : DynamicTransferPredictiveScoreResult {
        init { require(detail.isNotBlank()) }
    }
}

/** Every lower-is-better delta is defined exactly as M0 - N0. */
data class DynamicTransferM0ScoreDelta(
    val negativeLogScore: Double,
    val crpsLogResistance: Double,
    val weightedIntervalScoreLogResistance: Double,
    val intervalLogWidth: Double,
    val medianAbsoluteErrorKg: Double,
) {
    init {
        require(
            listOf(
                negativeLogScore,
                crpsLogResistance,
                weightedIntervalScoreLogResistance,
                intervalLogWidth,
                medianAbsoluteErrorKg,
            ).all { it.isFinite() },
        )
    }

    val harmfulByNegativeLogScore: Boolean get() = negativeLogScore > 0.0
    val harmfulByCrps: Boolean get() = crpsLogResistance > 0.0
    val harmfulByWis: Boolean get() = weightedIntervalScoreLogResistance > 0.0
    val harmfulByMedianAbsoluteError: Boolean get() = medianAbsoluteErrorKg > 0.0

    companion object {
        fun between(
            n0: DynamicTransferContinuousPredictiveScore,
            m0: DynamicTransferContinuousPredictiveScore,
        ): DynamicTransferM0ScoreDelta = DynamicTransferM0ScoreDelta(
            negativeLogScore = m0.negativeLogScore - n0.negativeLogScore,
            crpsLogResistance = m0.crpsLogResistance - n0.crpsLogResistance,
            weightedIntervalScoreLogResistance =
                m0.weightedIntervalScoreLogResistance - n0.weightedIntervalScoreLogResistance,
            intervalLogWidth = m0.intervalLogWidth - n0.intervalLogWidth,
            medianAbsoluteErrorKg = m0.medianAbsoluteErrorKg - n0.medianAbsoluteErrorKg,
        )
    }
}

data class DynamicTransferM0ObservationScoreComparison(
    val observation: DynamicTransferM0HeldOutObservation,
    val n0: DynamicTransferPredictiveScoreResult,
    val m0: DynamicTransferPredictiveScoreResult,
    val deltaM0MinusN0: DynamicTransferM0ScoreDelta?,
) {
    init {
        val comparable = n0 is DynamicTransferPredictiveScoreResult.Available &&
            m0 is DynamicTransferPredictiveScoreResult.Available
        require((deltaM0MinusN0 != null) == comparable)
    }
}

data class DynamicTransferContinuousAggregate(
    val count: Int,
    val meanNegativeLogScore: Double,
    val meanCrpsLogResistance: Double,
    val meanWeightedIntervalScoreLogResistance: Double,
    val coverage90: Double,
    val meanIntervalLogWidth: Double,
    val meanMedianAbsoluteErrorKg: Double,
    val meanSignedLogResidual: Double,
) {
    init {
        require(count > 0)
        require(
            listOf(
                meanNegativeLogScore,
                meanCrpsLogResistance,
                meanWeightedIntervalScoreLogResistance,
                coverage90,
                meanIntervalLogWidth,
                meanMedianAbsoluteErrorKg,
                meanSignedLogResidual,
            ).all { it.isFinite() },
        )
        require(coverage90 in 0.0..1.0)
    }
}

data class DynamicTransferM0NegativeTransferDiagnostics(
    val comparableObservationCount: Int,
    val meanDeltaM0MinusN0: DynamicTransferM0ScoreDelta,
) {
    init { require(comparableObservationCount > 0) }

    val harmfulByNegativeLogScore: Boolean get() = meanDeltaM0MinusN0.harmfulByNegativeLogScore
    val harmfulByCrps: Boolean get() = meanDeltaM0MinusN0.harmfulByCrps
    val harmfulByWis: Boolean get() = meanDeltaM0MinusN0.harmfulByWis
    val harmfulByMedianAbsoluteError: Boolean get() = meanDeltaM0MinusN0.harmfulByMedianAbsoluteError
}

data class DynamicTransferM0PrequentialSessionScore(
    val protocolId: String,
    val distributionProjectionId: String,
    val targetSessionId: String,
    val firstObservationTime: Instant,
    val frozenAt: Instant,
    val edgeKey: DynamicTransferM0DirectedEdgeKey,
    val sourceSelectionPolicyIdentity: String,
    val sourceSelectionMode: DynamicTransferM0SourceSelectionMode,
    val comparisons: List<DynamicTransferM0ObservationScoreComparison>,
    val n0Aggregate: DynamicTransferContinuousAggregate?,
    val m0Aggregate: DynamicTransferContinuousAggregate?,
    val negativeTransferDiagnostics: DynamicTransferM0NegativeTransferDiagnostics?,
) {
    init {
        require(protocolId == NBio7FPrequentialScoringV1.PROTOCOL_ID)
        require(distributionProjectionId == NBio7FPrequentialScoringV1.DISTRIBUTION_PROJECTION_ID)
        require(targetSessionId.isNotBlank() && comparisons.isNotEmpty())
        require(frozenAt.isBefore(firstObservationTime))
        val comparable = comparisons.count { it.deltaM0MinusN0 != null }
        require((negativeTransferDiagnostics?.comparableObservationCount ?: 0) == comparable)
    }
}

/**
 * Immutable pre-outcome state for exactly one selected/direct M0 edge and its destination N0.
 * Construction is restricted to [DynamicTransferM0PrequentialScorer.freeze].
 */
class DynamicTransferM0PrequentialFreeze internal constructor(
    val frozenAt: Instant,
    val destinationSession: DynamicTransferM0DestinationSessionDescriptor,
    val sourceSelectionPolicy: DynamicTransferM0SourceSelectionPolicyDefinition,
    val edgeKey: DynamicTransferM0DirectedEdgeKey,
    val candidate: DynamicTransferM0SourceCandidateAssessment.Admissible,
    val m0Fit: DynamicTransferM0PosteriorFit,
)

/** Pure scorer. It never updates destination/source state and never consumes more than one direct edge. */
object DynamicTransferM0PrequentialScorer {
    fun freeze(
        destinationSession: DynamicTransferM0DestinationSessionDescriptor,
        m0Fit: DynamicTransferM0PosteriorFit,
        sourceSelectionDecision: DynamicTransferM0SourceSelectionDecision,
        edgeKey: DynamicTransferM0DirectedEdgeKey,
        frozenAt: Instant,
    ): DynamicTransferM0PrequentialFreeze {
        require(frozenAt.isBefore(destinationSession.firstObservationTime)) {
            "N0/M0 evaluation state must be frozen strictly before the destination session begins."
        }
        require(sourceSelectionDecision.destinationSession == destinationSession) {
            "The source-selection decision must target the exact destination session being frozen."
        }
        val policy = sourceSelectionDecision.policy
        require(!policy.frozenAt.isAfter(frozenAt)) {
            "The source-selection policy cannot be frozen after the complete N0/M0 evaluation freeze."
        }
        val candidate = candidateForScoring(sourceSelectionDecision, edgeKey)
        require(candidate.frozenSession.destinationSession == destinationSession)
        require(candidate.input.edgeKey == edgeKey)
        require(m0Fit.relationship == candidate.input.relationship) {
            "Prequential scoring must use the exact directed relationship fitted by M0."
        }
        require(m0Fit.replayInput.destination == destinationSession.destination) {
            "Prequential M0 fit must target the exact frozen destination N0 context."
        }
        val n0 = destinationSession.destination.n0.destinationFit
        val n0EvidenceThrough = requireNotNull(n0.support.lastEvidenceAt) {
            "Destination N0 requires an evidence-through timestamp for prequential scoring."
        }
        require(!n0EvidenceThrough.isAfter(frozenAt)) {
            "The complete evaluation freeze cannot precede destination N0 evidence."
        }
        require(destinationSession.sessionId !in n0.selectedSessionIds) {
            "The held-out destination session cannot already exist in its frozen N0 state."
        }
        require(!candidate.input.source.causalCutoff.evidenceThrough.isAfter(frozenAt)) {
            "The complete evaluation freeze cannot precede source evidence."
        }
        require(!candidate.input.source.causalCutoff.asOf.isAfter(frozenAt)) {
            "The complete evaluation freeze cannot precede source snapshot construction."
        }
        require(destinationSession.sessionId !in candidate.input.source.selectedSessionIds) {
            "The held-out destination session cannot exist in the frozen source state."
        }
        return DynamicTransferM0PrequentialFreeze(
            frozenAt = frozenAt,
            destinationSession = destinationSession,
            sourceSelectionPolicy = policy,
            edgeKey = edgeKey,
            candidate = candidate,
            m0Fit = m0Fit,
        )
    }

    fun scoreSession(
        frozen: DynamicTransferM0PrequentialFreeze,
        observations: List<DynamicTransferM0HeldOutObservation>,
    ): DynamicTransferM0PrequentialSessionScore {
        require(observations.isNotEmpty())
        require(observations.map { it.observationId }.distinct().size == observations.size)
        require(observations.all { it.sessionId == frozen.destinationSession.sessionId }) {
            "Prequential scoring consumes exactly one whole held-out destination session."
        }
        require(observations.all { !it.completedAt.isBefore(frozen.destinationSession.firstObservationTime) })

        val destinationObservationIds = frozen.destinationSession.destination.n0.destinationFit.selectedObservationIds.toSet()
        val m0TrainingObservationIds = frozen.m0Fit.replayInput.trainingSessions
            .flatMap { session -> session.observations.map { it.observationId } }
            .toSet()
        val sourceObservationIds = frozen.candidate.input.source.selectedObservationIds.toSet()
        observations.forEach { observation ->
            require(observation.observationId !in destinationObservationIds) {
                "Held-out observation leaked into frozen destination N0 evidence."
            }
            require(observation.observationId !in m0TrainingObservationIds) {
                "Held-out observation leaked into frozen M0 training replay."
            }
            require(observation.observationId !in sourceObservationIds) {
                "Held-out observation leaked into frozen source evidence."
            }
        }

        val ordered = observations.sortedWith(
            compareBy<DynamicTransferM0HeldOutObservation> { it.completedAt }.thenBy { it.observationId },
        )
        val comparisons = ordered.map { observation -> scoreObservation(frozen, observation) }
        val n0Scores = comparisons.mapNotNull { (it.n0 as? DynamicTransferPredictiveScoreResult.Available)?.score }
        val m0Scores = comparisons.mapNotNull { (it.m0 as? DynamicTransferPredictiveScoreResult.Available)?.score }
        val deltas = comparisons.mapNotNull { it.deltaM0MinusN0 }
        return DynamicTransferM0PrequentialSessionScore(
            protocolId = NBio7FPrequentialScoringV1.PROTOCOL_ID,
            distributionProjectionId = NBio7FPrequentialScoringV1.DISTRIBUTION_PROJECTION_ID,
            targetSessionId = frozen.destinationSession.sessionId,
            firstObservationTime = frozen.destinationSession.firstObservationTime,
            frozenAt = frozen.frozenAt,
            edgeKey = frozen.edgeKey,
            sourceSelectionPolicyIdentity = frozen.sourceSelectionPolicy.policyIdentity,
            sourceSelectionMode = frozen.sourceSelectionPolicy.mode,
            comparisons = comparisons,
            n0Aggregate = aggregate(n0Scores),
            m0Aggregate = aggregate(m0Scores),
            negativeTransferDiagnostics = deltas.takeIf { it.isNotEmpty() }?.let {
                DynamicTransferM0NegativeTransferDiagnostics(
                    comparableObservationCount = it.size,
                    meanDeltaM0MinusN0 = averageDelta(it),
                )
            },
        )
    }

    private fun scoreObservation(
        frozen: DynamicTransferM0PrequentialFreeze,
        observation: DynamicTransferM0HeldOutObservation,
    ): DynamicTransferM0ObservationScoreComparison {
        val destinationFit = frozen.destinationSession.destination.n0.destinationFit
        val n0 = numericalResult {
            val components = destinationFit.posteriorNodes.mapIndexed { index, node ->
                PredictiveComponent(
                    weight = node.posteriorWeight,
                    logFrontier = DynamicTransferM0Kernel.n0LogFrontier(
                        destinationNode = node,
                        destinationReferenceRepetitions = destinationFit.referenceRepetitions,
                        queryRepetitions = observation.repetitions,
                        destinationSessionOffset = 1.0,
                    ),
                    slackScale = node.slackScale,
                    noiseScale = node.noiseScale,
                    stableKey = "n0:$index",
                )
            }
            val y = ln(observation.resistanceKg)
            val exactLogDensity = logSumExp(
                destinationFit.posteriorNodes.map { node ->
                    if (node.posteriorWeight <= 0.0) Double.NEGATIVE_INFINITY else {
                        ln(node.posteriorWeight) + DynamicTransferM0Kernel.n0ObservationLogDensity(
                            destinationNode = node,
                            yLogResistance = y,
                            destinationReferenceRepetitions = destinationFit.referenceRepetitions,
                            repetitions = observation.repetitions,
                            destinationSessionOffset = 1.0,
                        )
                    }
                },
            )
            PredictiveMixtureScorer.score(components, observation.resistanceKg, exactLogDensity)
        }

        val m0 = if (
            observation.repetitions < destinationFit.observedRepMin ||
            observation.repetitions > destinationFit.observedRepMax
        ) {
            DynamicTransferPredictiveScoreResult.Unavailable(
                DynamicTransferPredictiveUnavailableReason.OUTSIDE_M0_DESTINATION_REPETITION_DOMAIN,
                "M0 v1 does not extrapolate destination repetition predictions; N0 remains available.",
            )
        } else {
            numericalResult {
                val prediction = DynamicTransferM0PosteriorReplay.predict(
                    fit = frozen.m0Fit,
                    input = DynamicTransferM0PredictionReplayInput(
                        predictionCutoff = frozen.frozenAt,
                        source = frozen.candidate.input.source,
                        sourceLoadAccounting = frozen.candidate.input.sourceLoadAccounting,
                        relationship = frozen.candidate.input.relationship,
                        queryRepetitions = observation.repetitions,
                        sourceReplayDependencyScope = frozen.candidate.input.replayDependencyScope,
                    ),
                )
                val components = prediction.components.map { component ->
                    PredictiveComponent(
                        weight = component.mixtureWeight,
                        logFrontier = component.logFrontier,
                        slackScale = component.destinationSlackScale,
                        noiseScale = component.destinationNoiseScale,
                        stableKey = "m0:${component.destinationNodeIndex}:${component.betaNodeIndex}:${component.sourceOriginalNodeIndex}",
                    )
                }
                val y = ln(observation.resistanceKg)
                val exactLogDensity = logSumExp(
                    frozen.m0Fit.posteriorNodes.map { posteriorNode ->
                        if (posteriorNode.posteriorWeight <= 0.0) Double.NEGATIVE_INFINITY else {
                            ln(posteriorNode.posteriorWeight) + DynamicTransferM0Kernel.m0ObservationLogDensity(
                                destinationNode = posteriorNode.destinationNode,
                                yLogResistance = y,
                                destinationReferenceRepetitions = destinationFit.referenceRepetitions,
                                repetitions = observation.repetitions,
                                destinationSessionOffset = 1.0,
                                beta = posteriorNode.betaNode.beta,
                                sourceCoreset = prediction.sourceCoreset,
                                sourceCentre = frozen.m0Fit.sourceCentre.sourceCentre,
                            )
                        }
                    },
                )
                PredictiveMixtureScorer.score(components, observation.resistanceKg, exactLogDensity)
            }
        }

        val delta = if (
            n0 is DynamicTransferPredictiveScoreResult.Available &&
            m0 is DynamicTransferPredictiveScoreResult.Available
        ) {
            DynamicTransferM0ScoreDelta.between(n0.score, m0.score)
        } else null
        return DynamicTransferM0ObservationScoreComparison(observation, n0, m0, delta)
    }

    private fun numericalResult(block: () -> DynamicTransferContinuousPredictiveScore): DynamicTransferPredictiveScoreResult =
        try {
            DynamicTransferPredictiveScoreResult.Available(block())
        } catch (failure: DynamicTransferM0ScoringNumericalException) {
            DynamicTransferPredictiveScoreResult.Unavailable(
                DynamicTransferPredictiveUnavailableReason.NUMERICAL_FAILURE,
                failure.message ?: "Predictive numerical evaluation failed.",
            )
        }

    private fun candidateForScoring(
        decision: DynamicTransferM0SourceSelectionDecision,
        edgeKey: DynamicTransferM0DirectedEdgeKey,
    ): DynamicTransferM0SourceCandidateAssessment.Admissible = when (decision) {
        is DynamicTransferM0SourceSelectionDecision.IndependentCandidates ->
            decision.candidates.singleOrNull { it.edgeKey == edgeKey }
                ?: error("Requested edge is not an admissible independently scored source candidate.")
        is DynamicTransferM0SourceSelectionDecision.SelectedSingle -> {
            require(decision.selected.edgeKey == edgeKey) {
                "Explicit-priority scoring may evaluate only the source selected before the outcome."
            }
            decision.selected
        }
        is DynamicTransferM0SourceSelectionDecision.NoAdmissibleSource ->
            error("M0 scoring is unavailable because the frozen source-selection policy admitted no source.")
    }

    private fun aggregate(scores: List<DynamicTransferContinuousPredictiveScore>): DynamicTransferContinuousAggregate? =
        scores.takeIf { it.isNotEmpty() }?.let { values ->
            DynamicTransferContinuousAggregate(
                count = values.size,
                meanNegativeLogScore = values.map { it.negativeLogScore }.average(),
                meanCrpsLogResistance = values.map { it.crpsLogResistance }.average(),
                meanWeightedIntervalScoreLogResistance = values.map { it.weightedIntervalScoreLogResistance }.average(),
                coverage90 = values.count { it.coverage90 }.toDouble() / values.size,
                meanIntervalLogWidth = values.map { it.intervalLogWidth }.average(),
                meanMedianAbsoluteErrorKg = values.map { it.medianAbsoluteErrorKg }.average(),
                meanSignedLogResidual = values.map { it.signedLogResidual }.average(),
            )
        }

    private fun averageDelta(values: List<DynamicTransferM0ScoreDelta>): DynamicTransferM0ScoreDelta =
        DynamicTransferM0ScoreDelta(
            negativeLogScore = values.map { it.negativeLogScore }.average(),
            crpsLogResistance = values.map { it.crpsLogResistance }.average(),
            weightedIntervalScoreLogResistance = values.map { it.weightedIntervalScoreLogResistance }.average(),
            intervalLogWidth = values.map { it.intervalLogWidth }.average(),
            medianAbsoluteErrorKg = values.map { it.medianAbsoluteErrorKg }.average(),
        )
}

private class DynamicTransferM0ScoringNumericalException(message: String) : IllegalStateException(message)

private data class PredictiveComponent(
    val weight: Double,
    val logFrontier: Double,
    val slackScale: Double,
    val noiseScale: Double,
    val stableKey: String,
)

/** Bounded deterministic diagnostics over a generic latent-frontier mixture. */
private object PredictiveMixtureScorer {
    private val config = NBio7FN0V1.selectedSolverConfig.denseCoreConfig.mathematicalConfig.baseConfig
    private val slackPoints = buildSlackPoints()
    private val noiseQuantiles by lazy { buildNoiseQuantiles() }

    fun score(
        components: List<PredictiveComponent>,
        observedResistanceKg: Double,
        exactLogDensity: Double,
    ): DynamicTransferContinuousPredictiveScore {
        if (!exactLogDensity.isFinite()) numericalFailure("Non-finite predictive log density.")
        val projected = project(components)
        val p05 = quantile(projected, 0.05)
        val p50 = quantile(projected, 0.50)
        val p95 = quantile(projected, 0.95)
        val pit = cdf(projected, observedResistanceKg)
        val crps = crps(projected, ln(observedResistanceKg))
        val wis = PrequentialWeightedIntervalScore.score(
            observedResistanceKg = observedResistanceKg,
            p05ResistanceKg = p05,
            p50ResistanceKg = p50,
            p95ResistanceKg = p95,
            alpha = NBio7FPrequentialScoringV1.CENTRAL_INTERVAL_ALPHA,
        )
        val score = DynamicTransferContinuousPredictiveScore(
            p05ResistanceKg = p05,
            p50ResistanceKg = p50,
            p95ResistanceKg = p95,
            pit = pit,
            logPredictiveDensity = exactLogDensity,
            negativeLogScore = -exactLogDensity,
            crpsLogResistance = crps,
            weightedIntervalScoreLogResistance = wis,
            coverage90 = observedResistanceKg in p05..p95,
            intervalLogWidth = ln(p95 / p05),
            medianAbsoluteErrorKg = abs(p50 - observedResistanceKg),
            signedLogResidual = ln(observedResistanceKg / p50),
        )
        return score
    }

    private fun project(components: List<PredictiveComponent>): List<PredictiveComponent> {
        val positive = components.filter {
            it.weight.isFinite() && it.weight > 0.0 && it.logFrontier.isFinite() &&
                it.slackScale.isFinite() && it.slackScale > 0.0 && it.noiseScale.isFinite() && it.noiseScale > 0.0
        }
        if (positive.isEmpty()) numericalFailure("Predictive mixture contains no positive finite component mass.")
        val total = positive.sumOf { it.weight }
        if (!total.isFinite() || total <= 0.0) numericalFailure("Predictive mixture mass is invalid.")
        val ordered = positive.map { it.copy(weight = it.weight / total) }
            .sortedWith(
                compareBy<PredictiveComponent> { it.logFrontier }
                    .thenBy { it.slackScale }
                    .thenBy { it.noiseScale }
                    .thenBy { it.stableKey },
            )
        if (ordered.size <= NBio7FPrequentialScoringV1.MAX_PROJECTED_COMPONENTS) return ordered

        val projected = ArrayList<PredictiveComponent>(NBio7FPrequentialScoringV1.MAX_PROJECTED_COMPONENTS)
        var index = 0
        var cumulative = ordered.first().weight
        repeat(NBio7FPrequentialScoringV1.MAX_PROJECTED_COMPONENTS) { slot ->
            val target = (slot + 0.5) / NBio7FPrequentialScoringV1.MAX_PROJECTED_COMPONENTS.toDouble()
            while (index < ordered.lastIndex && cumulative < target) {
                index += 1
                cumulative += ordered[index].weight
            }
            projected += ordered[index].copy(weight = 1.0 / NBio7FPrequentialScoringV1.MAX_PROJECTED_COMPONENTS)
        }
        return projected
    }

    private fun cdf(components: List<PredictiveComponent>, resistanceKg: Double): Double {
        val y = ln(resistanceKg)
        var cumulative = 0.0
        components.forEach { component ->
            slackPoints.forEach { slack ->
                val location = component.logFrontier - component.slackScale * slack.standardised
                val t = (y - location) / component.noiseScale
                cumulative += component.weight * slack.probability * studentTCdf(t, config.studentTDegreesOfFreedom)
            }
        }
        if (!cumulative.isFinite()) numericalFailure("Predictive CDF is non-finite.")
        return cumulative.coerceIn(0.0, 1.0)
    }

    private fun quantile(components: List<PredictiveComponent>, probability: Double): Double {
        var low = ln(config.numericalMinimumResistanceKg)
        var high = ln(config.numericalMaximumResistanceKg)
        repeat(64) {
            val middle = (low + high) / 2.0
            if (cdf(components, exp(middle)) < probability) low = middle else high = middle
        }
        return exp((low + high) / 2.0).also {
            if (!it.isFinite() || it <= 0.0) numericalFailure("Predictive quantile is invalid.")
        }
    }

    private fun crps(components: List<PredictiveComponent>, observedLogResistance: Double): Double {
        val points = ArrayList<WeightedPoint>(components.size * slackPoints.size * noiseQuantiles.size)
        components.forEach { component ->
            slackPoints.forEach { slack ->
                val location = component.logFrontier - component.slackScale * slack.standardised
                noiseQuantiles.forEach { noise ->
                    points += WeightedPoint(
                        value = location + component.noiseScale * noise,
                        weight = component.weight * slack.probability / noiseQuantiles.size,
                    )
                }
            }
        }
        val total = points.sumOf { it.weight }
        if (!total.isFinite() || total <= 0.0) numericalFailure("CRPS approximation has invalid mass.")
        val ordered = points.map { it.copy(weight = it.weight / total) }.sortedBy { it.value }
        var firstTerm = 0.0
        var cumulativeWeight = 0.0
        var cumulativeWeightedValue = 0.0
        var halfPairwiseTerm = 0.0
        ordered.forEach { point ->
            firstTerm += point.weight * abs(point.value - observedLogResistance)
            halfPairwiseTerm += point.weight * (point.value * cumulativeWeight - cumulativeWeightedValue)
            cumulativeWeight += point.weight
            cumulativeWeightedValue += point.weight * point.value
        }
        val result = max(0.0, firstTerm - halfPairwiseTerm)
        if (!result.isFinite()) numericalFailure("CRPS approximation is non-finite.")
        return result
    }

    private fun buildSlackPoints(): List<SlackPoint> {
        val width = config.slackQuadratureMaximumSd / config.slackQuadraturePoints.toDouble()
        val raw = List(config.slackQuadraturePoints) { index ->
            val z = (index + 0.5) * width
            SlackPoint(z, exp(0.5 * ln(2.0 / PI) - 0.5 * z * z + ln(width)))
        }
        val total = raw.sumOf { it.probability }
        return raw.map { it.copy(probability = it.probability / total) }
    }

    private fun buildNoiseQuantiles(): DoubleArray = DoubleArray(NBio7FPrequentialScoringV1.CRPS_NOISE_POINTS) { index ->
        val probability = (index + 0.5) / NBio7FPrequentialScoringV1.CRPS_NOISE_POINTS.toDouble()
        inverseStudentTCdf(probability, config.studentTDegreesOfFreedom)
    }

    private fun inverseStudentTCdf(probability: Double, df: Double): Double {
        var low = -50.0
        var high = 50.0
        repeat(72) {
            val middle = (low + high) / 2.0
            if (studentTCdf(middle, df) < probability) low = middle else high = middle
        }
        return (low + high) / 2.0
    }

    private fun studentTCdf(t: Double, df: Double): Double {
        if (t == 0.0) return 0.5
        if (t == Double.POSITIVE_INFINITY) return 1.0
        if (t == Double.NEGATIVE_INFINITY) return 0.0
        if (!t.isFinite() || !df.isFinite() || df <= 0.0) numericalFailure("Student-t CDF input is invalid.")
        val x = df / (df + t * t)
        val ib = regularizedBeta(x, df / 2.0, 0.5)
        return if (t > 0.0) 1.0 - 0.5 * ib else 0.5 * ib
    }

    private fun regularizedBeta(x: Double, a: Double, b: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        val logBt = logGamma(a + b) - logGamma(a) - logGamma(b) + a * ln(x) + b * ln(1.0 - x)
        val bt = exp(logBt)
        return if (x < (a + 1.0) / (a + b + 2.0)) {
            bt * betaContinuedFraction(a, b, x) / a
        } else {
            1.0 - bt * betaContinuedFraction(b, a, 1.0 - x) / b
        }.coerceIn(0.0, 1.0)
    }

    private fun betaContinuedFraction(a: Double, b: Double, x: Double): Double {
        val maxIterations = 200
        val epsilon = 3e-14
        val tiny = 1e-300
        val qab = a + b
        val qap = a + 1.0
        val qam = a - 1.0
        var c = 1.0
        var d = 1.0 - qab * x / qap
        if (abs(d) < tiny) d = tiny
        d = 1.0 / d
        var h = d
        for (m in 1..maxIterations) {
            val m2 = 2 * m
            var aa = m.toDouble() * (b - m) * x / ((qam + m2) * (a + m2))
            d = 1.0 + aa * d
            if (abs(d) < tiny) d = tiny
            c = 1.0 + aa / c
            if (abs(c) < tiny) c = tiny
            d = 1.0 / d
            h *= d * c
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
            d = 1.0 + aa * d
            if (abs(d) < tiny) d = tiny
            c = 1.0 + aa / c
            if (abs(c) < tiny) c = tiny
            d = 1.0 / d
            val delta = d * c
            h *= delta
            if (abs(delta - 1.0) < epsilon) return h
        }
        return h
    }

    private fun logGamma(value: Double): Double {
        val coefficients = doubleArrayOf(
            676.5203681218851, -1259.1392167224028, 771.32342877765313,
            -176.61502916214059, 12.507343278686905, -0.13857109526572012,
            9.9843695780195716e-6, 1.5056327351493116e-7,
        )
        if (value < 0.5) return ln(PI) - ln(kotlin.math.sin(PI * value)) - logGamma(1.0 - value)
        val shifted = value - 1.0
        var sum = 0.99999999999980993
        coefficients.forEachIndexed { index, coefficient -> sum += coefficient / (shifted + index + 1.0) }
        val t = shifted + coefficients.size - 0.5
        return 0.5 * ln(2.0 * PI) + (shifted + 0.5) * ln(t) - t + ln(sum)
    }

    private data class SlackPoint(val standardised: Double, val probability: Double)
    private data class WeightedPoint(val value: Double, val weight: Double)
}

private fun logSumExp(values: List<Double>): Double {
    val finite = values.filter { it.isFinite() }
    if (finite.isEmpty()) return Double.NEGATIVE_INFINITY
    val maximum = finite.maxOrNull()!!
    return maximum + ln(finite.sumOf { exp(it - maximum) })
}

private fun numericalFailure(message: String): Nothing = throw DynamicTransferM0ScoringNumericalException(message)
