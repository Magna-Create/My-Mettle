package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.math.abs

/** Stable 7F family identity. These names match existing persisted capability-family semantics. */
enum class CapabilityTransferFamily(val storageValue: String) {
    DYNAMIC_RESISTANCE("dynamic_resistance"),
    LOADED_HOLD("loaded_hold"),
    DURATION_ONLY("duration_only"),
    REPEATED_CONTRACTION("repeated_contraction"),
}

/** Minimal profile semantics required to keep a source capability local and meaningfully typed. */
data class CapabilitySourceProfileSemantics(
    val executionProfileId: ExecutionProfileId,
    val executionProfileVersionId: ExecutionProfileVersionId,
    val metricFamily: MetricFamily,
    val resistanceSemantics: ResistanceSemantics,
    val resistanceModelVersion: String,
    val entryBasis: EntryBasis,
    val lateralityMode: LateralityMode,
) {
    init {
        require(resistanceModelVersion.isNotBlank())
    }

    companion object {
        fun from(profile: DynamicResistanceProfileSemantics) = CapabilitySourceProfileSemantics(
            executionProfileId = profile.executionProfileId,
            executionProfileVersionId = profile.executionProfileVersionId,
            metricFamily = profile.metricFamily,
            resistanceSemantics = profile.resistanceModel.semantics,
            resistanceModelVersion = profile.resistanceModel.modelVersion,
            entryBasis = profile.entryBasis,
            lateralityMode = profile.lateralityMode,
        )

        fun from(profile: NonDynamicProfileSemantics) = CapabilitySourceProfileSemantics(
            executionProfileId = profile.executionProfileId,
            executionProfileVersionId = profile.executionProfileVersionId,
            metricFamily = profile.metricFamily,
            resistanceSemantics = profile.resistanceModel.semantics,
            resistanceModelVersion = profile.resistanceModel.modelVersion,
            entryBasis = profile.entryBasis,
            lateralityMode = profile.lateralityMode,
        )
    }
}

/**
 * Equipment context is explicit at the transfer boundary. Unknown or mixed source history is not
 * replaced with today's preference and is not silently labelled as one equipment instance.
 */
sealed interface CapabilityEquipmentContext {
    data class ResolvedSingleContext(
        val equipmentId: EquipmentId,
        val interpretationVersion: String,
        val contributingObservationIds: Set<String>,
        val equipmentFactVersionIds: Set<String>,
    ) : CapabilityEquipmentContext {
        init {
            require(interpretationVersion.isNotBlank())
            require(contributingObservationIds.isNotEmpty())
            require(contributingObservationIds.all { it.isNotBlank() })
            require(equipmentFactVersionIds.all { it.isNotBlank() })
        }
    }

    /** Explicitly assessed as not equipment-sensitive for this source quantity. */
    data class NotApplicable(val reason: String) : CapabilityEquipmentContext {
        init { require(reason.isNotBlank()) }
    }

    /** Missing, mixed or unreconciled equipment context. Downstream transfer must fail closed. */
    data class Unresolved(val reason: String) : CapabilityEquipmentContext {
        init { require(reason.isNotBlank()) }
    }
}

enum class CapabilitySourceContextStatus {
    COMPLETE_FOR_ADMISSIBILITY_REVIEW,
    EQUIPMENT_CONTEXT_UNRESOLVED,
}

data class CapabilityCausalCutoff(
    val asOf: Instant,
    val evidenceThrough: Instant,
) {
    init {
        require(!evidenceThrough.isAfter(asOf)) {
            "Source capability evidence cannot extend beyond its as-of horizon."
        }
    }
}

data class CapabilityContinuousDomain(
    val minimum: Double,
    val maximum: Double,
) {
    init {
        require(minimum.isFinite() && maximum.isFinite())
        require(maximum >= minimum)
    }
}

data class CapabilityIntegerDomain(
    val minimum: Int,
    val maximum: Int,
) {
    init { require(minimum > 0 && maximum >= minimum) }
}

/** Family-specific observed/query support. No universal load coordinate is introduced. */
sealed interface CapabilityQueryDomain {
    val outputUnit: UnitId

