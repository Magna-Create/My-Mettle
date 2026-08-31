package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.inference.DynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitFailureReason
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitWarning
import dev.kian.mymettle.domain.inference.DynamicCapabilityModel
import dev.kian.mymettle.domain.inference.DynamicFrontierParameterPosterior
import dev.kian.mymettle.domain.inference.DynamicFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.DynamicObservationSlackPosterior
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicSlackPosteriorMass
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierConfig
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
import dev.kian.mymettle.domain.inference.InferenceComputeBackend
import dev.kian.mymettle.domain.inference.InferenceMathematicalModelIdentity
import dev.kian.mymettle.domain.inference.InferenceModelComponent
import dev.kian.mymettle.domain.inference.InferencePosteriorRepresentation
import dev.kian.mymettle.domain.inference.InferenceSolverDiagnostics
import dev.kian.mymettle.domain.inference.InferenceSolverFamily
import dev.kian.mymettle.domain.inference.InferenceSolverIdentity
import dev.kian.mymettle.domain.inference.ModelConfigDefinition
import dev.kian.mymettle.domain.inference.ModelOutputProvenance
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * High-fidelity deterministic solver for the Candidate-v2 LINEAR_TREND mathematical model.
 *
 * The frozen Candidate-v1 posterior supplies the complete deterministic c/b/sigma_u/sigma_e base
 * grid at g=0. For every base node and every configured trend-grid point this solver applies the
 * exact discrete importance identity:
 *
 *   p(c,b,sigma,g|y) ∝ p_v1(c,b,sigma|y,g=0) * p(g) * L(y|c,b,sigma,g) / L(y|c,b,sigma,g=0)
 *
 * No frozen-v1 node is pruned. The uniform trend-grid cell width is common to every g point and
 * therefore cancels during posterior normalisation. This makes the solver a deterministic dense
 * reference over the same finite base grid rather than a new biological/mathematical candidate.
 */
data class DynamicTrendDenseReferenceConfig(
    val mathematicalConfig: DynamicTrendFrontierConfig = DynamicTrendFrontierV2.config,
    val trendGridPoints: Int = 17,
    val trendGridPriorSdRadius: Double = 4.0,
    val approximationVersion: String = "candidate-v2-dense-full-v1-support-trend-grid-v1",
) {
    init {
        require(trendGridPoints >= 9 && trendGridPoints % 2 == 1)
        require(trendGridPriorSdRadius >= 3.0 && trendGridPriorSdRadius.isFinite())
        require(approximationVersion.isNotBlank())
    }

    val mathematicalModelIdentity: InferenceMathematicalModelIdentity
        get() = DynamicTrendFrontierV2.mathematicalIdentity(mathematicalConfig)

    val solverIdentity: InferenceSolverIdentity
        get() = InferenceSolverIdentity(
            solverFamily = InferenceSolverFamily.DENSE_TENSOR_REFERENCE,
            semanticVersion = "candidate-v2-dense-reference-v1",
            computeBackend = InferenceComputeBackend.KOTLIN_JVM,
            deterministicReplay = true,
            approximationDefinition = "$approximationVersion|trendPoints=$trendGridPoints|radiusSd=$trendGridPriorSdRadius",
        )

    fun toModelConfig(createdAt: Instant): ModelConfigDefinition = ModelConfigDefinition.create(
        component = InferenceModelComponent.DYNAMIC_CAPABILITY,
        modelFamily = "stochastic_frontier_session_trend",
        modelName = "candidate_v2_dense_reference_solver",
        semanticVersion = "n-bio-7bx-candidate-v2-dense-reference-v1",
        configSchemaVersion = 1,
        parameters = mapOf(
            "mathematicalModelIdentity" to mathematicalModelIdentity.identity,
            "solverIdentity" to solverIdentity.identity,
            "evidencePolicyIdentity" to mathematicalConfig.evidencePolicyIdentity,
            "trendGridPoints" to trendGridPoints.toString(),
            "trendGridPriorSdRadius" to trendGridPriorSdRadius.toString(),
            "approximationVersion" to approximationVersion,
            "contextConsumption" to mathematicalConfig.contextConsumption,
        ),
        createdAt = createdAt,
    )
}

