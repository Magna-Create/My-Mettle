package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.DynamicTrendFrontierConfig
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2

enum class CandidateV2SequentialReuseVerdict(val storageValue: String) {
    EXACT_STATE_REANCHOR_AVAILABLE("exact_state_reanchor_available"),
    FULL_INCREMENTAL_POSTERIOR_NOT_EQUIVALENT_TO_CURRENT_BATCH_REFERENCE("full_incremental_posterior_not_equivalent_to_current_batch_reference"),
}

data class CandidateV2SequentialReuseAssessment(
    val version: String,
    val stateReanchorIdentity: String,
    val currentReferenceUsesMovingRecentSessionWindow: Boolean,
    val oldestLikelihoodMayNeedRemoval: Boolean,
    val referenceRepCoordinateMayChange: Boolean,
    val nuisanceLearningRegimeMayChange: Boolean,
    val frozenV1NumericalGridMayChange: Boolean,
    val exactFactorRemovalStatePersisted: Boolean,
    val verdict: CandidateV2SequentialReuseVerdict,
    val conclusion: String,
)

/**
 * Scientific/engineering assessment of whether Candidate-v2 can be made sequential without silently
 * changing its mathematics or pretending that the generic sequential-grid fixture is equivalent.
 */
object DynamicTrendSequentialReuseAssessment {
    const val VERSION = "candidate-v2-sequential-reuse-assessment-v1"

    /**
     * Candidate-v2 uses latest-session z=0. Advancing the origin by one independent session changes
     * every previous z to z-1. The same latent line is represented exactly by c_new = c_old + g.
     */
    fun reanchorLatestLogFrontier(previousLatestLogFrontier: Double, frontierTrend: Double): Double {
        require(previousLatestLogFrontier.isFinite() && frontierTrend.isFinite())
        return previousLatestLogFrontier + frontierTrend
    }

    fun assess(config: DynamicTrendFrontierConfig = DynamicTrendFrontierV2.config): CandidateV2SequentialReuseAssessment {
        val movingWindow = config.baseConfig.recentIndependentSessionWindow > 0
        return CandidateV2SequentialReuseAssessment(
            version = VERSION,
            stateReanchorIdentity = "z_new=z_old-1;c_new=c_old+g",
            currentReferenceUsesMovingRecentSessionWindow = movingWindow,
            oldestLikelihoodMayNeedRemoval = movingWindow,
            referenceRepCoordinateMayChange = true,
            nuisanceLearningRegimeMayChange = true,
            frozenV1NumericalGridMayChange = true,
            exactFactorRemovalStatePersisted = false,
            verdict = CandidateV2SequentialReuseVerdict.FULL_INCREMENTAL_POSTERIOR_NOT_EQUIVALENT_TO_CURRENT_BATCH_REFERENCE,
            conclusion = "Candidate-v2 state re-anchoring is exact, but the current dense reference is an importance extension of a freshly rebuilt frozen-v1 rolling-window proposal. Exact online equivalence would require persisted removable likelihood factors (including dropped-window sessions), deterministic coordinate/regrid handling and learning-gate transitions. Therefore generic sequential tensor is a valid forward architecture substrate, but is not claimed equivalent to the current Candidate-v2 batch reference.",
        )
    }
}
