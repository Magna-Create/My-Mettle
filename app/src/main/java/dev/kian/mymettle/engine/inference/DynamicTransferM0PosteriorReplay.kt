package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.CapabilityTransferSource
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierPosteriorNode
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** One immutable destination observation used only by the frozen M0 likelihood-ratio replay surface. */
data class DynamicTransferM0ObservedDestinationPoint(
    val observationId: String,
    val repetitions: Double,
    val resistanceKg: Double,
) {
    init {
        require(observationId.isNotBlank())
        require(repetitions.isFinite() && repetitions > 0.0)
        require(resistanceKg.isFinite() && resistanceKg > 0.0)
    }

    val logResistance: Double
        get() = ln(resistanceKg)
}

/** Historical source state frozen before one destination independent session, or an explicit no-pair state. */
sealed interface DynamicTransferM0HistoricalPairing {
    data class Paired(
        val source: CapabilityTransferSource,
        val sourceLoadAccounting: DynamicTransferM0LoadAccountingContext,
        val relationship: DirectedDynamicTransferRelationshipDescriptor,
        val replayDependencyScope: DynamicTransferM0ReplayDependencyScope,
    ) : DynamicTransferM0HistoricalPairing

    data class Unpaired(val reason: String) : DynamicTransferM0HistoricalPairing {
        init { require(reason.isNotBlank()) }
    }
}

/**
 * One destination session in the frozen M0 training history.
 *
 * [destinationSessionOffset] is the accepted Candidate-v2 ordinal at the final N0 fit: latest is 0,
 * older sessions are -1, -2, ... . Source chronology is checked here again so replay data cannot
 * bypass the already-proven pre-session freeze contract.
 */
data class DynamicTransferM0TrainingSession(
    val sessionId: String,
    val firstObservationTime: Instant,
    val destinationSessionOffset: Int,
    val observations: List<DynamicTransferM0ObservedDestinationPoint>,
    val pairing: DynamicTransferM0HistoricalPairing,
) {
    init {
        require(sessionId.isNotBlank())
        require(observations.isNotEmpty())
        require(observations.map { it.observationId }.distinct().size == observations.size)
        when (val frozen = pairing) {
            is DynamicTransferM0HistoricalPairing.Paired -> {
                require(frozen.source.causalCutoff.evidenceThrough.isBefore(firstObservationTime)) {
                    "M0 historical source evidence must end strictly before the destination session begins."
                }
                require(!frozen.source.causalCutoff.asOf.isAfter(firstObservationTime)) {
                    "M0 historical source snapshot cannot be constructed after the destination session begins."
                }
                require(sessionId !in frozen.source.selectedSessionIds) {
                    "M0 historical source snapshot cannot contain the destination session identity."
                }
            }
            is DynamicTransferM0HistoricalPairing.Unpaired -> Unit
        }
    }
}

/** Complete immutable inputs needed to reproduce the fitted synthetic M0 posterior. */
data class DynamicTransferM0PosteriorReplayInput(
    val destination: DynamicTransferM0DestinationContext,
    val trainingSessions: List<DynamicTransferM0TrainingSession>,
    val destinationReplayDependencyScope: DynamicTransferM0ReplayDependencyScope,
) {
    init {
        require(trainingSessions.isNotEmpty())
        require(trainingSessions.map { it.sessionId }.distinct().size == trainingSessions.size)
    }
}

/** Re-prepared historical pair at the final destination reference-repetition coordinate. */
data class DynamicTransferM0PreparedTrainingPair(
    val sessionId: String,
    val firstObservationTime: Instant,
    val destinationSessionOffset: Int,
    val observations: List<DynamicTransferM0ObservedDestinationPoint>,
    val preparedEdge: DynamicTransferM0PreparedEdge,
    val replayDependencyScope: DynamicTransferM0ReplayDependencyScope,
)

data class DynamicTransferM0PosteriorNode(
    val destinationNodeIndex: Int,
    val betaNodeIndex: Int,
    val destinationNode: DynamicTrendFrontierPosteriorNode,
    val betaNode: DynamicTransferM0BetaNode,
    val logLikelihoodRatio: Double,
    val posteriorWeight: Double,
) {
    init {
        require(destinationNodeIndex >= 0 && betaNodeIndex >= 0)
        require(logLikelihoodRatio.isFinite())
        require(posteriorWeight.isFinite() && posteriorWeight >= 0.0)
    }
}