    data class DynamicResistance(
        val observedRepetitions: CapabilityIntegerDomain,
        val observedResistanceKg: CapabilityContinuousDomain,
        val referenceRepetitions: Double,
    ) : CapabilityQueryDomain {
        override val outputUnit: UnitId = UnitId.KILOGRAM

        init { require(referenceRepetitions.isFinite() && referenceRepetitions > 0.0) }
    }

    data class LoadedHold(
        val observedDurationSeconds: CapabilityContinuousDomain,
        val observedResistanceKg: CapabilityContinuousDomain,
        val referenceDurationSeconds: Double,
    ) : CapabilityQueryDomain {
        override val outputUnit: UnitId = UnitId.KILOGRAM

        init { require(referenceDurationSeconds.isFinite() && referenceDurationSeconds > 0.0) }
    }

    data class DurationOnly(
        val observedDurationSeconds: CapabilityContinuousDomain,
    ) : CapabilityQueryDomain {
        override val outputUnit: UnitId = UnitId.SECOND
    }

    data class RepeatedContraction(
        val observedCycles: CapabilityIntegerDomain,
        val observedResistanceKg: CapabilityContinuousDomain,
        val referenceCycles: Double,
    ) : CapabilityQueryDomain {
        override val outputUnit: UnitId = UnitId.KILOGRAM

        init { require(referenceCycles.isFinite() && referenceCycles > 0.0) }
    }
}

/**
 * Posterior payloads retain the native joint weighted-node structure already accepted upstream.
 * 7F-C deliberately does not replace these with mean/variance or a generic vector/tensor contract.
 */
sealed interface CapabilityPosteriorPayload {
    val representation: InferencePosteriorRepresentation
    val anchorPosterior: PosteriorEstimate

    data class DynamicTrendNodes(
        override val representation: InferencePosteriorRepresentation,
        override val anchorPosterior: PosteriorEstimate,
        val referenceRepetitions: Double,
        val nodes: List<DynamicTrendFrontierPosteriorNode>,
    ) : CapabilityPosteriorPayload {
        init {
            requireWeightedNodeRepresentation(representation)
            require(referenceRepetitions.isFinite() && referenceRepetitions > 0.0)
            requireJointWeights(nodes.map { it.posteriorWeight })
        }
    }

    data class NonDynamicNodes(
        val capabilityFamily: CapabilityTransferFamily,
        override val representation: InferencePosteriorRepresentation,
        override val anchorPosterior: PosteriorEstimate,
        val referenceCoordinate: Double?,
        val nodes: List<NonDynamicPosteriorNode>,
    ) : CapabilityPosteriorPayload {
        init {
            require(capabilityFamily != CapabilityTransferFamily.DYNAMIC_RESISTANCE)
            requireWeightedNodeRepresentation(representation)
            require(referenceCoordinate == null || referenceCoordinate.isFinite() && referenceCoordinate > 0.0)
            requireJointWeights(nodes.map { it.posteriorWeight })
        }
    }
}

enum class WithinSourceDependenceRepresentation {
    JOINT_WEIGHTED_POSTERIOR_NODES_RETAINED,
}

enum class CrossSourceDependenceStatus {
    NOT_ESTABLISHED_DO_NOT_ASSUME_INDEPENDENT,
}

data class CapabilityDependenceMetadata(
    val withinSource: WithinSourceDependenceRepresentation =
        WithinSourceDependenceRepresentation.JOINT_WEIGHTED_POSTERIOR_NODES_RETAINED,
    val crossSource: CrossSourceDependenceStatus =
        CrossSourceDependenceStatus.NOT_ESTABLISHED_DO_NOT_ASSUME_INDEPENDENT,
)

data class CapabilityUpstreamIdentity(
    val modelConfigId: ModelConfigId,
    val modelManifestId: ModelManifestId?,
    val inferenceRunId: InferenceRunId?,
    val mathematicalModelIdentity: InferenceMathematicalModelIdentity,
    val solverIdentity: InferenceSolverIdentity,
    val posteriorRepresentation: InferencePosteriorRepresentation,
    val evidencePolicyIdentity: String,
) {
    init { require(evidencePolicyIdentity.isNotBlank()) }
}

