package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.CapabilityTransferSource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

/** Two preregistered safe multi-source forms. Neither combines source evidence. */
enum class DynamicTransferM0SourceSelectionMode {
    SCORE_INDEPENDENTLY,
    EXPLICIT_PRIORITY_SINGLE_SOURCE,
}

data class DynamicTransferM0DirectedEdgeKey(
    val relationshipId: String,
    val relationshipVersion: Int,
    val relationshipPolicyIdentity: String,
    val sourceExecutionProfileVersionId: String,
    val destinationExecutionProfileVersionId: String,
    val relationshipFingerprint: String,
) {
    init {
        require(relationshipId.isNotBlank())
        require(relationshipVersion > 0)
        require(relationshipPolicyIdentity.isNotBlank())
        require(sourceExecutionProfileVersionId.isNotBlank())
        require(destinationExecutionProfileVersionId.isNotBlank())
        require(relationshipFingerprint.startsWith("sha256_"))
    }

    val canonicalIdentity: String = listOf(
        relationshipId,
        relationshipVersion.toString(),
        relationshipPolicyIdentity,
        sourceExecutionProfileVersionId,
        destinationExecutionProfileVersionId,
        relationshipFingerprint,
    ).joinToString("|")

    companion object {
        fun from(relationship: DirectedDynamicTransferRelationshipDescriptor): DynamicTransferM0DirectedEdgeKey =
            DynamicTransferM0DirectedEdgeKey(
                relationshipId = relationship.relationshipId,
                relationshipVersion = relationship.version,
                relationshipPolicyIdentity = relationship.policyIdentity,
                sourceExecutionProfileVersionId = relationship.sourceExecutionProfileVersionId.value,
                destinationExecutionProfileVersionId = relationship.destinationExecutionProfileVersionId.value,
                relationshipFingerprint = "sha256_${sha256(canonicalRelationship(relationship))}",
            )
    }
}

/**
 * Explicit versioned source-selection policy frozen before a held-out destination outcome.
 *
 * [frozenAt] is replay provenance, not part of [policyIdentity]. The identity describes the behaviour
 * and exact ordered relationship descriptors; a later freeze of the same immutable policy therefore
 * retains the same configuration identity while preserving its own chronology separately.
 */
data class DynamicTransferM0SourceSelectionPolicyDefinition(
    val policyId: String,
    val version: Int,
    val frozenAt: Instant,
    val mode: DynamicTransferM0SourceSelectionMode,
    val explicitPriority: List<DynamicTransferM0DirectedEdgeKey>,
    val semanticVersion: String = SEMANTIC_VERSION,
) {
    init {
        require(policyId.isNotBlank())
        require(version > 0)
        require(semanticVersion == SEMANTIC_VERSION) { "M0 source-selection semantic version is immutable." }
        when (mode) {
            DynamicTransferM0SourceSelectionMode.SCORE_INDEPENDENTLY -> require(explicitPriority.isEmpty()) {
                "Independent scoring cannot carry a hidden source-priority order."
            }
            DynamicTransferM0SourceSelectionMode.EXPLICIT_PRIORITY_SINGLE_SOURCE -> {
                require(explicitPriority.isNotEmpty()) { "Single-source selection requires an explicit priority list." }
                require(explicitPriority.distinct().size == explicitPriority.size) {
                    "Explicit source priority cannot contain duplicate directed relationship identities."
                }
            }
        }
    }

    val policyIdentity: String = "m0_source_selection_sha256_${sha256(canonicalPolicy())}"

    private fun canonicalPolicy(): String = buildString {
        append("semanticVersion=").append(semanticVersion).append('\n')
        append("policyId=").append(policyId).append('\n')
        append("version=").append(version).append('\n')
        append("mode=").append(mode.name).append('\n')
        append("explicitPriority=")
        append(explicitPriority.joinToString(">") { it.canonicalIdentity })
    }

    companion object {
        const val SEMANTIC_VERSION = "n-bio-7f-m0-source-selection-policy-v1"
    }
}

object DynamicTransferM0SourceSelectionPolicies {
    fun scoreIndependently(
        policyId: String,
        version: Int,
        frozenAt: Instant,
    ): DynamicTransferM0SourceSelectionPolicyDefinition = DynamicTransferM0SourceSelectionPolicyDefinition(
        policyId = policyId,
        version = version,
        frozenAt = frozenAt,
        mode = DynamicTransferM0SourceSelectionMode.SCORE_INDEPENDENTLY,
        explicitPriority = emptyList(),
    )