data class DynamicTransferM0PosteriorFit(
    val replayInput: DynamicTransferM0PosteriorReplayInput,
    val relationship: DirectedDynamicTransferRelationshipDescriptor,
    val sourceCentre: DynamicTransferM0TrainingSourceCentre,
    val pairedTraining: List<DynamicTransferM0PreparedTrainingPair>,
    val posteriorNodes: List<DynamicTransferM0PosteriorNode>,
    val replayDependencyScope: DynamicTransferM0ReplayDependencyScope,
    val modelConfigId: String = NBio7FM0V1.EXPECTED_MODEL_CONFIG_ID,
    val mathematicalModelIdentity: String = NBio7FM0V1.mathematicalModelIdentity.identity,
    val solverIdentity: String = NBio7FM0V1.solverIdentity.identity,
) {
    init {
        require(pairedTraining.isNotEmpty())
        require(posteriorNodes.isNotEmpty())
        require(abs(posteriorNodes.sumOf { it.posteriorWeight } - 1.0) <= 1e-10)
        require(modelConfigId == NBio7FM0V1.EXPECTED_MODEL_CONFIG_ID)
        require(mathematicalModelIdentity == NBio7FM0V1.mathematicalModelIdentity.identity)
        require(solverIdentity == NBio7FM0V1.solverIdentity.identity)
    }
}

/** Inputs required to replay one future destination prediction from a fitted M0 edge. */
data class DynamicTransferM0PredictionReplayInput(
    val predictionCutoff: Instant,
    val source: CapabilityTransferSource,
    val sourceLoadAccounting: DynamicTransferM0LoadAccountingContext,
    val relationship: DirectedDynamicTransferRelationshipDescriptor,
    val queryRepetitions: Double,
    val sourceReplayDependencyScope: DynamicTransferM0ReplayDependencyScope,
) {
    init { require(queryRepetitions.isFinite() && queryRepetitions > 0.0) }
}

/** One deterministic component of the latent-frontier/observation predictive mixture. */
data class DynamicTransferM0PredictiveComponent(
    val destinationNodeIndex: Int,
    val betaNodeIndex: Int,
    val sourceOriginalNodeIndex: Int,
    val beta: Double,
    val sourceAnchor: Double,
    val logFrontier: Double,
    val destinationSlackScale: Double,
    val destinationNoiseScale: Double,
    val mixtureWeight: Double,
) {
    init {
        require(destinationNodeIndex >= 0 && betaNodeIndex >= 0 && sourceOriginalNodeIndex >= 0)
        require(beta.isFinite() && sourceAnchor.isFinite() && logFrontier.isFinite())
        require(destinationSlackScale.isFinite() && destinationSlackScale > 0.0)
        require(destinationNoiseScale.isFinite() && destinationNoiseScale > 0.0)
        require(mixtureWeight.isFinite() && mixtureWeight >= 0.0)
    }
}

data class DynamicTransferM0Prediction(
    val fit: DynamicTransferM0PosteriorFit,
    val replayInput: DynamicTransferM0PredictionReplayInput,
    val sourceCoreset: DynamicTransferM0SourceCoreset,
    val components: List<DynamicTransferM0PredictiveComponent>,
    val replayDependencyScope: DynamicTransferM0ReplayDependencyScope,
) {
    init {
        require(components.isNotEmpty())
        require(abs(components.sumOf { it.mixtureWeight } - 1.0) <= 1e-10)
    }
}

/**
 * Frozen section-11/12 M0 posterior and prediction construction.
 *
 * This is deliberately pure and synthetic-only. It does not read repositories, persist candidates,
 * select edges, alter N0, or fit real history. The complete canonical inputs needed for exact replay
 * are retained on the returned fit/prediction objects.
 */
