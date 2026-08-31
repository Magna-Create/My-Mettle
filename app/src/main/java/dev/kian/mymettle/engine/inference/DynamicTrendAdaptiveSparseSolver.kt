package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
import dev.kian.mymettle.domain.inference.InferenceComputeBackend
import dev.kian.mymettle.domain.inference.InferencePosteriorRepresentation
import dev.kian.mymettle.domain.inference.InferenceSolverDiagnostics
import dev.kian.mymettle.domain.inference.InferenceSolverFamily
import dev.kian.mymettle.domain.inference.InferenceSolverIdentity
import dev.kian.mymettle.domain.inference.ModelConfigDefinition
import dev.kian.mymettle.domain.inference.ModelOutputProvenance
import dev.kian.mymettle.engine.performance.DynamicTrendDenseReferenceConfig
import dev.kian.mymettle.engine.performance.DynamicTrendDenseReferenceModel
import java.time.Instant

/**
 * Same-mathematics Candidate-v2 sparse challenger.
 *
 * The approximation is intentionally narrow: preserve the dense reference trend grid and exact
 * Candidate-v2 likelihood, but remove negligible frozen-v1 posterior support before crossing the
 * base posterior with the trend grid. This avoids the scientifically weaker shortcut of uniformly
 * lowering every grid dimension.
 */
data class DynamicTrendAdaptiveSparseConfig(
    val denseCoreConfig: DynamicTrendDenseReferenceConfig = DynamicTrendDenseReferenceConfig(),
    val retainedBasePosteriorMass: Double = 0.9995,
    val minimumRetainedBaseNodes: Int = 64,
    val maximumRetainedBaseNodes: Int = 2_048,
    val approximationVersion: String = "candidate-v2-base-posterior-mass-pruned-trend-grid-v1",
) {
    init {
        require(retainedBasePosteriorMass in 0.95..1.0)
        require(minimumRetainedBaseNodes > 0)
        require(maximumRetainedBaseNodes >= minimumRetainedBaseNodes)
        require(approximationVersion.isNotBlank())
    }

    val solverIdentity: InferenceSolverIdentity
        get() = InferenceSolverIdentity(
            solverFamily = InferenceSolverFamily.ADAPTIVE_SPARSE_TENSOR,
            semanticVersion = "candidate-v2-adaptive-sparse-v1",
            computeBackend = InferenceComputeBackend.KOTLIN_JVM,
            deterministicReplay = true,
            approximationDefinition = listOf(
                approximationVersion,
                "retainedBaseMass=$retainedBasePosteriorMass",
                "minBaseNodes=$minimumRetainedBaseNodes",
                "maxBaseNodes=$maximumRetainedBaseNodes",
                "trendPoints=${denseCoreConfig.trendGridPoints}",
                "trendRadiusSd=${denseCoreConfig.trendGridPriorSdRadius}",
            ).joinToString("|"),
        )

    fun toModelConfig(createdAt: Instant): ModelConfigDefinition {
        val dense = denseCoreConfig.toModelConfig(createdAt)
        return ModelConfigDefinition.create(
            component = dense.component,
            modelFamily = "stochastic_frontier_session_trend",
            modelName = "candidate_v2_adaptive_sparse_solver",
            semanticVersion = "n-bio-7bx-candidate-v2-adaptive-sparse-v1",
            configSchemaVersion = 1,
            parameters = mapOf(
                "mathematicalModelIdentity" to denseCoreConfig.mathematicalModelIdentity.identity,
                "solverIdentity" to solverIdentity.identity,
                "denseCoreConfigId" to dense.id.value,
                "retainedBasePosteriorMass" to retainedBasePosteriorMass.toString(),
                "minimumRetainedBaseNodes" to minimumRetainedBaseNodes.toString(),
                "maximumRetainedBaseNodes" to maximumRetainedBaseNodes.toString(),
                "trendGridPoints" to denseCoreConfig.trendGridPoints.toString(),
                "trendGridPriorSdRadius" to denseCoreConfig.trendGridPriorSdRadius.toString(),
                "approximationVersion" to approximationVersion,
                "contextConsumption" to denseCoreConfig.mathematicalConfig.contextConsumption,
            ),
            createdAt = createdAt,
        )
    }
}

data class DynamicTrendAdaptiveSparseSupport(
    val originalBaseNodeCount: Int,
    val retainedBaseNodeCount: Int,
    val retainedBasePosteriorMassBeforeRenormalisation: Double,
) {
    init {
        require(originalBaseNodeCount > 0)
        require(retainedBaseNodeCount in 1..originalBaseNodeCount)
        require(retainedBasePosteriorMassBeforeRenormalisation in 0.0..1.0)
    }
}

