package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.inference.DynamicResistanceV3Contract
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierEvidenceV3
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierEvidenceV3
import dev.kian.mymettle.engine.inference.DynamicTrendAdaptiveSparseConfig
import dev.kian.mymettle.engine.inference.DynamicTrendAdaptiveSparseSolver
import dev.kian.mymettle.engine.inference.DynamicTrendCandidateV2Solver
import dev.kian.mymettle.engine.inference.DynamicTrendConditionalLaplaceSolverAdapter
import dev.kian.mymettle.engine.inference.DynamicTrendDenseReferenceSolverAdapter
import dev.kian.mymettle.engine.inference.DynamicTrendSolverHistoricalBakeoff
import dev.kian.mymettle.engine.inference.DynamicTrendSolverHistoricalBakeoffCorrected
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import dev.kian.mymettle.engine.performance.DynamicTrendDenseReferenceConfig
import dev.kian.mymettle.engine.performance.DynamicTrendDenseReferenceModel
import dev.kian.mymettle.engine.performance.DynamicTrendFrontierModel

/**
 * One acceptance-only wiring point for Candidate-v2 over the semantically corrected Lite baseline.
 * It changes provenance eligibility, not the Candidate-v2 equation, priors, likelihood or numerical
 * solver definitions.
 */
object NBioCorrectedCandidateV2Bundle {
    val mathematicalConfig = DynamicTrendFrontierEvidenceV3.config
    val mathematicalModelIdentity = DynamicTrendFrontierEvidenceV3.mathematicalModelIdentity
    val evidencePolicy = DynamicResistanceV3Contract.evidencePolicy
    val baseConfig = DynamicStochasticFrontierEvidenceV3.config

    private val denseConfig = DynamicTrendDenseReferenceConfig(
        mathematicalConfig = mathematicalConfig,
    )

    private val sparseConfig = DynamicTrendAdaptiveSparseConfig(
        denseCoreConfig = denseConfig,
    )

    fun denseSolver(): DynamicTrendCandidateV2Solver = DynamicTrendDenseReferenceSolverAdapter(
        DynamicTrendDenseReferenceModel(denseConfig),
    )

    fun sparseSolver(): DynamicTrendCandidateV2Solver = DynamicTrendAdaptiveSparseSolver(sparseConfig)

    fun laplaceSolver(): DynamicTrendCandidateV2Solver = DynamicTrendConditionalLaplaceSolverAdapter(
        DynamicTrendFrontierModel(mathematicalConfig),
    )

    fun historicalBakeoff(
        solvers: List<DynamicTrendCandidateV2Solver>,
    ): DynamicTrendSolverHistoricalBakeoffCorrected = DynamicTrendSolverHistoricalBakeoffCorrected(
        solvers = solvers,
        delegate = DynamicTrendSolverHistoricalBakeoff(
            solvers = solvers,
            v1Model = DynamicStochasticFrontierModel(baseConfig),
            evidencePolicy = evidencePolicy,
        ),
    )
}
