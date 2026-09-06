package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CapabilityEquipmentContext
import dev.kian.mymettle.domain.inference.CapabilityPosteriorPayload
import dev.kian.mymettle.domain.inference.CapabilityQueryDomain
import dev.kian.mymettle.domain.inference.CapabilitySourceProfileSemantics
import dev.kian.mymettle.domain.inference.CapabilityTransferFamily
import dev.kian.mymettle.domain.inference.CapabilityTransferSource
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
import dev.kian.mymettle.domain.inference.InferenceComputeBackend
import dev.kian.mymettle.domain.inference.InferenceMathematicalModelIdentity
import dev.kian.mymettle.domain.inference.InferenceModelComponent
import dev.kian.mymettle.domain.inference.InferenceSolverFamily
import dev.kian.mymettle.domain.inference.InferenceSolverIdentity
import dev.kian.mymettle.domain.inference.ModelConfigDefinition
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.pow

/** Frozen N-BIO-7F M0 directed dynamic-capability transfer challenger. */
object NBio7FM0V1 {
    const val CANDIDATE_ROLE = "directed_source_challenger"
    const val CAPABILITY_FAMILY = "dynamic_resistance"
    const val MODEL_FAMILY = "directed_dynamic_capability_transfer"
    const val MODEL_NAME = "destination_frontier_source_covariate"
    const val SEMANTIC_VERSION = "n-bio-7f-m0-directed-source-covariate-v1"
    const val MATHEMATICAL_SEMANTIC_VERSION = "m0-source-covariate-math-v1"
    const val EXPECTED_MODEL_CONFIG_ID =
        "modelcfg_sha256_f4fa3fb165873df5407da1daefcb9bce3656caa9586ecefe3b35a0ca42c79961"
    const val SOURCE_CORESET_SIZE = 17
    const val SOURCE_CORESET_IDENTITY = "joint_systematic_weighted_cdf_midpoint_k17_v1"
    const val BETA_PRIOR_SD = 0.35
    const val IDENTIFIABILITY_VARIANCE_FLOOR = 1e-12
    const val EXECUTION_MODE = "shadow_developer_only"
    const val NORMAL_PRODUCT_AUTHORITY = "benchmark_v0_unchanged"

    private const val EQUATION = "y=c+g*z-b*x+beta*q_source-u+epsilon"
    private const val SOURCE_ANCHOR =
        "S=logFrontierLatest-slope*ln(destinationReferenceReps/sourceReferenceReps)"

    val mathematicalModelIdentity = InferenceMathematicalModelIdentity(
        family = MODEL_FAMILY,
        semanticVersion = MATHEMATICAL_SEMANTIC_VERSION,
        definition = listOf(
            "destinationBase=${NBio7FN0V1.mathematicalModelIdentity.identity}",
            "equation=$EQUATION",
            "sourceAnchor=S=logFrontierLatest-slope*ln(rD/rS)",
            "sourceCenter=mean_paired_destination_sessions(E_source[S])",
            "sourceUncertainty=joint_source_node_mixture_inside_destination_likelihood",
            "betaPrior=normal(0,0.35)",
            "betaQuadrature=gauss_hermite_7_fixed",
            "n0Reuse=frozen_destination_n0_posterior_likelihood_ratio_reweight",
            "sessionWeight=equal_total_weight_per_session_v1",
            "chronology=destination_session_atomic_source_prior_sessions_only",
            "repDomain=no_extrapolation_source_anchor_or_m0_destination_prediction",
        ).joinToString(";"),
    )

    val solverIdentity = InferenceSolverIdentity(
        solverFamily = InferenceSolverFamily.SEQUENTIAL_TENSOR,
        semanticVersion = "n-bio-7f-m0-n0-posterior-gh7-source-coreset17-v1",
        computeBackend = InferenceComputeBackend.KOTLIN_JVM,
        deterministicReplay = true,
        approximationDefinition =
            "n0-posterior-likelihood-ratio|beta=gauss-hermite-7|source=joint-systematic-weighted-cdf-midpoint-k17-v1|logsumexp=true",
    )

    val betaQuadrature: List<DynamicTransferM0BetaNode> = listOf(
        DynamicTransferM0BetaNode(-1.3126539012040097, 0.0005482688559722182),
        DynamicTransferM0BetaNode(-0.8283657937570895, 0.030757123967586515),
        DynamicTransferM0BetaNode(-0.40404188815898884, 0.24012317860501273),
        DynamicTransferM0BetaNode(0.0, 0.45714285714285713),
        DynamicTransferM0BetaNode(0.40404188815898884, 0.24012317860501273),
        DynamicTransferM0BetaNode(0.8283657937570895, 0.030757123967586515),
        DynamicTransferM0BetaNode(1.3126539012040097, 0.0005482688559722182),
    ).also { nodes ->
        require(nodes.size == 7)
        require(nodes.count { it.beta == 0.0 } == 1)
        require(abs(nodes.sumOf { it.priorWeight } - 1.0) <= 1e-15)
    }