    fun selectByExplicitPriority(
        policyId: String,
        version: Int,
        frozenAt: Instant,
        orderedRelationships: List<DirectedDynamicTransferRelationshipDescriptor>,
    ): DynamicTransferM0SourceSelectionPolicyDefinition = DynamicTransferM0SourceSelectionPolicyDefinition(
        policyId = policyId,
        version = version,
        frozenAt = frozenAt,
        mode = DynamicTransferM0SourceSelectionMode.EXPLICIT_PRIORITY_SINGLE_SOURCE,
        explicitPriority = orderedRelationships.map(DynamicTransferM0DirectedEdgeKey::from),
    )
}

data class DynamicTransferM0SourceCandidateInput(
    val source: CapabilityTransferSource,
    val sourceLoadAccounting: DynamicTransferM0LoadAccountingContext,
    val relationship: DirectedDynamicTransferRelationshipDescriptor,
    val replayDependencyScope: DynamicTransferM0ReplayDependencyScope,
) {
    val edgeKey: DynamicTransferM0DirectedEdgeKey = DynamicTransferM0DirectedEdgeKey.from(relationship)
}

sealed interface DynamicTransferM0SourceCandidateRefusal {
    data class FreezeFailure(
        val reason: DynamicTransferM0FreezeFailure,
        val detail: String,
    ) : DynamicTransferM0SourceCandidateRefusal {
        init { require(detail.isNotBlank()) }
    }

    data class AdmissibilityFailure(
        val reason: DynamicTransferM0InadmissibilityReason,
        val detail: String,
    ) : DynamicTransferM0SourceCandidateRefusal {
        init { require(detail.isNotBlank()) }
    }

    data class OutsideExplicitPriority(
        val policyIdentity: String,
    ) : DynamicTransferM0SourceCandidateRefusal {
        init { require(policyIdentity.isNotBlank()) }
    }
}

sealed interface DynamicTransferM0SourceCandidateAssessment {
    val edgeKey: DynamicTransferM0DirectedEdgeKey
    val input: DynamicTransferM0SourceCandidateInput

    data class Admissible(
        override val edgeKey: DynamicTransferM0DirectedEdgeKey,
        override val input: DynamicTransferM0SourceCandidateInput,
        val frozenSession: DynamicTransferM0FrozenDestinationSession,
    ) : DynamicTransferM0SourceCandidateAssessment {
        init {
            require(frozenSession.sourcePairing is DynamicTransferM0FrozenSourcePairing.Paired)
        }
    }

    data class Rejected(
        override val edgeKey: DynamicTransferM0DirectedEdgeKey,
        override val input: DynamicTransferM0SourceCandidateInput,
        val refusal: DynamicTransferM0SourceCandidateRefusal,
    ) : DynamicTransferM0SourceCandidateAssessment
}

enum class DynamicTransferM0SourceSelectionPolicyFailure {
    POLICY_NOT_STRICTLY_PRIOR,
    DUPLICATE_CANDIDATE_EDGE,
}

class DynamicTransferM0SourceSelectionPolicyException(
    val reason: DynamicTransferM0SourceSelectionPolicyFailure,
    message: String,
) : IllegalArgumentException(message)

sealed interface DynamicTransferM0SourceSelectionDecision {
    val policy: DynamicTransferM0SourceSelectionPolicyDefinition
    val destinationSession: DynamicTransferM0DestinationSessionDescriptor
    val assessments: List<DynamicTransferM0SourceCandidateAssessment>

    /** Every admissible source remains a separate M0 candidate. Nothing is combined or selected. */
    data class IndependentCandidates(
        override val policy: DynamicTransferM0SourceSelectionPolicyDefinition,
        override val destinationSession: DynamicTransferM0DestinationSessionDescriptor,
        val candidates: List<DynamicTransferM0SourceCandidateAssessment.Admissible>,
        val rejected: List<DynamicTransferM0SourceCandidateAssessment.Rejected>,
    ) : DynamicTransferM0SourceSelectionDecision {
        init { require(candidates.isNotEmpty()) }
        override val assessments: List<DynamicTransferM0SourceCandidateAssessment> = candidates + rejected
    }