object DynamicTransferM0PosteriorReplay {
    fun fit(input: DynamicTransferM0PosteriorReplayInput): DynamicTransferM0PosteriorFit {
        validateDestinationTrainingCoverage(input)
        val paired = input.trainingSessions.mapNotNull { session ->
            val historical = session.pairing as? DynamicTransferM0HistoricalPairing.Paired ?: return@mapNotNull null
            val prepared = DynamicTransferM0Kernel.prepareDirectedEdge(
                source = historical.source,
                sourceLoadAccounting = historical.sourceLoadAccounting,
                destination = input.destination,
                relationship = historical.relationship,
            )
            DynamicTransferM0PreparedTrainingPair(
                sessionId = session.sessionId,
                firstObservationTime = session.firstObservationTime,
                destinationSessionOffset = session.destinationSessionOffset,
                observations = session.observations,
                preparedEdge = prepared,
                replayDependencyScope = historical.replayDependencyScope,
            )
        }
        require(paired.isNotEmpty()) { "M0 requires at least one admissible historical source pair." }
        val relationships = paired.map { it.preparedEdge.relationship }.distinct()
        require(relationships.size == 1) {
            "One fitted M0 candidate may contain only one exact directed relationship identity."
        }
        val relationship = relationships.single()
        val sourceCentre = DynamicTransferM0Kernel.freezeTrainingSourceCentre(
            paired.map { it.preparedEdge.sourceCoreset },
        )

        val destinationFit = input.destination.n0.destinationFit
        val weightedCandidates = buildList {
            destinationFit.posteriorNodes.forEachIndexed { destinationIndex, destinationNode ->
                NBio7FM0V1.betaQuadrature.forEachIndexed { betaIndex, betaNode ->
                    var delta = 0.0
                    paired.forEach { trainingPair ->
                        val observationWeight = 1.0 / trainingPair.observations.size.toDouble()
                        trainingPair.observations.forEach { observation ->
                            val n0LogDensity = DynamicTransferM0Kernel.n0ObservationLogDensity(
                                destinationNode = destinationNode,
                                yLogResistance = observation.logResistance,
                                destinationReferenceRepetitions = destinationFit.referenceRepetitions,
                                repetitions = observation.repetitions,
                                destinationSessionOffset = trainingPair.destinationSessionOffset.toDouble(),
                            )
                            val m0LogDensity = DynamicTransferM0Kernel.m0ObservationLogDensity(
                                destinationNode = destinationNode,
                                yLogResistance = observation.logResistance,
                                destinationReferenceRepetitions = destinationFit.referenceRepetitions,
                                repetitions = observation.repetitions,
                                destinationSessionOffset = trainingPair.destinationSessionOffset.toDouble(),
                                beta = betaNode.beta,
                                sourceCoreset = trainingPair.preparedEdge.sourceCoreset,
                                sourceCentre = sourceCentre.sourceCentre,
                            )
                            val increment = observationWeight * (m0LogDensity - n0LogDensity)
                            require(increment.isFinite()) { "M0 likelihood-ratio increment must remain finite." }
                            delta += increment
                        }
                    }
                    require(delta.isFinite())
                    val logWeight = if (destinationNode.posteriorWeight > 0.0) {
                        ln(destinationNode.posteriorWeight) + ln(betaNode.priorWeight) + delta
                    } else {
                        Double.NEGATIVE_INFINITY
                    }
                    add(
                        UnnormalisedPosteriorNode(
                            destinationNodeIndex = destinationIndex,
                            betaNodeIndex = betaIndex,
                            destinationNode = destinationNode,
                            betaNode = betaNode,
                            logLikelihoodRatio = delta,
                            logWeight = logWeight,
                        ),
                    )
                }
            }
        }
        val logNormaliser = logSumExp(weightedCandidates.map { it.logWeight })
        require(logNormaliser.isFinite()) { "M0 posterior normalisation failed." }
        val posteriorNodes = weightedCandidates.map { candidate ->
            DynamicTransferM0PosteriorNode(
                destinationNodeIndex = candidate.destinationNodeIndex,
                betaNodeIndex = candidate.betaNodeIndex,
                destinationNode = candidate.destinationNode,
                betaNode = candidate.betaNode,
                logLikelihoodRatio = candidate.logLikelihoodRatio,
                posteriorWeight = if (candidate.logWeight.isFinite()) exp(candidate.logWeight - logNormaliser) else 0.0,
            )
        }

        val dependencyIds = linkedSetOf<String>()
        dependencyIds += input.destinationReplayDependencyScope.sourceDependencyIds
        paired.forEach { dependencyIds += it.replayDependencyScope.sourceDependencyIds }

        return DynamicTransferM0PosteriorFit(
            replayInput = input,
            relationship = relationship,
            sourceCentre = sourceCentre,
            pairedTraining = paired,
            posteriorNodes = posteriorNodes,
            replayDependencyScope = DynamicTransferM0ReplayDependencyScope(dependencyIds),
        )
    }