    fun modelConfig(createdAt: Instant): ModelConfigDefinition = ModelConfigDefinition.create(
        component = InferenceModelComponent.TRANSLATION,
        modelFamily = MODEL_FAMILY,
        modelName = MODEL_NAME,
        semanticVersion = SEMANTIC_VERSION,
        configSchemaVersion = 1,
        parameters = mapOf(
            "candidateRole" to CANDIDATE_ROLE,
            "capabilityFamily" to CAPABILITY_FAMILY,
            "translationMathematicalIdentity" to mathematicalModelIdentity.identity,
            "translationSolverIdentity" to solverIdentity.identity,
            "destinationBaseMathematicalIdentity" to NBio7FN0V1.mathematicalModelIdentity.identity,
            "destinationBaseModelVersion" to DynamicTrendFrontierV2.MODEL_VERSION,
            "destinationBaseSolverIdentity" to NBio7FN0V1.solverIdentity.identity,
            "equation" to EQUATION,
            "slackDistribution" to "half_normal_destination_n0",
            "noiseDistribution" to "student_t_df_5_destination_n0",
            "sessionWeighting" to "equal_independent_session;observation_weight=1/observations_in_destination_session",
            "sourceAnchor" to SOURCE_ANCHOR,
            "sourceCenter" to "mean_over_paired_destination_sessions_of_source_posterior_expected_anchor",
            "sourceCenterFreeze" to "fit_time_training_pairs_only;never_recenter_on_held_out_or_prediction_source_snapshot",
            "sourceCovariate" to "q=S-sourceCenter",
            "sourceSessionPairing" to "paired_when_admissible_prior_source_snapshot_exists;unpaired_destination_session_delta_zero",
            "sourceSnapshotCutoff" to "strictly_before_destination_session_first_observation;exclude_same_session_id",
            "sourceTrendProjection" to "none_without_source_independent_session",
            "betaPrior" to "normal(mean=0,sd=0.35)",
            "betaQuadratureRule" to "gauss_hermite_7_standard_normal_fixed_v1",
            "betaQuadratureNodes" to betaQuadrature.joinToString(",") { betaNodeString(it.beta) },
            "betaQuadratureWeights" to betaQuadrature.joinToString(",") { it.priorWeight.toString() },
            "sourcePosteriorCoreset" to SOURCE_CORESET_IDENTITY,
            "sourcePosteriorCoresetSort" to
                "sourceAnchor,logFrontierAtLatestSession,slope,frontierTrend,slackScale,noiseScale,stableOriginalIndex",
            "sourcePosteriorCoresetSize" to SOURCE_CORESET_SIZE.toString(),
            "sourcePosteriorCoresetWeighting" to
                "retain_all_normalised_if_nodes<=17;otherwise_coalesced_selection_count/17",
            "sourceUncertainty" to "marginalize_joint_source_nodes_inside_destination_likelihood",
            "n0Reuse" to "frozen_destination_n0_posterior_likelihood_ratio_reweight",
            "nestedNoTransfer" to "beta_zero_exact_n0",
            "identifiabilityFloor" to "between_session_source_expected_anchor_variance_gt_1e-12",
            "chronology" to "destination_session_atomic_freeze;source_prior_sessions_only;score_then_update",
            "sourceRepDomain" to "no_extrapolation",
            "destinationRepDomain" to "no_extrapolation_for_m0",
            "laterality" to "exact_match",
            "equipmentContext" to "resolved_exact_source_and_destination_history",
            "loadAccounting" to "stable_known_inclusive_or_added_only_per_edge_side;unknown_or_mixed_inadmissible",
            "resistanceScope" to "external_mass_dimensional_dynamic_only;device_ordinal_bodyweight_assistance_inadmissible",
            "relationshipAdmissibility" to
                "explicit_versioned_directed_relationship_required;no_implicit_name_muscle_or_equipment_family_match",
            "multipleSources" to "one_directed_edge_per_candidate;no_precision_combination;no_transitivity",
            "predictionSourceSnapshot" to "current_admissible_source_snapshot_frozen_at_destination_prediction_cutoff",
            "predictionEquation" to "logF_D=c+g-b*ln(r/rD)+beta*q_source",
            "predictionWeighting" to "fitted_destination_beta_posterior_x_current_source_joint_nodes",
            "correctionInvalidation" to "equipment_and_load_semantic_dependencies_invalidate_derived_m0_only",
            "executionMode" to EXECUTION_MODE,
            "productAuthority" to NORMAL_PRODUCT_AUTHORITY,
        ),
        createdAt = createdAt,
    ).also { config ->
        require(config.id.value == EXPECTED_MODEL_CONFIG_ID) {
            "Frozen M0 parameter map no longer reproduces its preregistered ModelConfig identity."
        }
    }

