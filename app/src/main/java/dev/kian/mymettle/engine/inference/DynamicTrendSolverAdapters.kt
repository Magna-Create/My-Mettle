package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierConfig
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.InferenceMathematicalModelIdentity
import dev.kian.mymettle.domain.inference.InferenceSolverIdentity
import dev.kian.mymettle.domain.inference.ModelConfigDefinition
import dev.kian.mymettle.engine.performance.DynamicTrendDenseReferenceModel
import dev.kian.mymettle.engine.performance.DynamicTrendFrontierModel
import java.time.Instant

/** One mathematical Candidate-v2 family may be evaluated by multiple numerical solvers. */
interface DynamicTrendCandidateV2Solver {
    val mathematicalModelIdentity: InferenceMathematicalModelIdentity
    val solverIdentity: InferenceSolverIdentity
    val baseConfig: DynamicStochasticFrontierConfig
    val nextSessionOffset: Double

    fun modelConfig(createdAt: Instant): ModelConfigDefinition

    fun fitFromFrozenV1(
        request: DynamicCapabilityFitRequest,
        baseFit: DynamicStochasticFrontierFit,
    ): DynamicTrendFrontierFit

    fun projectToNextSession(fit: DynamicTrendFrontierFit): DynamicStochasticFrontierFit
}

class DynamicTrendConditionalLaplaceSolverAdapter(
    private val model: DynamicTrendFrontierModel = DynamicTrendFrontierModel(),
) : DynamicTrendCandidateV2Solver {
    override val mathematicalModelIdentity: InferenceMathematicalModelIdentity
        get() = dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2.mathematicalIdentity(model.config)
    override val solverIdentity: InferenceSolverIdentity
        get() = dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2.conditionalLaplaceSolverIdentity
    override val baseConfig: DynamicStochasticFrontierConfig get() = model.config.baseConfig
    override val nextSessionOffset: Double get() = model.config.nextIndependentSessionOffset

    override fun modelConfig(createdAt: Instant): ModelConfigDefinition = model.config.toModelConfig(createdAt)

    override fun fitFromFrozenV1(
        request: DynamicCapabilityFitRequest,
        baseFit: DynamicStochasticFrontierFit,
    ): DynamicTrendFrontierFit = model.fitFromFrozenV1(request, baseFit)

    override fun projectToNextSession(fit: DynamicTrendFrontierFit): DynamicStochasticFrontierFit =
        model.projectToSessionOffset(fit, nextSessionOffset)
}

class DynamicTrendDenseReferenceSolverAdapter(
    private val model: DynamicTrendDenseReferenceModel = DynamicTrendDenseReferenceModel(),
) : DynamicTrendCandidateV2Solver {
    override val mathematicalModelIdentity: InferenceMathematicalModelIdentity get() = model.solverConfig.mathematicalModelIdentity
    override val solverIdentity: InferenceSolverIdentity get() = model.solverConfig.solverIdentity
    override val baseConfig: DynamicStochasticFrontierConfig get() = model.solverConfig.mathematicalConfig.baseConfig
    override val nextSessionOffset: Double get() = model.solverConfig.mathematicalConfig.nextIndependentSessionOffset

    override fun modelConfig(createdAt: Instant): ModelConfigDefinition = model.solverConfig.toModelConfig(createdAt)

    override fun fitFromFrozenV1(
        request: DynamicCapabilityFitRequest,
        baseFit: DynamicStochasticFrontierFit,
    ): DynamicTrendFrontierFit = model.fitFromFrozenV1(request, baseFit)

    override fun projectToNextSession(fit: DynamicTrendFrontierFit): DynamicStochasticFrontierFit =
        model.projectToSessionOffset(fit, nextSessionOffset)
}
