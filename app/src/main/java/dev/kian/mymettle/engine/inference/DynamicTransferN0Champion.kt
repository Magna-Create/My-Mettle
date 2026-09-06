package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
import dev.kian.mymettle.domain.inference.InferenceMathematicalModelIdentity
import dev.kian.mymettle.domain.inference.InferenceSolverIdentity
import dev.kian.mymettle.domain.inference.ModelConfigDefinition
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import dev.kian.mymettle.engine.performance.DynamicTrendDenseReferenceConfig
import java.time.Instant

/**
 * Frozen N-BIO-7F destination-only champion role.
 *
 * N0 is not a new mathematical candidate. It binds 7F evaluation to the already selected 7B.X
 * Candidate-v2 mathematics and Adaptive Sparse numerical solver so later M0 code cannot silently
 * compare itself with another destination backend. No source capability/equipment input exists on
 * this API by design.
 */
object NBio7FN0V1 {
    const val ROLE_IDENTITY = "n-bio-7f-n0-destination-only-champion-v1"
    const val EXECUTION_MODE = "shadow_developer_only"
    const val NORMAL_PRODUCT_AUTHORITY = "benchmark_v0_unchanged"

    val selectedSolverConfig: DynamicTrendAdaptiveSparseConfig = DynamicTrendAdaptiveSparseConfig(
        denseCoreConfig = DynamicTrendDenseReferenceConfig(
            mathematicalConfig = DynamicTrendFrontierV2.config,
            trendGridPoints = 17,
            trendGridPriorSdRadius = 4.0,
            approximationVersion = "candidate-v2-dense-full-v1-support-trend-grid-v1",
        ),
        retainedBasePosteriorMass = 0.9995,
        minimumRetainedBaseNodes = 64,
        maximumRetainedBaseNodes = 2_048,
        approximationVersion = "candidate-v2-base-posterior-mass-pruned-trend-grid-v1",
    )

    val mathematicalModelIdentity: InferenceMathematicalModelIdentity
        get() = selectedSolverConfig.denseCoreConfig.mathematicalModelIdentity

    val solverIdentity: InferenceSolverIdentity
        get() = selectedSolverConfig.solverIdentity
}

/**
 * Explicitly typed destination-only fit consumed by later 7F transfer evaluation.
 *
 * The wrapper does not copy posterior fields. [destinationFit] remains the complete accepted
 * Candidate-v2 joint posterior so dependence and upstream provenance stay intact.
 */
data class DynamicTransferN0Fit(
    val destinationFit: DynamicTrendFrontierFit,
    val roleIdentity: String = NBio7FN0V1.ROLE_IDENTITY,
) {
    init {
        require(roleIdentity == NBio7FN0V1.ROLE_IDENTITY) { "N0 role identity is immutable." }
        require(destinationFit.mathematicalModelIdentity == NBio7FN0V1.mathematicalModelIdentity) {
            "N0 requires the frozen Candidate-v2 mathematical identity."
        }
        require(destinationFit.solverDiagnostics.solverIdentity == NBio7FN0V1.solverIdentity) {
            "N0 requires the selected Candidate-v2 Adaptive Sparse solver."
        }
    }
}

class DynamicTransferN0Champion {
    private val selectedSolver = DynamicTrendAdaptiveSparseSolver(NBio7FN0V1.selectedSolverConfig)
    private val baseModel = DynamicStochasticFrontierModel(selectedSolver.baseConfig)

    init {
        require(selectedSolver.mathematicalModelIdentity == NBio7FN0V1.mathematicalModelIdentity)
        require(selectedSolver.solverIdentity == NBio7FN0V1.solverIdentity)
        require(baseModel.config == selectedSolver.baseConfig)
    }

    val mathematicalModelIdentity: InferenceMathematicalModelIdentity
        get() = selectedSolver.mathematicalModelIdentity

    val solverIdentity: InferenceSolverIdentity
        get() = selectedSolver.solverIdentity

    fun modelConfig(createdAt: Instant): ModelConfigDefinition = selectedSolver.modelConfig(createdAt)

    /**
     * Fits N0 using destination evidence only. Source capability is intentionally impossible to pass
     * through this boundary; M0 must remain an explicit extension around the frozen result.
     */
    fun fit(
        destinationProjection: DynamicResistanceEvidenceProjection,
        inferenceHorizon: Instant,
        configCreatedAt: Instant,
    ): DynamicTransferN0Fit {
        require(destinationProjection.policy.identity == selectedSolver.baseConfig.evidencePolicyIdentity) {
            "N0 destination evidence policy must match the frozen Candidate-v2 policy."
        }

        val frozenV1 = baseModel.fit(
            DynamicCapabilityFitRequest(
                projection = destinationProjection,
                inferenceHorizon = inferenceHorizon,
                modelConfig = baseModel.config.toModelConfig(configCreatedAt),
            ),
        )
        val destinationFit = selectedSolver.fitFromFrozenV1(
            request = DynamicCapabilityFitRequest(
                projection = destinationProjection,
                inferenceHorizon = inferenceHorizon,
                modelConfig = selectedSolver.modelConfig(configCreatedAt),
            ),
            baseFit = frozenV1,
        )
        return DynamicTransferN0Fit(destinationFit)
    }

    /** Normal accepted N0 next-independent-session projection; M0 has its own stricter domain rules. */
    fun projectToNextSession(fit: DynamicTransferN0Fit): DynamicStochasticFrontierFit =
        selectedSolver.projectToNextSession(fit.destinationFit)
}