    private fun betaNodeString(value: Double): String = if (value == 0.0) "0" else value.toString()
}

data class DynamicTransferM0BetaNode(
    val beta: Double,
    val priorWeight: Double,
) {
    init {
        require(beta.isFinite())
        require(priorWeight.isFinite() && priorWeight > 0.0)
    }
}

sealed interface DynamicTransferM0LoadAccountingContext {
    data class StableKnown(
        val accounting: ExternalLoadAccounting,
        val contributingObservationIds: Set<String>,
    ) : DynamicTransferM0LoadAccountingContext {
        init {
            require(contributingObservationIds.isNotEmpty())
            require(contributingObservationIds.all { it.isNotBlank() })
        }
    }

    data class Unknown(val reason: String) : DynamicTransferM0LoadAccountingContext {
        init { require(reason.isNotBlank()) }
    }

    data class Mixed(
        val contributingObservationIds: Set<String>,
        val reason: String,
    ) : DynamicTransferM0LoadAccountingContext {
        init {
            require(contributingObservationIds.isNotEmpty())
            require(contributingObservationIds.all { it.isNotBlank() })
            require(reason.isNotBlank())
        }
    }
}

data class DirectedDynamicTransferRelationshipDescriptor(
    val relationshipId: String,
    val version: Int,
    val policyIdentity: String,
    val sourceExecutionProfileId: ExecutionProfileId,
    val sourceExecutionProfileVersionId: ExecutionProfileVersionId,
    val destinationExecutionProfileId: ExecutionProfileId,
    val destinationExecutionProfileVersionId: ExecutionProfileVersionId,
    val side: Laterality,
    val sourceEquipmentId: EquipmentId,
    val sourceEquipmentInterpretationVersion: String,
    val sourceEquipmentFactVersionIds: Set<String>,
    val destinationEquipmentId: EquipmentId,
    val destinationEquipmentInterpretationVersion: String,
    val destinationEquipmentFactVersionIds: Set<String>,
    val sourceLoadAccounting: ExternalLoadAccounting,
    val destinationLoadAccounting: ExternalLoadAccounting,
) {
    init {
        require(relationshipId.isNotBlank() && policyIdentity.isNotBlank())
        require(version > 0)
        require(sourceEquipmentInterpretationVersion.isNotBlank())
        require(destinationEquipmentInterpretationVersion.isNotBlank())
        require(sourceEquipmentFactVersionIds.all { it.isNotBlank() })
        require(destinationEquipmentFactVersionIds.all { it.isNotBlank() })
        require(sourceExecutionProfileId != destinationExecutionProfileId) {
            "M0 requires an explicitly directed cross-profile edge, not a self-edge."
        }
        require(sourceExecutionProfileVersionId != destinationExecutionProfileVersionId) {
            "M0 requires distinct source and destination profile versions."
        }
    }
}

data class DynamicTransferM0DestinationContext(
    val n0: DynamicTransferN0Fit,
    val profile: CapabilitySourceProfileSemantics,
    val equipmentContext: CapabilityEquipmentContext,
    val loadAccounting: DynamicTransferM0LoadAccountingContext,
) {
    init {
        require(profile.executionProfileVersionId == n0.destinationFit.executionProfileVersionId)
    }
}

enum class DynamicTransferM0InadmissibilityReason {
    SOURCE_NOT_DYNAMIC_EXTERNAL_MASS,
    DESTINATION_NOT_DYNAMIC_EXTERNAL_MASS,
    SOURCE_UPSTREAM_IDENTITY_MISMATCH,
    LATERALITY_MISMATCH,
    SOURCE_EQUIPMENT_UNRESOLVED,
    DESTINATION_EQUIPMENT_UNRESOLVED,
    DESTINATION_EQUIPMENT_COVERAGE_MISMATCH,
    SOURCE_LOAD_ACCOUNTING_UNKNOWN_OR_MIXED,
    DESTINATION_LOAD_ACCOUNTING_UNKNOWN_OR_MIXED,
    LOAD_ACCOUNTING_COVERAGE_MISMATCH,
    RELATIONSHIP_DIRECTION_MISMATCH,
    RELATIONSHIP_EQUIPMENT_CONTEXT_MISMATCH,
    RELATIONSHIP_LOAD_ACCOUNTING_MISMATCH,
    SOURCE_REFERENCE_REPETITIONS_OUTSIDE_DOMAIN,
}