    /** Exactly one source is selected by the explicit frozen priority; lower listed sources stay separate and unused. */
    data class SelectedSingle(
        override val policy: DynamicTransferM0SourceSelectionPolicyDefinition,
        override val destinationSession: DynamicTransferM0DestinationSessionDescriptor,
        val selected: DynamicTransferM0SourceCandidateAssessment.Admissible,
        val admissibleButNotSelected: List<DynamicTransferM0SourceCandidateAssessment.Admissible>,
        val rejected: List<DynamicTransferM0SourceCandidateAssessment.Rejected>,
    ) : DynamicTransferM0SourceSelectionDecision {
        override val assessments: List<DynamicTransferM0SourceCandidateAssessment> =
            listOf(selected) + admissibleButNotSelected + rejected
    }

    /** Transfer is unavailable under this frozen policy. Destination N0 remains independently available. */
    data class NoAdmissibleSource(
        override val policy: DynamicTransferM0SourceSelectionPolicyDefinition,
        override val destinationSession: DynamicTransferM0DestinationSessionDescriptor,
        val rejected: List<DynamicTransferM0SourceCandidateAssessment.Rejected>,
    ) : DynamicTransferM0SourceSelectionDecision {
        override val assessments: List<DynamicTransferM0SourceCandidateAssessment> = rejected
    }
}

/**
 * Pure pre-outcome source admissibility and selection surface for M0.
 *
 * No destination outcome is accepted by this API. Independent mode returns separate direct-edge
 * candidates. Explicit-priority mode can choose one exact relationship descriptor only. There is no
 * source averaging, precision weighting, transitive path, or learned ranking here.
 */
object DynamicTransferM0SourceSelector {
    fun decide(
        destinationSession: DynamicTransferM0DestinationSessionDescriptor,
        policy: DynamicTransferM0SourceSelectionPolicyDefinition,
        candidates: List<DynamicTransferM0SourceCandidateInput>,
    ): DynamicTransferM0SourceSelectionDecision {
        if (!policy.frozenAt.isBefore(destinationSession.firstObservationTime)) {
            throw DynamicTransferM0SourceSelectionPolicyException(
                DynamicTransferM0SourceSelectionPolicyFailure.POLICY_NOT_STRICTLY_PRIOR,
                "M0 source-selection policy must be frozen strictly before the destination outcome begins.",
            )
        }
        if (candidates.map { it.edgeKey }.distinct().size != candidates.size) {
            throw DynamicTransferM0SourceSelectionPolicyException(
                DynamicTransferM0SourceSelectionPolicyFailure.DUPLICATE_CANDIDATE_EDGE,
                "M0 source-selection input cannot contain the same exact directed edge twice.",
            )
        }

        return when (policy.mode) {
            DynamicTransferM0SourceSelectionMode.SCORE_INDEPENDENTLY -> independentDecision(
                destinationSession = destinationSession,
                policy = policy,
                candidates = candidates,
            )
            DynamicTransferM0SourceSelectionMode.EXPLICIT_PRIORITY_SINGLE_SOURCE -> priorityDecision(
                destinationSession = destinationSession,
                policy = policy,
                candidates = candidates,
            )
        }
    }

    private fun independentDecision(
        destinationSession: DynamicTransferM0DestinationSessionDescriptor,
        policy: DynamicTransferM0SourceSelectionPolicyDefinition,
        candidates: List<DynamicTransferM0SourceCandidateInput>,
    ): DynamicTransferM0SourceSelectionDecision {
        val assessments = candidates
            .sortedBy { it.edgeKey.canonicalIdentity }
            .map { assess(destinationSession, it) }
        val admissible = assessments.filterIsInstance<DynamicTransferM0SourceCandidateAssessment.Admissible>()
        val rejected = assessments.filterIsInstance<DynamicTransferM0SourceCandidateAssessment.Rejected>()
        return if (admissible.isEmpty()) {
            DynamicTransferM0SourceSelectionDecision.NoAdmissibleSource(policy, destinationSession, rejected)
        } else {
            DynamicTransferM0SourceSelectionDecision.IndependentCandidates(
                policy = policy,
                destinationSession = destinationSession,
                candidates = admissible,
                rejected = rejected,
            )
        }
    }