/**
 * Lossless source-capability message for 7F transfer candidates.
 *
 * This only makes a source inspectable for later semantic admissibility. It does not assert that
 * source and destination are exchangeable, that transfer is useful, or that multiple sources are
 * independent.
 */
data class CapabilityTransferSource(
    val profile: CapabilitySourceProfileSemantics,
    val capabilityFamily: CapabilityTransferFamily,
    val side: Laterality,
    val causalCutoff: CapabilityCausalCutoff,
    val equipmentContext: CapabilityEquipmentContext,
    val queryDomain: CapabilityQueryDomain,
    val posterior: CapabilityPosteriorPayload,
    val support: EvidenceSupport,
    val selectedObservationIds: List<String>,
    val selectedSessionIds: List<String>,
    val upstream: CapabilityUpstreamIdentity,
    val dependence: CapabilityDependenceMetadata,
    val warnings: Set<String>,
) {
    init {
        require(support.observationCount > 0)
        require(support.lastEvidenceAt == causalCutoff.evidenceThrough) {
            "Capability support and causal evidence cutoff must describe the same evidence horizon."
        }
        require(selectedObservationIds.size == support.observationCount)
        require(selectedObservationIds.distinct().size == selectedObservationIds.size)
        require(selectedSessionIds.distinct().size == support.effectiveIndependentSessionCount)
        require(selectedSessionIds.size == support.effectiveIndependentSessionCount)
        require(warnings.all { it.isNotBlank() })
        require(posterior.anchorPosterior.support == support)
        require(posterior.anchorPosterior.provenance.evidenceThrough == causalCutoff.evidenceThrough)
        require(posterior.anchorPosterior.provenance.modelConfigId == upstream.modelConfigId)
        require(posterior.anchorPosterior.provenance.modelManifestId == upstream.modelManifestId)
        require(posterior.anchorPosterior.provenance.inferenceRunId == upstream.inferenceRunId)
        require(posterior.representation == upstream.posteriorRepresentation)

        if (equipmentContext is CapabilityEquipmentContext.ResolvedSingleContext) {
            require(equipmentContext.contributingObservationIds == selectedObservationIds.toSet()) {
                "Resolved equipment context must account for every selected source observation exactly."
            }
        }

        when (capabilityFamily) {
            CapabilityTransferFamily.DYNAMIC_RESISTANCE -> {
                require(profile.metricFamily in setOf(MetricFamily.DYNAMIC_RESISTANCE, MetricFamily.BODYWEIGHT_RESISTANCE))
                require(queryDomain is CapabilityQueryDomain.DynamicResistance)
                require(posterior is CapabilityPosteriorPayload.DynamicTrendNodes)
            }
            CapabilityTransferFamily.LOADED_HOLD -> {
                require(profile.metricFamily == MetricFamily.LOADED_HOLD)
                require(queryDomain is CapabilityQueryDomain.LoadedHold)
                require(posterior is CapabilityPosteriorPayload.NonDynamicNodes &&
                    posterior.capabilityFamily == CapabilityTransferFamily.LOADED_HOLD)
            }
            CapabilityTransferFamily.DURATION_ONLY -> {
                require(profile.metricFamily == MetricFamily.DURATION_ONLY)
                require(queryDomain is CapabilityQueryDomain.DurationOnly)
                require(posterior is CapabilityPosteriorPayload.NonDynamicNodes &&
                    posterior.capabilityFamily == CapabilityTransferFamily.DURATION_ONLY)
            }
            CapabilityTransferFamily.REPEATED_CONTRACTION -> {
                require(profile.metricFamily == MetricFamily.REPEATED_CONTRACTION)
                require(queryDomain is CapabilityQueryDomain.RepeatedContraction)
                require(posterior is CapabilityPosteriorPayload.NonDynamicNodes &&
                    posterior.capabilityFamily == CapabilityTransferFamily.REPEATED_CONTRACTION)
            }
        }
    }

    val contextStatus: CapabilitySourceContextStatus
        get() = if (equipmentContext is CapabilityEquipmentContext.Unresolved) {
            CapabilitySourceContextStatus.EQUIPMENT_CONTEXT_UNRESOLVED
        } else {
            CapabilitySourceContextStatus.COMPLETE_FOR_ADMISSIBILITY_REVIEW
        }
}