class DynamicTransferM0InadmissibleException(
    val reason: DynamicTransferM0InadmissibilityReason,
    message: String,
) : IllegalArgumentException(message)

data class DynamicTransferM0SourceCoresetNode(
    val stableOriginalIndex: Int,
    val sourceAnchor: Double,
    val originalPosteriorWeight: Double,
    val node: DynamicTrendFrontierPosteriorNode,
) {
    init {
        require(stableOriginalIndex >= 0)
        require(sourceAnchor.isFinite())
        require(originalPosteriorWeight.isFinite() && originalPosteriorWeight >= 0.0)
    }
}

data class DynamicTransferM0SourceCoreset(
    val sourceReferenceRepetitions: Double,
    val destinationReferenceRepetitions: Double,
    val originalNodeCount: Int,
    val nodes: List<DynamicTransferM0SourceCoresetNode>,
    val algorithmIdentity: String = NBio7FM0V1.SOURCE_CORESET_IDENTITY,
) {
    init {
        require(sourceReferenceRepetitions.isFinite() && sourceReferenceRepetitions > 0.0)
        require(destinationReferenceRepetitions.isFinite() && destinationReferenceRepetitions > 0.0)
        require(originalNodeCount > 0)
        require(nodes.isNotEmpty() && nodes.size <= NBio7FM0V1.SOURCE_CORESET_SIZE)
        require(nodes.map { it.stableOriginalIndex }.distinct().size == nodes.size)
        require(abs(nodes.sumOf { it.node.posteriorWeight } - 1.0) <= 1e-12)
        require(algorithmIdentity == NBio7FM0V1.SOURCE_CORESET_IDENTITY)
    }

    val expectedAnchor: Double
        get() = nodes.sumOf { it.sourceAnchor * it.node.posteriorWeight }
}

data class DynamicTransferM0PreparedEdge(
    val source: CapabilityTransferSource,
    val destination: DynamicTransferM0DestinationContext,
    val relationship: DirectedDynamicTransferRelationshipDescriptor,
    val sourceEquipmentContext: CapabilityEquipmentContext.ResolvedSingleContext,
    val destinationEquipmentContext: CapabilityEquipmentContext.ResolvedSingleContext,
    val sourceLoadAccounting: ExternalLoadAccounting,
    val destinationLoadAccounting: ExternalLoadAccounting,
    val sourceCoreset: DynamicTransferM0SourceCoreset,
)

data class DynamicTransferM0TrainingSourceCentre(
    val sourceCentre: Double,
    val betweenSessionExpectedAnchorVariance: Double,
) {
    init {
        require(sourceCentre.isFinite())
        require(betweenSessionExpectedAnchorVariance.isFinite())
        require(betweenSessionExpectedAnchorVariance > NBio7FM0V1.IDENTIFIABILITY_VARIANCE_FLOOR)
    }
}

object DynamicTransferM0Kernel {
    fun prepareDirectedEdge(
        source: CapabilityTransferSource,
        sourceLoadAccounting: DynamicTransferM0LoadAccountingContext,
        destination: DynamicTransferM0DestinationContext,
        relationship: DirectedDynamicTransferRelationshipDescriptor,
    ): DynamicTransferM0PreparedEdge {
        requireDynamicExternalMassSource(source)
        requireDynamicExternalMassDestination(destination)
        requireFrozenSourceIdentity(source)
        if (source.side != destination.n0.destinationFit.side) {
            inadmissible(DynamicTransferM0InadmissibilityReason.LATERALITY_MISMATCH, "M0 requires exact side match.")
        }

        val sourceEquipment = source.equipmentContext as? CapabilityEquipmentContext.ResolvedSingleContext
            ?: inadmissible(
                DynamicTransferM0InadmissibilityReason.SOURCE_EQUIPMENT_UNRESOLVED,
                "M0 requires resolved historical source equipment context.",
            )
        val destinationEquipment = destination.equipmentContext as? CapabilityEquipmentContext.ResolvedSingleContext
            ?: inadmissible(
                DynamicTransferM0InadmissibilityReason.DESTINATION_EQUIPMENT_UNRESOLVED,
                "M0 requires resolved historical destination equipment context.",
            )
        if (destinationEquipment.contributingObservationIds != destination.n0.destinationFit.selectedObservationIds.toSet()) {
            inadmissible(
                DynamicTransferM0InadmissibilityReason.DESTINATION_EQUIPMENT_COVERAGE_MISMATCH,
                "Resolved destination equipment context must cover the exact N0 observations.",
            )
        }
        val sourceAccounting = stableAccounting(
            sourceLoadAccounting,
            source.selectedObservationIds.toSet(),
            DynamicTransferM0InadmissibilityReason.SOURCE_LOAD_ACCOUNTING_UNKNOWN_OR_MIXED,
        )
        val destinationAccounting = stableAccounting(
            destination.loadAccounting,
            destination.n0.destinationFit.selectedObservationIds.toSet(),
            DynamicTransferM0InadmissibilityReason.DESTINATION_LOAD_ACCOUNTING_UNKNOWN_OR_MIXED,
        )

        requireDirectedRelationship(
            source = source,
            destination = destination,
            sourceEquipment = sourceEquipment,
            destinationEquipment = destinationEquipment,
            sourceAccounting = sourceAccounting,
            destinationAccounting = destinationAccounting,
            relationship = relationship,
        )

        return DynamicTransferM0PreparedEdge(
            source = source,
            destination = destination,
            relationship = relationship,
            sourceEquipmentContext = sourceEquipment,
            destinationEquipmentContext = destinationEquipment,
            sourceLoadAccounting = sourceAccounting,
            destinationLoadAccounting = destinationAccounting,
            sourceCoreset = sourceCoreset(source, destination.n0.destinationFit.referenceRepetitions),
        )
    }