class DynamicTrendAdaptiveSparseSolver(
    val config: DynamicTrendAdaptiveSparseConfig = DynamicTrendAdaptiveSparseConfig(),
    private val denseCore: DynamicTrendDenseReferenceModel = DynamicTrendDenseReferenceModel(config.denseCoreConfig),
) : DynamicTrendCandidateV2Solver {
    override val mathematicalModelIdentity get() = config.denseCoreConfig.mathematicalModelIdentity
    override val solverIdentity get() = config.solverIdentity
    override val baseConfig get() = config.denseCoreConfig.mathematicalConfig.baseConfig
    override val nextSessionOffset get() = config.denseCoreConfig.mathematicalConfig.nextIndependentSessionOffset

    override fun modelConfig(createdAt: Instant): ModelConfigDefinition = config.toModelConfig(createdAt)

    override fun fitFromFrozenV1(
        request: DynamicCapabilityFitRequest,
        baseFit: DynamicStochasticFrontierFit,
    ): DynamicTrendFrontierFit {
        val expected = modelConfig(request.modelConfig.createdAt)
        require(request.modelConfig.id == expected.id && request.modelConfig.canonicalConfigPayload == expected.canonicalConfigPayload) {
            "Candidate-v2 adaptive sparse solver requires its immutable solver config."
        }
        require(baseFit.evidencePolicyIdentity == baseConfig.evidencePolicyIdentity)
        val (prunedBase, support) = pruneBasePosterior(baseFit)
        val denseRequest = DynamicCapabilityFitRequest(
            projection = request.projection,
            inferenceHorizon = request.inferenceHorizon,
            modelConfig = config.denseCoreConfig.toModelConfig(request.modelConfig.createdAt),
        )
        val denseResult = denseCore.fitFromFrozenV1(denseRequest, prunedBase)
        val provenance = denseResult.frontierAtLatestSession.provenance
        return denseResult.copy(
            modelConfigId = request.modelConfig.id,
            frontierAtLatestSession = denseResult.frontierAtLatestSession.copy(
                provenance = ModelOutputProvenance(
                    modelConfigId = request.modelConfig.id,
                    modelManifestId = provenance.modelManifestId,
                    inferenceRunId = provenance.inferenceRunId,
                    evidenceThrough = provenance.evidenceThrough,
                ),
            ),
            approximationVersion = config.approximationVersion,
            warnings = denseResult.warnings + setOf(
                "adaptive_sparse_base_posterior",
                "retained_base_nodes=${support.retainedBaseNodeCount}/${support.originalBaseNodeCount}",
                "retained_base_mass=${support.retainedBasePosteriorMassBeforeRenormalisation}",
            ),
            mathematicalModelIdentity = mathematicalModelIdentity,
            solverDiagnostics = InferenceSolverDiagnostics(
                solverIdentity = solverIdentity,
                posteriorRepresentation = InferencePosteriorRepresentation.WEIGHTED_SPARSE_NODES,
                evaluatedNodeCount = denseResult.solverDiagnostics.evaluatedNodeCount,
                effectiveNodeCount = denseResult.solverDiagnostics.effectiveNodeCount,
                updateRuntimeNanos = denseResult.solverDiagnostics.updateRuntimeNanos,
                peakWorkingBytes = denseResult.solverDiagnostics.peakWorkingBytes,
                notes = denseResult.solverDiagnostics.notes + setOf(
                    "same_candidate_v2_math_as_dense_reference",
                    "base_support_pruned_before_trend_expansion",
                    "retainedBaseNodes=${support.retainedBaseNodeCount}",
                    "originalBaseNodes=${support.originalBaseNodeCount}",
                    "retainedBaseMass=${support.retainedBasePosteriorMassBeforeRenormalisation}",
                ),
            ),
        )
    }

    override fun projectToNextSession(fit: DynamicTrendFrontierFit): DynamicStochasticFrontierFit =
        denseCore.projectToSessionOffset(fit, nextSessionOffset)

    fun pruneBasePosterior(baseFit: DynamicStochasticFrontierFit): Pair<DynamicStochasticFrontierFit, DynamicTrendAdaptiveSparseSupport> {
        val ordered = baseFit.posteriorNodes.sortedWith(
            compareByDescending<DynamicFrontierPosteriorNode> { it.posteriorWeight }
                .thenBy { it.logFrontierAtReference }
                .thenBy { it.slope }
                .thenBy { it.slackScale }
                .thenBy { it.noiseScale },
        )
        val selected = ArrayList<DynamicFrontierPosteriorNode>()
        var mass = 0.0
        for (node in ordered) {
            if (selected.size >= config.maximumRetainedBaseNodes) break
            selected += node
            mass += node.posteriorWeight
            if (selected.size >= config.minimumRetainedBaseNodes && mass >= config.retainedBasePosteriorMass) break
        }
        require(selected.isNotEmpty() && mass > 0.0 && mass.isFinite())
        val normalised = selected.map { it.copy(posteriorWeight = it.posteriorWeight / mass) }
        val support = DynamicTrendAdaptiveSparseSupport(
            originalBaseNodeCount = baseFit.posteriorNodes.size,
            retainedBaseNodeCount = normalised.size,
            retainedBasePosteriorMassBeforeRenormalisation = mass.coerceIn(0.0, 1.0),
        )
        return baseFit.copy(posteriorNodes = normalised) to support
    }
}