    private fun priorityDecision(
        destinationSession: DynamicTransferM0DestinationSessionDescriptor,
        policy: DynamicTransferM0SourceSelectionPolicyDefinition,
        candidates: List<DynamicTransferM0SourceCandidateInput>,
    ): DynamicTransferM0SourceSelectionDecision {
        val priority = policy.explicitPriority
        val prioritySet = priority.toSet()
        val assessments = candidates
            .sortedBy { it.edgeKey.canonicalIdentity }
            .map { candidate ->
                if (candidate.edgeKey !in prioritySet) {
                    DynamicTransferM0SourceCandidateAssessment.Rejected(
                        edgeKey = candidate.edgeKey,
                        input = candidate,
                        refusal = DynamicTransferM0SourceCandidateRefusal.OutsideExplicitPriority(policy.policyIdentity),
                    )
                } else {
                    assess(destinationSession, candidate)
                }
            }
        val admissibleByKey = assessments
            .filterIsInstance<DynamicTransferM0SourceCandidateAssessment.Admissible>()
            .associateBy { it.edgeKey }
        val selected = priority.firstNotNullOfOrNull { admissibleByKey[it] }
        val rejected = assessments.filterIsInstance<DynamicTransferM0SourceCandidateAssessment.Rejected>()
        if (selected == null) {
            return DynamicTransferM0SourceSelectionDecision.NoAdmissibleSource(policy, destinationSession, rejected)
        }
        val notSelected = priority
            .mapNotNull { admissibleByKey[it] }
            .filterNot { it.edgeKey == selected.edgeKey }
        return DynamicTransferM0SourceSelectionDecision.SelectedSingle(
            policy = policy,
            destinationSession = destinationSession,
            selected = selected,
            admissibleButNotSelected = notSelected,
            rejected = rejected,
        )
    }

    private fun assess(
        destinationSession: DynamicTransferM0DestinationSessionDescriptor,
        candidate: DynamicTransferM0SourceCandidateInput,
    ): DynamicTransferM0SourceCandidateAssessment = try {
        DynamicTransferM0SourceCandidateAssessment.Admissible(
            edgeKey = candidate.edgeKey,
            input = candidate,
            frozenSession = DynamicTransferM0SessionPolicy.freezePaired(
                destinationSession = destinationSession,
                source = candidate.source,
                sourceLoadAccounting = candidate.sourceLoadAccounting,
                relationship = DynamicTransferM0CandidateTopology.requireSingleDirectEdge(listOf(candidate.relationship)),
            ),
        )
    } catch (failure: DynamicTransferM0FreezeException) {
        DynamicTransferM0SourceCandidateAssessment.Rejected(
            edgeKey = candidate.edgeKey,
            input = candidate,
            refusal = DynamicTransferM0SourceCandidateRefusal.FreezeFailure(
                failure.reason,
                failure.message ?: failure.reason.name,
            ),
        )
    } catch (failure: DynamicTransferM0InadmissibleException) {
        DynamicTransferM0SourceCandidateAssessment.Rejected(
            edgeKey = candidate.edgeKey,
            input = candidate,
            refusal = DynamicTransferM0SourceCandidateRefusal.AdmissibilityFailure(
                failure.reason,
                failure.message ?: failure.reason.name,
            ),
        )
    }
}

private fun canonicalRelationship(relationship: DirectedDynamicTransferRelationshipDescriptor): String = buildString {
    append("relationshipId=").append(relationship.relationshipId).append('\n')
    append("version=").append(relationship.version).append('\n')
    append("relationshipPolicyIdentity=").append(relationship.policyIdentity).append('\n')
    append("sourceProfile=").append(relationship.sourceExecutionProfileId.value).append('\n')
    append("sourceProfileVersion=").append(relationship.sourceExecutionProfileVersionId.value).append('\n')
    append("destinationProfile=").append(relationship.destinationExecutionProfileId.value).append('\n')
    append("destinationProfileVersion=").append(relationship.destinationExecutionProfileVersionId.value).append('\n')
    append("side=").append(relationship.side.name).append('\n')
    append("sourceEquipment=").append(relationship.sourceEquipmentId.value).append('\n')
    append("sourceInterpretation=").append(relationship.sourceEquipmentInterpretationVersion).append('\n')
    append("sourceFacts=").append(relationship.sourceEquipmentFactVersionIds.sorted().joinToString(",")).append('\n')
    append("destinationEquipment=").append(relationship.destinationEquipmentId.value).append('\n')
    append("destinationInterpretation=").append(relationship.destinationEquipmentInterpretationVersion).append('\n')
    append("destinationFacts=").append(relationship.destinationEquipmentFactVersionIds.sorted().joinToString(",")).append('\n')
    append("sourceLoadAccounting=").append(relationship.sourceLoadAccounting.storageValue).append('\n')
    append("destinationLoadAccounting=").append(relationship.destinationLoadAccounting.storageValue)
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