    fun sourceCoreset(
        source: CapabilityTransferSource,
        destinationReferenceRepetitions: Double,
    ): DynamicTransferM0SourceCoreset {
        requireDynamicExternalMassSource(source)
        requireFrozenSourceIdentity(source)
        val queryDomain = source.queryDomain as CapabilityQueryDomain.DynamicResistance
        if (destinationReferenceRepetitions < queryDomain.observedRepetitions.minimum ||
            destinationReferenceRepetitions > queryDomain.observedRepetitions.maximum
        ) {
            inadmissible(
                DynamicTransferM0InadmissibilityReason.SOURCE_REFERENCE_REPETITIONS_OUTSIDE_DOMAIN,
                "M0 does not extrapolate the source anchor beyond its observed repetition domain.",
            )
        }
        val payload = source.posterior as CapabilityPosteriorPayload.DynamicTrendNodes
        return buildSourceCoreset(
            nodes = payload.nodes,
            sourceReferenceRepetitions = payload.referenceRepetitions,
            destinationReferenceRepetitions = destinationReferenceRepetitions,
        )
    }

    fun buildSourceCoreset(
        nodes: List<DynamicTrendFrontierPosteriorNode>,
        sourceReferenceRepetitions: Double,
        destinationReferenceRepetitions: Double,
    ): DynamicTransferM0SourceCoreset {
        require(nodes.isNotEmpty())
        require(sourceReferenceRepetitions.isFinite() && sourceReferenceRepetitions > 0.0)
        require(destinationReferenceRepetitions.isFinite() && destinationReferenceRepetitions > 0.0)
        val totalWeight = nodes.sumOf { it.posteriorWeight }
        require(totalWeight.isFinite() && totalWeight > 0.0)

        val transformed = nodes.mapIndexed { index, node ->
            IndexedSourceNode(
                stableOriginalIndex = index,
                sourceAnchor = node.logFrontierAtLatestSession -
                    node.slope * ln(destinationReferenceRepetitions / sourceReferenceRepetitions),
                node = node,
                normalisedOriginalWeight = node.posteriorWeight / totalWeight,
            )
        }
        val selected = if (transformed.size <= NBio7FM0V1.SOURCE_CORESET_SIZE) {
            transformed.map { indexed ->
                DynamicTransferM0SourceCoresetNode(
                    stableOriginalIndex = indexed.stableOriginalIndex,
                    sourceAnchor = indexed.sourceAnchor,
                    originalPosteriorWeight = indexed.node.posteriorWeight,
                    node = indexed.node.copy(posteriorWeight = indexed.normalisedOriginalWeight),
                )
            }
        } else {
            val ordered = transformed.sortedWith(sourceNodeComparator)
            val cumulative = ArrayList<Double>(ordered.size)
            var running = 0.0
            ordered.forEach { indexed ->
                running += indexed.normalisedOriginalWeight
                cumulative += running
            }
            val selectionCounts = linkedMapOf<Int, Int>()
            for (ordinal in 0 until NBio7FM0V1.SOURCE_CORESET_SIZE) {
                val target = (ordinal + 0.5) / NBio7FM0V1.SOURCE_CORESET_SIZE.toDouble()
                val selectedIndex = cumulative.indexOfFirst { it >= target }
                    .let { if (it >= 0) it else ordered.lastIndex }
                val originalIndex = ordered[selectedIndex].stableOriginalIndex
                selectionCounts[originalIndex] = selectionCounts.getOrDefault(originalIndex, 0) + 1
            }
            selectionCounts.entries.map { (originalIndex, count) ->
                val indexed = transformed[originalIndex]
                DynamicTransferM0SourceCoresetNode(
                    stableOriginalIndex = originalIndex,
                    sourceAnchor = indexed.sourceAnchor,
                    originalPosteriorWeight = indexed.node.posteriorWeight,
                    node = indexed.node.copy(
                        posteriorWeight = count / NBio7FM0V1.SOURCE_CORESET_SIZE.toDouble(),
                    ),
                )
            }
        }

        return DynamicTransferM0SourceCoreset(
            sourceReferenceRepetitions = sourceReferenceRepetitions,
            destinationReferenceRepetitions = destinationReferenceRepetitions,
            originalNodeCount = nodes.size,
            nodes = selected,
        )
    }