/** Source-led adapters for the accepted 7B.X Candidate-v2 and 7C fit shapes. */
object CapabilityTransferSourceFactory {
    fun fromDynamicTrendFit(
        profile: DynamicResistanceProfileSemantics,
        fit: DynamicTrendFrontierFit,
        equipmentContext: CapabilityEquipmentContext,
    ): CapabilityTransferSource {
        require(fit.executionProfileVersionId == profile.executionProfileVersionId)
        require(profile.metricFamily in setOf(MetricFamily.DYNAMIC_RESISTANCE, MetricFamily.BODYWEIGHT_RESISTANCE))

        val anchor = fit.frontierAtLatestSession
        return CapabilityTransferSource(
            profile = CapabilitySourceProfileSemantics.from(profile),
            capabilityFamily = CapabilityTransferFamily.DYNAMIC_RESISTANCE,
            side = fit.side,
            causalCutoff = causalCutoff(fit.inferenceHorizon, fit.support, anchor),
            equipmentContext = equipmentContext,
            queryDomain = CapabilityQueryDomain.DynamicResistance(
                observedRepetitions = CapabilityIntegerDomain(fit.observedRepMin, fit.observedRepMax),
                observedResistanceKg = CapabilityContinuousDomain(
                    fit.observedResistanceMinKg,
                    fit.observedResistanceMaxKg,
                ),
                referenceRepetitions = fit.referenceRepetitions,
            ),
            posterior = CapabilityPosteriorPayload.DynamicTrendNodes(
                representation = fit.solverDiagnostics.posteriorRepresentation,
                anchorPosterior = anchor,
                referenceRepetitions = fit.referenceRepetitions,
                nodes = fit.posteriorNodes.toList(),
            ),
            support = fit.support,
            selectedObservationIds = fit.selectedObservationIds.toList(),
            selectedSessionIds = fit.selectedSessionIds.toList(),
            upstream = CapabilityUpstreamIdentity(
                modelConfigId = fit.modelConfigId,
                modelManifestId = anchor.provenance.modelManifestId,
                inferenceRunId = anchor.provenance.inferenceRunId,
                mathematicalModelIdentity = fit.mathematicalModelIdentity,
                solverIdentity = fit.solverDiagnostics.solverIdentity,
                posteriorRepresentation = fit.solverDiagnostics.posteriorRepresentation,
                evidencePolicyIdentity = fit.evidencePolicyIdentity,
            ),
            dependence = CapabilityDependenceMetadata(),
            warnings = fit.warnings.toSet(),
        )
    }

    fun fromNonDynamicFit(
        profile: NonDynamicProfileSemantics,
        fit: NonDynamicCapabilityFit,
        equipmentContext: CapabilityEquipmentContext,
    ): CapabilityTransferSource {
        require(fit.executionProfileVersionId == profile.executionProfileVersionId)
        require(fit.family == profile.metricFamily)

        val family = fit.family.toTransferFamily()
        val anchor = fit.frontierAtReference
        return CapabilityTransferSource(
            profile = CapabilitySourceProfileSemantics.from(profile),
            capabilityFamily = family,
            side = fit.side,
            causalCutoff = causalCutoff(fit.inferenceHorizon, fit.support, anchor),
            equipmentContext = equipmentContext,
            queryDomain = fit.toTransferQueryDomain(),
            posterior = CapabilityPosteriorPayload.NonDynamicNodes(
                capabilityFamily = family,
                representation = fit.solverDiagnostics.posteriorRepresentation,
                anchorPosterior = anchor,
                referenceCoordinate = fit.referenceCoordinate,
                nodes = fit.posteriorNodes.toList(),
            ),
            support = fit.support,
            selectedObservationIds = fit.selectedObservationIds.toList(),
            selectedSessionIds = fit.selectedSessionIds.toList(),
            upstream = CapabilityUpstreamIdentity(
                modelConfigId = fit.modelConfigId,
                modelManifestId = anchor.provenance.modelManifestId,
                inferenceRunId = anchor.provenance.inferenceRunId,
                mathematicalModelIdentity = fit.mathematicalModelIdentity,
                solverIdentity = fit.solverDiagnostics.solverIdentity,
                posteriorRepresentation = fit.solverDiagnostics.posteriorRepresentation,
                evidencePolicyIdentity = fit.evidencePolicyIdentity,
            ),
            dependence = CapabilityDependenceMetadata(),
            warnings = fit.warnings.toSet(),
        )
    }
}