class DynamicTrendDenseReferenceModel(
    val solverConfig: DynamicTrendDenseReferenceConfig = DynamicTrendDenseReferenceConfig(),
    private val baseModel: DynamicStochasticFrontierModel =
        DynamicStochasticFrontierModel(solverConfig.mathematicalConfig.baseConfig),
) : DynamicCapabilityModel<DynamicTrendFrontierFit> {
    override val modelVersion: String = DynamicTrendFrontierV2.MODEL_VERSION

    private val slackQuadrature = buildSlackQuadrature(solverConfig.mathematicalConfig.baseConfig)
    private val slackScratch = object : ThreadLocal<DoubleArray>() {
        override fun initialValue(): DoubleArray = DoubleArray(solverConfig.mathematicalConfig.baseConfig.slackQuadraturePoints)
    }

    override fun fit(request: DynamicCapabilityFitRequest): DynamicTrendFrontierFit {
        validateRequest(request)
        val baseConfig = solverConfig.mathematicalConfig.baseConfig.toModelConfig(request.modelConfig.createdAt)
        val baseFit = baseModel.fit(
            DynamicCapabilityFitRequest(
                projection = request.projection,
                inferenceHorizon = request.inferenceHorizon,
                modelConfig = baseConfig,
            ),
        )
        return fitFromFrozenV1(request, baseFit)
    }

    fun fitFromFrozenV1(
        request: DynamicCapabilityFitRequest,
        baseFit: DynamicStochasticFrontierFit,
    ): DynamicTrendFrontierFit {
        validateRequest(request)
        require(baseFit.executionProfileVersionId == request.projection.profile.executionProfileVersionId)
        require(baseFit.side == request.projection.side)
        require(baseFit.evidencePolicyIdentity == solverConfig.mathematicalConfig.evidencePolicyIdentity)

        val start = System.nanoTime()
        val selectedById = request.projection.evidence.associateBy { it.observationId }
        val selected = baseFit.selectedObservationIds.map { id ->
            requireNotNull(selectedById[id]) { "Frozen-v1 base fit selected missing observation $id." }
        }
        val sessionZ = baseFit.selectedSessionIds.mapIndexed { index, sessionId ->
            sessionId to (index - (baseFit.selectedSessionIds.size - 1)).toDouble()
        }.toMap()
        val sessionCounts = selected.groupingBy { it.sessionId }.eachCount()
        val observations = selected.map { evidence ->
            TrendObservation(
                evidence = evidence,
                y = ln(evidence.resistance.value),
                x = ln(evidence.repetitions / baseFit.referenceRepetitions),
                z = requireNotNull(sessionZ[evidence.sessionId]),
                sessionWeight = 1.0 / requireNotNull(sessionCounts[evidence.sessionId]).toDouble(),
            )
        }

        val learnTrend = baseFit.support.effectiveIndependentSessionCount >=
            solverConfig.mathematicalConfig.trendMinimumIndependentSessionsToLearn
        val rawNodes = if (!learnTrend) {
            baseFit.posteriorNodes.map { node ->
                RawNode(
                    c = node.logFrontierAtReference,
                    slope = node.slope,
                    trend = 0.0,
                    slackScale = node.slackScale,
                    noiseScale = node.noiseScale,
                    logWeight = if (node.posteriorWeight > 0.0) ln(node.posteriorWeight) else Double.NEGATIVE_INFINITY,
                )
            }
        } else {
            val trendGrid = trendGrid()
            val chunks = baseFit.posteriorNodes.chunked(
                max(1, (baseFit.posteriorNodes.size + FIT_PARALLELISM - 1) / FIT_PARALLELISM),
            )
            val tasks = chunks.map { chunk ->
                Callable {
                    val local = ArrayList<RawNode>(chunk.size * trendGrid.size)
                    chunk.forEach { baseNode ->
                        if (baseNode.posteriorWeight <= 0.0) return@forEach
                        val zero = logLikelihood(baseNode, 0.0, observations)
                        if (!zero.isFinite()) return@forEach
                        trendGrid.forEach { trend ->
                            val likelihood = if (trend == 0.0) zero else logLikelihood(baseNode, trend, observations)
                            if (likelihood.isFinite()) {
                                local += RawNode(
                                    c = baseNode.logFrontierAtReference,
                                    slope = baseNode.slope,
                                    trend = trend,
                                    slackScale = baseNode.slackScale,
                                    noiseScale = baseNode.noiseScale,
                                    logWeight = ln(baseNode.posteriorWeight) + normalTrendLogPrior(trend) + likelihood - zero,
                                )
                            }
                        }
                    }
                    local
                }
            }
            FIT_EXECUTOR.invokeAll(tasks).flatMap { it.get() }
        }

        val nodes = normalise(rawNodes)
        val effectiveNodeCount = 1.0 / nodes.sumOf { it.posteriorWeight * it.posteriorWeight }
        val provenance = ModelOutputProvenance(
            modelConfigId = request.modelConfig.id,
            modelManifestId = null,
            inferenceRunId = null,
            evidenceThrough = baseFit.support.lastEvidenceAt,
        )
        val frontierSummary = weightedSummary(nodes.map { WeightedValue(exp(it.logFrontierAtLatestSession), it.posteriorWeight) })
        val trendSummary = weightedSummary(nodes.map { WeightedValue(it.frontierTrend, it.posteriorWeight) })
        val trendIdentification = trendIdentification(baseFit.support.effectiveIndependentSessionCount, trendSummary)
        val topSlackNodes = nodes.sortedByDescending { it.posteriorWeight }
            .take(solverConfig.mathematicalConfig.baseConfig.slackPosteriorTopNodeCount)
        val topSlackWeight = topSlackNodes.sumOf { it.posteriorWeight }
        if (!topSlackWeight.isFinite() || topSlackWeight <= 0.0) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.DEGENERATE_POSTERIOR,
                "Dense Candidate-v2 reference has no posterior mass for slack reconstruction.",
            )
        }
        val slackIdentification = if (baseFit.slackScale.identification == DynamicParameterIdentification.FIXED_BY_CONFIG) {
            DynamicParameterIdentification.PRIOR_DOMINATED
        } else {
            DynamicParameterIdentification.PARTIALLY_LEARNED
        }
        val observationSlack = observations.map { observation ->
            inferObservationSlack(observation, topSlackNodes, topSlackWeight, slackIdentification)
        }
        val elapsed = System.nanoTime() - start

        return DynamicTrendFrontierFit(
            executionProfileVersionId = baseFit.executionProfileVersionId,
            side = baseFit.side,
            inferenceHorizon = request.inferenceHorizon,
            referenceRepetitions = baseFit.referenceRepetitions,
            modelConfigId = request.modelConfig.id,
            modelVersion = modelVersion,
            evidencePolicyIdentity = baseFit.evidencePolicyIdentity,
            support = baseFit.support,
            observedRepMin = baseFit.observedRepMin,
            observedRepMax = baseFit.observedRepMax,
            observedResistanceMinKg = baseFit.observedResistanceMinKg,
            observedResistanceMaxKg = baseFit.observedResistanceMaxKg,
            frontierAtLatestSession = PosteriorEstimate(frontierSummary, baseFit.support, provenance),
            slope = DynamicFrontierParameterPosterior(
                summary = weightedSummary(nodes.map { WeightedValue(it.slope, it.posteriorWeight) }),
                identification = baseFit.slope.identification,
                semanticUnit = baseFit.slope.semanticUnit,
            ),
            frontierTrend = DynamicFrontierParameterPosterior(
                summary = trendSummary,
                identification = trendIdentification,
                semanticUnit = "statistical log-frontier trajectory per independent-session ordinal",
            ),
            slackScale = DynamicFrontierParameterPosterior(
                summary = weightedSummary(nodes.map { WeightedValue(it.slackScale, it.posteriorWeight) }),
                identification = baseFit.slackScale.identification,
                semanticUnit = baseFit.slackScale.semanticUnit,
            ),
            noiseScale = DynamicFrontierParameterPosterior(
                summary = weightedSummary(nodes.map { WeightedValue(it.noiseScale, it.posteriorWeight) }),
                identification = baseFit.noiseScale.identification,
                semanticUnit = baseFit.noiseScale.semanticUnit,
            ),
            observationSlack = observationSlack,
            selectedObservationIds = baseFit.selectedObservationIds,
            selectedSessionIds = baseFit.selectedSessionIds,
            approximationVersion = solverConfig.approximationVersion,
            laplaceValidBasePosteriorMass = null,
            laplaceFiniteDifferenceStep = null,
            posteriorEffectiveNodeCount = effectiveNodeCount,
            warnings = buildSet {
                add("dense_reference_solver")
                add("model_development_retrospective_candidate")
                if (!learnTrend) add("frontier_trend_fixed_zero_sparse_history")
                if (trendIdentification == DynamicParameterIdentification.PRIOR_DOMINATED) add("frontier_trend_prior_dominated")
                baseFit.warnings.forEach { add("inherited_v1:${it.storageValue}") }
            },
            posteriorNodes = nodes,
            mathematicalModelIdentity = solverConfig.mathematicalModelIdentity,
            solverDiagnostics = InferenceSolverDiagnostics(
                solverIdentity = solverConfig.solverIdentity,
                posteriorRepresentation = InferencePosteriorRepresentation.WEIGHTED_DENSE_NODES,
                evaluatedNodeCount = rawNodes.size.toLong(),
                effectiveNodeCount = effectiveNodeCount,
                updateRuntimeNanos = elapsed,
                notes = setOf("complete_frozen_v1_base_support", "uniform_dense_trend_grid"),
            ),
        )
    }

    override fun predictFrontier(fit: DynamicTrendFrontierFit, repetitions: Double): PosteriorEstimate =
        predictFrontier(fit, repetitions, 0.0)

    fun predictFrontier(fit: DynamicTrendFrontierFit, repetitions: Double, sessionOffset: Double): PosteriorEstimate =
        baseModel.predictFrontier(projectToSessionOffset(fit, sessionOffset), repetitions)

    fun projectToSessionOffset(fit: DynamicTrendFrontierFit, sessionOffset: Double): DynamicStochasticFrontierFit {
        require(sessionOffset.isFinite())
        val projected = fit.posteriorNodes.map { node ->
            DynamicFrontierPosteriorNode(
                logFrontierAtReference = node.logFrontierAtLatestSession + node.frontierTrend * sessionOffset,
                slope = node.slope,
                slackScale = node.slackScale,
                noiseScale = node.noiseScale,
                posteriorWeight = node.posteriorWeight,
            )
        }
        val frontier = weightedSummary(projected.map { WeightedValue(exp(it.logFrontierAtReference), it.posteriorWeight) })
        return DynamicStochasticFrontierFit(
            executionProfileVersionId = fit.executionProfileVersionId,
            side = fit.side,
            inferenceHorizon = fit.inferenceHorizon,
            referenceRepetitions = fit.referenceRepetitions,
            modelConfigId = fit.modelConfigId,
            modelVersion = fit.modelVersion,
            evidencePolicyIdentity = fit.evidencePolicyIdentity,
            support = fit.support,
            observedRepMin = fit.observedRepMin,
            observedRepMax = fit.observedRepMax,
            observedResistanceMinKg = fit.observedResistanceMinKg,
            observedResistanceMaxKg = fit.observedResistanceMaxKg,
            frontierAtReference = PosteriorEstimate(frontier, fit.support, fit.frontierAtLatestSession.provenance),
            slope = fit.slope,
            slackScale = fit.slackScale,
            noiseScale = fit.noiseScale,
            observationSlack = fit.observationSlack,
            selectedObservationIds = fit.selectedObservationIds,
            selectedSessionIds = fit.selectedSessionIds,
            approximationVersion = fit.approximationVersion,
            warnings = setOf(DynamicCapabilityFitWarning.APPROXIMATE_POSTERIOR),
            posteriorNodes = projected,
        )
    }

    fun basePredictiveModel(): DynamicStochasticFrontierModel = baseModel

    private fun validateRequest(request: DynamicCapabilityFitRequest) {
        val expected = solverConfig.toModelConfig(request.modelConfig.createdAt)
        if (request.modelConfig.id != expected.id || request.modelConfig.canonicalConfigPayload != expected.canonicalConfigPayload) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.MODEL_CONFIG_MISMATCH,
                "Candidate-v2 dense reference requires its immutable solver config.",
            )
        }
        if (request.projection.policy.identity != solverConfig.mathematicalConfig.evidencePolicyIdentity) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.EVIDENCE_POLICY_MISMATCH,
                "Candidate-v2 dense reference received evidence from another policy identity.",
            )
        }
    }

    private fun trendGrid(): List<Double> {
        val sd = solverConfig.mathematicalConfig.trendPriorSdLogResistancePerSession
        val radius = solverConfig.trendGridPriorSdRadius * sd
        val step = 2.0 * radius / (solverConfig.trendGridPoints - 1).toDouble()
        return List(solverConfig.trendGridPoints) { index -> -radius + step * index }
    }

    private fun normalTrendLogPrior(trend: Double): Double {
        val sd = solverConfig.mathematicalConfig.trendPriorSdLogResistancePerSession
        val z = trend / sd
        return -0.5 * z * z - ln(sd) - 0.5 * ln(2.0 * PI)
    }

    private fun logLikelihood(node: DynamicFrontierPosteriorNode, trend: Double, observations: List<TrendObservation>): Double {
        val base = solverConfig.mathematicalConfig.baseConfig
        val noiseNormalisation = studentTLogNormalisation(base.studentTDegreesOfFreedom, node.noiseScale)
        var total = 0.0
        observations.forEach { observation ->
            val frontier = node.logFrontierAtReference + trend * observation.z - node.slope * observation.x
            val residual = observation.y - frontier
            total += observation.sessionWeight * marginalObservationLogDensity(
                residual = residual,
                slackScale = node.slackScale,
                noiseScale = node.noiseScale,
                noiseLogNormalisation = noiseNormalisation,
            )
        }
        return total
    }

    private fun inferObservationSlack(
        observation: TrendObservation,
        topNodes: List<DynamicTrendFrontierPosteriorNode>,
        topWeightTotal: Double,
        identification: DynamicParameterIdentification,
    ): DynamicObservationSlackPosterior {
        val base = solverConfig.mathematicalConfig.baseConfig
        val mass = ArrayList<DynamicSlackPosteriorMass>(topNodes.size * slackQuadrature.size)
        topNodes.forEach { node ->
            val frontier = node.logFrontierAtLatestSession + node.frontierTrend * observation.z - node.slope * observation.x
            val residual = observation.y - frontier
            val noiseNorm = studentTLogNormalisation(base.studentTDegreesOfFreedom, node.noiseScale)
            val localLogWeights = slackQuadrature.map { point ->
                point.logPriorMass + studentTLogDensity(
                    residual + node.slackScale * point.standardisedSlack,
                    base.studentTDegreesOfFreedom,
                    node.noiseScale,
                    noiseNorm,
                )
            }
            val normaliser = logSumExp(localLogWeights)
            val global = node.posteriorWeight / topWeightTotal
            slackQuadrature.forEachIndexed { index, point ->
                mass += DynamicSlackPosteriorMass(
                    slack = node.slackScale * point.standardisedSlack,
                    probability = global * exp(localLogWeights[index] - normaliser),
                )
            }
        }
        val total = mass.sumOf { it.probability }
        val normalised = mass.map { it.copy(probability = it.probability / total) }
        return DynamicObservationSlackPosterior(
            observationId = observation.evidence.observationId,
            summary = weightedSummary(normalised.map { WeightedValue(it.slack, it.probability) }),
            identification = identification,
            massPoints = normalised,
        )
    }

    private fun trendIdentification(sessionCount: Int, summary: PosteriorSummary): DynamicParameterIdentification {
        val model = solverConfig.mathematicalConfig
        if (sessionCount < model.trendMinimumIndependentSessionsToLearn) return DynamicParameterIdentification.FIXED_BY_CONFIG
        val posteriorSd = sqrt(max(0.0, summary.posteriorVariance))
        val fraction = posteriorSd / model.trendPriorSdLogResistancePerSession
        return when {
            sessionCount >= model.trendDataInformedMinimumIndependentSessions &&
                fraction <= model.trendDataInformedPosteriorSdFraction -> DynamicParameterIdentification.DATA_INFORMED
            fraction >= model.trendPriorDominatedPosteriorSdFraction -> DynamicParameterIdentification.PRIOR_DOMINATED
            else -> DynamicParameterIdentification.PARTIALLY_LEARNED
        }
    }

    private fun normalise(raw: List<RawNode>): List<DynamicTrendFrontierPosteriorNode> {
        val finite = raw.filter { it.logWeight.isFinite() }
        if (finite.isEmpty()) throw DynamicCapabilityFitException(
            DynamicCapabilityFitFailureReason.NON_FINITE_POSTERIOR,
            "Dense Candidate-v2 reference produced no finite posterior node.",
        )
        val maximum = finite.maxOf { it.logWeight }
        val weights = raw.map { if (it.logWeight.isFinite()) exp(it.logWeight - maximum) else 0.0 }
        val total = weights.sum()
        if (!total.isFinite() || total <= 0.0) throw DynamicCapabilityFitException(
            DynamicCapabilityFitFailureReason.NON_FINITE_POSTERIOR,
            "Dense Candidate-v2 reference posterior normalisation failed.",
        )
        return raw.mapIndexed { index, node ->
            DynamicTrendFrontierPosteriorNode(
                logFrontierAtLatestSession = node.c,
                slope = node.slope,
                frontierTrend = node.trend,
                slackScale = node.slackScale,
                noiseScale = node.noiseScale,
                posteriorWeight = weights[index] / total,
            )
        }
    }

    private fun marginalObservationLogDensity(
        residual: Double,
        slackScale: Double,
        noiseScale: Double,
        noiseLogNormalisation: Double,
    ): Double {
        val base = solverConfig.mathematicalConfig.baseConfig
        val scratch = requireNotNull(slackScratch.get())
        var maximum = Double.NEGATIVE_INFINITY
        slackQuadrature.forEachIndexed { index, point ->
            val term = point.logPriorMass + studentTLogDensity(
                residual + slackScale * point.standardisedSlack,
                base.studentTDegreesOfFreedom,
                noiseScale,
                noiseLogNormalisation,
            )
            scratch[index] = term
            if (term.isFinite() && term > maximum) maximum = term
        }
        if (!maximum.isFinite()) return Double.NEGATIVE_INFINITY
        var total = 0.0
        slackQuadrature.indices.forEach { index ->
            val term = scratch[index]
            if (term.isFinite()) total += exp(term - maximum)
        }
        return maximum + ln(total)
    }

    private fun weightedSummary(values: List<WeightedValue>): PosteriorSummary {
        require(values.isNotEmpty())
        val total = values.sumOf { it.weight }
        require(total.isFinite() && total > 0.0)
        val normalised = values.map { WeightedValue(it.value, it.weight / total) }
        val mean = normalised.sumOf { it.value * it.weight }
        val variance = normalised.sumOf { it.weight * (it.value - mean).pow(2) }
        return PosteriorSummary(
            credibleLower05 = weightedQuantile(normalised, 0.05),
            estimateMedian = weightedQuantile(normalised, 0.50),
            credibleUpper95 = weightedQuantile(normalised, 0.95),
            posteriorVariance = variance,
        )
    }

    private fun weightedQuantile(values: List<WeightedValue>, probability: Double): Double {
        val ordered = values.sortedBy { it.value }
        var cumulative = 0.0
        for (value in ordered) {
            cumulative += value.weight
            if (cumulative >= probability) return value.value
        }
        return ordered.last().value
    }

    private fun studentTLogDensity(residual: Double, df: Double, scale: Double, norm: Double): Double =
        norm - ((df + 1.0) / 2.0) * ln1p((residual / scale).pow(2) / df)

    private fun studentTLogNormalisation(df: Double, scale: Double): Double =
        logGamma((df + 1.0) / 2.0) - logGamma(df / 2.0) - 0.5 * ln(df * PI) - ln(scale)

    private fun logSumExp(values: List<Double>): Double {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return Double.NEGATIVE_INFINITY
        val maximum = finite.max()
        return maximum + ln(finite.sumOf { exp(it - maximum) })
    }

    private data class TrendObservation(
        val evidence: DynamicResistanceEvidence,
        val y: Double,
        val x: Double,
        val z: Double,
        val sessionWeight: Double,
    )
    private data class RawNode(
        val c: Double,
        val slope: Double,
        val trend: Double,
        val slackScale: Double,
        val noiseScale: Double,
        val logWeight: Double,
    )
    private data class WeightedValue(val value: Double, val weight: Double)
    private data class SlackQuadraturePoint(val standardisedSlack: Double, val logPriorMass: Double)

    companion object {
        const val FIT_PARALLELISM = 3
        private val FIT_EXECUTOR = Executors.newFixedThreadPool(FIT_PARALLELISM) { runnable ->
            Thread(runnable, "n-bio-7bx-dense-trend").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }

        private fun buildSlackQuadrature(config: dev.kian.mymettle.domain.inference.DynamicStochasticFrontierConfig): List<SlackQuadraturePoint> {
            val width = config.slackQuadratureMaximumSd / config.slackQuadraturePoints.toDouble()
            return List(config.slackQuadraturePoints) { index ->
                val z = (index + 0.5) * width
                SlackQuadraturePoint(
                    standardisedSlack = z,
                    logPriorMass = 0.5 * ln(2.0 / PI) - 0.5 * z * z + ln(width),
                )
            }
        }

        private fun logGamma(value: Double): Double {
            require(value > 0.0 && value.isFinite())
            val coefficients = doubleArrayOf(
                676.5203681218851,
                -1259.1392167224028,
                771.32342877765313,
                -176.61502916214059,
                12.507343278686905,
                -0.13857109526572012,
                9.9843695780195716e-6,
                1.5056327351493116e-7,
            )
            if (value < 0.5) return ln(PI) - ln(kotlin.math.sin(PI * value)) - logGamma(1.0 - value)
            val shifted = value - 1.0
            var x = 0.99999999999980993
            coefficients.forEachIndexed { index, coefficient -> x += coefficient / (shifted + index + 1.0) }
            val t = shifted + coefficients.size - 0.5
            return 0.5 * ln(2.0 * PI) + (shifted + 0.5) * ln(t) - t + ln(x)
        }
    }
}