    fun freezeTrainingSourceCentre(
        pairedSourceCoresets: List<DynamicTransferM0SourceCoreset>,
    ): DynamicTransferM0TrainingSourceCentre {
        require(pairedSourceCoresets.isNotEmpty())
        require(pairedSourceCoresets.map { it.destinationReferenceRepetitions }.distinct().size == 1) {
            "M0 source centre must use one frozen destination reference-repetition coordinate."
        }
        val anchors = pairedSourceCoresets.map { it.expectedAnchor }
        require(anchors.all { it.isFinite() })
        val centre = anchors.average()
        val variance = anchors.sumOf { (it - centre).pow(2) } / anchors.size.toDouble()
        if (variance <= NBio7FM0V1.IDENTIFIABILITY_VARIANCE_FLOOR) {
            throw IllegalArgumentException(
                "M0 is unavailable because paired expected source anchors have no finite between-session variation.",
            )
        }
        return DynamicTransferM0TrainingSourceCentre(centre, variance)
    }

    fun n0LogFrontier(
        destinationNode: DynamicTrendFrontierPosteriorNode,
        destinationReferenceRepetitions: Double,
        queryRepetitions: Double,
        destinationSessionOffset: Double,
    ): Double {
        require(destinationReferenceRepetitions.isFinite() && destinationReferenceRepetitions > 0.0)
        require(queryRepetitions.isFinite() && queryRepetitions > 0.0)
        require(destinationSessionOffset.isFinite())
        return destinationNode.logFrontierAtLatestSession +
            destinationNode.frontierTrend * destinationSessionOffset -
            destinationNode.slope * ln(queryRepetitions / destinationReferenceRepetitions)
    }

    fun m0LogFrontier(
        destinationNode: DynamicTrendFrontierPosteriorNode,
        destinationFit: DynamicTransferN0Fit,
        queryRepetitions: Double,
        destinationSessionOffset: Double,
        beta: Double,
        sourceCovariate: Double,
    ): Double {
        require(beta.isFinite() && sourceCovariate.isFinite())
        val fit = destinationFit.destinationFit
        if (queryRepetitions < fit.observedRepMin || queryRepetitions > fit.observedRepMax) {
            throw IllegalArgumentException("M0 v1 does not extrapolate destination repetition predictions.")
        }
        return n0LogFrontier(
            destinationNode = destinationNode,
            destinationReferenceRepetitions = fit.referenceRepetitions,
            queryRepetitions = queryRepetitions,
            destinationSessionOffset = destinationSessionOffset,
        ) + beta * sourceCovariate
    }

    fun n0ObservationLogDensity(
        destinationNode: DynamicTrendFrontierPosteriorNode,
        yLogResistance: Double,
        destinationReferenceRepetitions: Double,
        repetitions: Double,
        destinationSessionOffset: Double,
    ): Double {
        require(yLogResistance.isFinite())
        val frontier = n0LogFrontier(
            destinationNode,
            destinationReferenceRepetitions,
            repetitions,
            destinationSessionOffset,
        )
        return destinationObservationLogDensity(yLogResistance, frontier, destinationNode)
    }