private fun causalCutoff(
    inferenceHorizon: Instant,
    support: EvidenceSupport,
    anchor: PosteriorEstimate,
): CapabilityCausalCutoff {
    val evidenceThrough = requireNotNull(anchor.provenance.evidenceThrough) {
        "Transfer source requires an explicit causal evidence cutoff."
    }
    require(support.lastEvidenceAt == evidenceThrough) {
        "Transfer source support and posterior provenance disagree on evidence cutoff."
    }
    return CapabilityCausalCutoff(inferenceHorizon, evidenceThrough)
}

private fun MetricFamily.toTransferFamily(): CapabilityTransferFamily = when (this) {
    MetricFamily.LOADED_HOLD -> CapabilityTransferFamily.LOADED_HOLD
    MetricFamily.DURATION_ONLY -> CapabilityTransferFamily.DURATION_ONLY
    MetricFamily.REPEATED_CONTRACTION -> CapabilityTransferFamily.REPEATED_CONTRACTION
    else -> throw IllegalArgumentException("Metric family $storageValue is not a 7C non-dynamic transfer source family.")
}

private fun NonDynamicCapabilityFit.toTransferQueryDomain(): CapabilityQueryDomain = when (family) {
    MetricFamily.LOADED_HOLD -> {
        require(canonicalUnit == UnitId.KILOGRAM)
        CapabilityQueryDomain.LoadedHold(
            observedDurationSeconds = CapabilityContinuousDomain(
                requireNotNull(observedInputMin),
                requireNotNull(observedInputMax),
            ),
            observedResistanceKg = CapabilityContinuousDomain(observedOutputMin, observedOutputMax),
            referenceDurationSeconds = requireNotNull(referenceCoordinate),
        )
    }
    MetricFamily.DURATION_ONLY -> {
        require(canonicalUnit == UnitId.SECOND)
        require(observedInputMin == null && observedInputMax == null && referenceCoordinate == null)
        CapabilityQueryDomain.DurationOnly(
            observedDurationSeconds = CapabilityContinuousDomain(observedOutputMin, observedOutputMax),
        )
    }
    MetricFamily.REPEATED_CONTRACTION -> {
        require(canonicalUnit == UnitId.KILOGRAM)
        CapabilityQueryDomain.RepeatedContraction(
            observedCycles = CapabilityIntegerDomain(
                requireIntegralPositive(requireNotNull(observedInputMin), "minimum observed cycles"),
                requireIntegralPositive(requireNotNull(observedInputMax), "maximum observed cycles"),
            ),
            observedResistanceKg = CapabilityContinuousDomain(observedOutputMin, observedOutputMax),
            referenceCycles = requireNotNull(referenceCoordinate),
        )
    }
    else -> error("Unsupported 7C transfer source family ${family.storageValue}")
}

private fun requireIntegralPositive(value: Double, label: String): Int {
    require(value.isFinite() && value > 0.0 && value % 1.0 == 0.0) { "$label must be a positive integer coordinate." }
    require(value <= Int.MAX_VALUE.toDouble()) { "$label exceeds the supported integer domain." }
    return value.toInt()
}

private fun requireWeightedNodeRepresentation(representation: InferencePosteriorRepresentation) {
    require(
        representation in setOf(
            InferencePosteriorRepresentation.WEIGHTED_DENSE_NODES,
            InferencePosteriorRepresentation.WEIGHTED_SPARSE_NODES,
        ),
    ) { "7F-C node payload requires an upstream weighted-node posterior representation." }
}

private fun requireJointWeights(weights: List<Double>) {
    require(weights.isNotEmpty())
    require(weights.all { it.isFinite() && it >= 0.0 })
    require(abs(weights.sum() - 1.0) <= 1e-8) { "Transfer posterior node weights must sum to one." }
}