    fun predict(
        fit: DynamicTransferM0PosteriorFit,
        input: DynamicTransferM0PredictionReplayInput,
    ): DynamicTransferM0Prediction {
        require(input.relationship == fit.relationship) {
            "M0 prediction must use the exact fitted directed relationship identity."
        }
        require(!input.source.causalCutoff.evidenceThrough.isAfter(input.predictionCutoff)) {
            "M0 prediction source evidence cannot extend beyond the prediction cutoff."
        }
        require(!input.source.causalCutoff.asOf.isAfter(input.predictionCutoff)) {
            "M0 prediction source snapshot cannot be constructed after the prediction cutoff."
        }

        val prepared = DynamicTransferM0Kernel.prepareDirectedEdge(
            source = input.source,
            sourceLoadAccounting = input.sourceLoadAccounting,
            destination = fit.replayInput.destination,
            relationship = input.relationship,
        )
        val components = buildList {
            fit.posteriorNodes.forEach { posteriorNode ->
                prepared.sourceCoreset.nodes.forEach { sourceNode ->
                    val sourceCovariate = sourceNode.sourceAnchor - fit.sourceCentre.sourceCentre
                    add(
                        DynamicTransferM0PredictiveComponent(
                            destinationNodeIndex = posteriorNode.destinationNodeIndex,
                            betaNodeIndex = posteriorNode.betaNodeIndex,
                            sourceOriginalNodeIndex = sourceNode.stableOriginalIndex,
                            beta = posteriorNode.betaNode.beta,
                            sourceAnchor = sourceNode.sourceAnchor,
                            logFrontier = DynamicTransferM0Kernel.m0LogFrontier(
                                destinationNode = posteriorNode.destinationNode,
                                destinationFit = fit.replayInput.destination.n0,
                                queryRepetitions = input.queryRepetitions,
                                destinationSessionOffset = 1.0,
                                beta = posteriorNode.betaNode.beta,
                                sourceCovariate = sourceCovariate,
                            ),
                            destinationSlackScale = posteriorNode.destinationNode.slackScale,
                            destinationNoiseScale = posteriorNode.destinationNode.noiseScale,
                            mixtureWeight = posteriorNode.posteriorWeight * sourceNode.node.posteriorWeight,
                        ),
                    )
                }
            }
        }
        val dependencyIds = linkedSetOf<String>()
        dependencyIds += fit.replayDependencyScope.sourceDependencyIds
        dependencyIds += input.sourceReplayDependencyScope.sourceDependencyIds
        return DynamicTransferM0Prediction(
            fit = fit,
            replayInput = input,
            sourceCoreset = prepared.sourceCoreset,
            components = components,
            replayDependencyScope = DynamicTransferM0ReplayDependencyScope(dependencyIds),
        )
    }

    fun replayPosterior(previous: DynamicTransferM0PosteriorFit): DynamicTransferM0PosteriorFit =
        fit(previous.replayInput)

    fun replayPrediction(previous: DynamicTransferM0Prediction): DynamicTransferM0Prediction =
        predict(replayPosterior(previous.fit), previous.replayInput)

    private fun validateDestinationTrainingCoverage(input: DynamicTransferM0PosteriorReplayInput) {
        val destinationFit = input.destination.n0.destinationFit
        val sessions = input.trainingSessions
        require(sessions.map { it.sessionId }.toSet() == destinationFit.selectedSessionIds.toSet()) {
            "M0 replay history must explicitly represent every selected N0 destination session, including unpaired sessions."
        }
        val observationIds = sessions.flatMap { session -> session.observations.map { it.observationId } }
        require(observationIds.distinct().size == observationIds.size)
        require(observationIds.toSet() == destinationFit.selectedObservationIds.toSet()) {
            "M0 replay history must cover the exact selected N0 destination observations."
        }
        val chronological = sessions.sortedBy { it.firstObservationTime }
        require(chronological.map { it.firstObservationTime }.distinct().size == chronological.size) {
            "M0 destination independent sessions require distinct first-observation times."
        }
        chronological.forEachIndexed { index, session ->
            val expectedOffset = index - chronological.lastIndex
            require(session.destinationSessionOffset == expectedOffset) {
                "M0 destination session offsets must follow the frozen latest-zero/older-negative Candidate-v2 coordinate."
            }
        }
    }

    private fun logSumExp(values: List<Double>): Double {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return Double.NEGATIVE_INFINITY
        val maximum = finite.maxOrNull()!!
        return maximum + ln(finite.sumOf { exp(it - maximum) })
    }

    private data class UnnormalisedPosteriorNode(
        val destinationNodeIndex: Int,
        val betaNodeIndex: Int,
        val destinationNode: DynamicTrendFrontierPosteriorNode,
        val betaNode: DynamicTransferM0BetaNode,
        val logLikelihoodRatio: Double,
        val logWeight: Double,
    )
}