    fun m0ObservationLogDensity(
        destinationNode: DynamicTrendFrontierPosteriorNode,
        yLogResistance: Double,
        destinationReferenceRepetitions: Double,
        repetitions: Double,
        destinationSessionOffset: Double,
        beta: Double,
        sourceCoreset: DynamicTransferM0SourceCoreset,
        sourceCentre: Double,
    ): Double {
        require(beta.isFinite() && sourceCentre.isFinite())
        require(sourceCoreset.nodes.isNotEmpty())
        val n0Frontier = n0LogFrontier(
            destinationNode,
            destinationReferenceRepetitions,
            repetitions,
            destinationSessionOffset,
        )
        return logSumExp(
            sourceCoreset.nodes.map { sourceNode ->
                val sourceCovariate = sourceNode.sourceAnchor - sourceCentre
                ln(sourceNode.node.posteriorWeight) + destinationObservationLogDensity(
                    yLogResistance = yLogResistance,
                    frontierLogResistance = n0Frontier + beta * sourceCovariate,
                    destinationNode = destinationNode,
                )
            },
        )
    }

    private fun requireDynamicExternalMassSource(source: CapabilityTransferSource) {
        val query = source.queryDomain as? CapabilityQueryDomain.DynamicResistance
        if (source.capabilityFamily != CapabilityTransferFamily.DYNAMIC_RESISTANCE ||
            source.profile.metricFamily != MetricFamily.DYNAMIC_RESISTANCE ||
            source.profile.resistanceSemantics != ResistanceSemantics.EXTERNAL ||
            query == null || query.outputUnit != UnitId.KILOGRAM ||
            query.observedResistanceKg.minimum <= 0.0 ||
            source.posterior !is CapabilityPosteriorPayload.DynamicTrendNodes
        ) {
            inadmissible(
                DynamicTransferM0InadmissibilityReason.SOURCE_NOT_DYNAMIC_EXTERNAL_MASS,
                "M0 source must be external, positive, mass-dimensional dynamic resistance.",
            )
        }
    }

    private fun requireDynamicExternalMassDestination(destination: DynamicTransferM0DestinationContext) {
        if (destination.profile.metricFamily != MetricFamily.DYNAMIC_RESISTANCE ||
            destination.profile.resistanceSemantics != ResistanceSemantics.EXTERNAL ||
            destination.n0.destinationFit.observedResistanceMinKg <= 0.0
        ) {
            inadmissible(
                DynamicTransferM0InadmissibilityReason.DESTINATION_NOT_DYNAMIC_EXTERNAL_MASS,
                "M0 destination must be external, positive, mass-dimensional dynamic resistance.",
            )
        }
    }

    private fun requireFrozenSourceIdentity(source: CapabilityTransferSource) {
        if (source.upstream.mathematicalModelIdentity != NBio7FN0V1.mathematicalModelIdentity ||
            source.upstream.solverIdentity != NBio7FN0V1.solverIdentity
        ) {
            inadmissible(
                DynamicTransferM0InadmissibilityReason.SOURCE_UPSTREAM_IDENTITY_MISMATCH,
                "M0 source must use the frozen Candidate-v2 Adaptive Sparse capability posterior.",
            )
        }
    }

    private fun stableAccounting(
        context: DynamicTransferM0LoadAccountingContext,
        expectedObservationIds: Set<String>,
        unknownOrMixedReason: DynamicTransferM0InadmissibilityReason,
    ): ExternalLoadAccounting {
        val stable = context as? DynamicTransferM0LoadAccountingContext.StableKnown
            ?: inadmissible(unknownOrMixedReason, "M0 requires one stable known load-accounting meaning per edge side.")
        if (stable.contributingObservationIds != expectedObservationIds) {
            inadmissible(
                DynamicTransferM0InadmissibilityReason.LOAD_ACCOUNTING_COVERAGE_MISMATCH,
                "Stable load-accounting context must cover the exact capability observations.",
            )
        }
        return stable.accounting
    }

    private fun requireDirectedRelationship(
        source: CapabilityTransferSource,
        destination: DynamicTransferM0DestinationContext,
        sourceEquipment: CapabilityEquipmentContext.ResolvedSingleContext,
        destinationEquipment: CapabilityEquipmentContext.ResolvedSingleContext,
        sourceAccounting: ExternalLoadAccounting,
        destinationAccounting: ExternalLoadAccounting,
        relationship: DirectedDynamicTransferRelationshipDescriptor,
    ) {
        if (relationship.sourceExecutionProfileId != source.profile.executionProfileId ||
            relationship.sourceExecutionProfileVersionId != source.profile.executionProfileVersionId ||
            relationship.destinationExecutionProfileId != destination.profile.executionProfileId ||
            relationship.destinationExecutionProfileVersionId != destination.profile.executionProfileVersionId ||
            relationship.side != source.side || relationship.side != destination.n0.destinationFit.side
        ) {
            inadmissible(
                DynamicTransferM0InadmissibilityReason.RELATIONSHIP_DIRECTION_MISMATCH,
                "The explicit directed relationship does not authorise this exact source -> destination edge.",
            )
        }
        if (relationship.sourceEquipmentId != sourceEquipment.equipmentId ||
            relationship.sourceEquipmentInterpretationVersion != sourceEquipment.interpretationVersion ||
            relationship.sourceEquipmentFactVersionIds != sourceEquipment.equipmentFactVersionIds ||
            relationship.destinationEquipmentId != destinationEquipment.equipmentId ||
            relationship.destinationEquipmentInterpretationVersion != destinationEquipment.interpretationVersion ||
            relationship.destinationEquipmentFactVersionIds != destinationEquipment.equipmentFactVersionIds
        ) {
            inadmissible(
                DynamicTransferM0InadmissibilityReason.RELATIONSHIP_EQUIPMENT_CONTEXT_MISMATCH,
                "The explicit relationship does not match the resolved historical equipment contexts.",
            )
        }
        if (relationship.sourceLoadAccounting != sourceAccounting ||
            relationship.destinationLoadAccounting != destinationAccounting
        ) {
            inadmissible(
                DynamicTransferM0InadmissibilityReason.RELATIONSHIP_LOAD_ACCOUNTING_MISMATCH,
                "The explicit relationship does not match canonical load-accounting semantics.",
            )
        }
    }

    private fun destinationObservationLogDensity(
        yLogResistance: Double,
        frontierLogResistance: Double,
        destinationNode: DynamicTrendFrontierPosteriorNode,
    ): Double {
        val config = NBio7FN0V1.selectedSolverConfig.denseCoreConfig.mathematicalConfig.baseConfig
        val width = config.slackQuadratureMaximumSd / config.slackQuadraturePoints.toDouble()
        val noiseNormalisation = studentTLogNormalisation(
            config.studentTDegreesOfFreedom,
            destinationNode.noiseScale,
        )
        val residual = yLogResistance - frontierLogResistance
        val terms = List(config.slackQuadraturePoints) { index ->
            val standardisedSlack = (index + 0.5) * width
            val logPriorMass = 0.5 * ln(2.0 / PI) - 0.5 * standardisedSlack * standardisedSlack + ln(width)
            logPriorMass + studentTLogDensity(
                residual = residual + destinationNode.slackScale * standardisedSlack,
                degreesOfFreedom = config.studentTDegreesOfFreedom,
                scale = destinationNode.noiseScale,
                logNormalisation = noiseNormalisation,
            )
        }
        return logSumExp(terms)
    }

    private fun studentTLogDensity(
        residual: Double,
        degreesOfFreedom: Double,
        scale: Double,
        logNormalisation: Double,
    ): Double = logNormalisation -
        ((degreesOfFreedom + 1.0) / 2.0) * ln1p((residual / scale).pow(2) / degreesOfFreedom)

    private fun studentTLogNormalisation(degreesOfFreedom: Double, scale: Double): Double =
        logGamma((degreesOfFreedom + 1.0) / 2.0) -
            logGamma(degreesOfFreedom / 2.0) -
            0.5 * ln(degreesOfFreedom * PI) -
            ln(scale)

    private fun logSumExp(values: List<Double>): Double {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return Double.NEGATIVE_INFINITY
        val maximum = finite.maxOrNull()!!
        return maximum + ln(finite.sumOf { exp(it - maximum) })
    }

    /** Same deterministic Lanczos implementation used by the accepted N0 frontier likelihood. */
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
        if (value < 0.5) {
            return ln(PI) - ln(kotlin.math.sin(PI * value)) - logGamma(1.0 - value)
        }
        val z = value - 1.0
        var x = 0.99999999999980993
        coefficients.forEachIndexed { index, coefficient ->
            x += coefficient / (z + index + 1.0)
        }
        val t = z + coefficients.size - 0.5
        return 0.5 * ln(2.0 * PI) + (z + 0.5) * ln(t) - t + ln(x)
    }

    private data class IndexedSourceNode(
        val stableOriginalIndex: Int,
        val sourceAnchor: Double,
        val node: DynamicTrendFrontierPosteriorNode,
        val normalisedOriginalWeight: Double,
    )

    private val sourceNodeComparator = compareBy<IndexedSourceNode> { it.sourceAnchor }
        .thenBy { it.node.logFrontierAtLatestSession }
        .thenBy { it.node.slope }
        .thenBy { it.node.frontierTrend }
        .thenBy { it.node.slackScale }
        .thenBy { it.node.noiseScale }
        .thenBy { it.stableOriginalIndex }

    private fun inadmissible(reason: DynamicTransferM0InadmissibilityReason, message: String): Nothing =
        throw DynamicTransferM0InadmissibleException(reason, message)
}
